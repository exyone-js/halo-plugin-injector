package com.erzbir.injector.halo.filter;

import com.erzbir.injector.api.InjectMode;
import com.erzbir.injector.halo.core.ElementIDInjector;
import com.erzbir.injector.halo.core.HTMLCode;
import com.erzbir.injector.halo.core.HTMLInjector;
import com.erzbir.injector.halo.core.InjectHelper;
import com.erzbir.injector.halo.core.SelectorInjector;
import com.erzbir.injector.halo.scheme.InjectionRule;
import com.erzbir.injector.halo.util.FingerprintUtil;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
class HTMLInjectDispatcher {
    private static final List<InjectionHandler> INJECTION_HANDLERS = List.of(
        new InjectionHandler(InjectMode.SELECTOR, new SelectorInjector()),
        new InjectionHandler(InjectMode.ID, new ElementIDInjector())
    );

    private final InjectHelper injectHelper;

    public HTMLInjectDispatcher(InjectHelper injectHelper) {
        this.injectHelper = injectHelper;
    }

    public Mono<String> dispatch(String html, String permalink) {
        return collectAllRuleCodes(permalink)
            .collectList()
            .flatMap(ruleCodes -> {
                if (ruleCodes.isEmpty()) {
                    return Mono.just(html);
                }
                long htmlFingerprint = FingerprintUtil.fnv1a64(html);
                long ruleFingerprint = buildRuleFingerprint(ruleCodes);
                long cacheFingerprint = Long.rotateLeft(htmlFingerprint, 17) ^ ruleFingerprint;
                String cached = HTMLResponseCache.get(permalink, cacheFingerprint);
                if (cached != null) {
                    return Mono.just(cached);
                }
                return Mono.fromCallable(() -> {
                        Document document = Jsoup.parse(html);
                        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
                        return applyRuleCodes(document, permalink, ruleCodes);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(processed ->
                        HTMLResponseCache.put(permalink, cacheFingerprint, processed)
                    );
            });
    }

    private Flux<RuleCode> collectAllRuleCodes(String permalink) {
        return Flux.fromIterable(INJECTION_HANDLERS)
            .concatMap(handler -> fetchRuleCodes(permalink, handler));
    }

    private Flux<RuleCode> fetchRuleCodes(String path, InjectionHandler handler) {
        var matchedRules = injectHelper.getMatchedRules(path, handler.mode());
        if (matchedRules == null) {
            return Flux.empty();
        }
        return matchedRules.concatMap(rule -> injectHelper.getConcatCode(rule)
                .map(code -> new RuleCode(rule, code, handler.injector())))
            .filter(rc -> !rc.code().isBlank());
    }

    private String applyRuleCodes(Document document, String path, List<RuleCode> ruleCodes) {
        Document.OutputSettings outputSettings = document.outputSettings();
        for (RuleCode rc : ruleCodes) {
            var rule = rc.rule();
            try {
                rc.injector().inject(document, new HTMLCode(rc.code()), rule, null);
            } catch (Exception e) {
                log.warn("Failed to inject HTML rule, rule: {}, mode: {}, path: {}", rule.getId(),
                    rule.getMode(), path, e);
            } finally {
                document.outputSettings(outputSettings);
            }
        }
        return document.html();
    }

    private Long buildRuleFingerprint(List<RuleCode> ruleCodes) {
        if (ruleCodes == null || ruleCodes.isEmpty()) {
            return 0L;
        }
        return ruleCodes.stream()
            .map(RuleCode::fpString)
            .map(FingerprintUtil::fnv1a64)
            .reduce(0L, (a, b) -> {
                long rotated = (a << 17) | (a >>> 47);
                return rotated ^ b;
            });
    }

    private record RuleCode(InjectionRule rule, String code, HTMLInjector injector) {
        String fpString() {
            return rule.getId()
                + "|" + rule.getMode()
                + "|" + rule.getMatch()
                + "|" + rule.getPosition()
                + "|" + rule.getEffectiveMatchRule()
                + "|" + rule.getSnippetIds().stream().sorted().collect(Collectors.joining(","))
                + "|" + code;
        }
    }

    private record InjectionHandler(InjectMode mode, HTMLInjector injector) {
    }
}
