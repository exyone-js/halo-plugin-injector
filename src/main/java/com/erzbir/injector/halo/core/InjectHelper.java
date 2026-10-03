package com.erzbir.injector.halo.core;

import com.erzbir.injector.api.InjectMode;
import com.erzbir.injector.halo.manager.CodeSnippetManager;
import com.erzbir.injector.halo.manager.InjectionRuleManager;
import com.erzbir.injector.halo.scheme.CodeSnippet;
import com.erzbir.injector.halo.scheme.InjectionRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Erzbir
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InjectHelper {
    private final InjectionRuleManager ruleManager;
    private final CodeSnippetManager snippetManager;
    private final MatchRuleResolver matchRuleResolver = new MatchRuleResolver();

    public Flux<InjectionRule> getMatchedRules(String targetPath,
                                               InjectMode mode) {
        if (targetPath.isEmpty()) {
            return Flux.empty();
        }

        return ruleManager.listRuleByMode(mode)
                .filter(InjectionRule::isEnabled)
                .filter(rule -> matchRuleResolver.matches(rule.getEffectiveMatchRule(), targetPath))
                .onErrorResume(e -> {
                    log.warn("Failed to resolve matched rules, mode: {}, path: {}", mode,
                        targetPath, e);
                    return Flux.empty();
                });
    }

    public Mono<String> getConcatCode(InjectionRule rule) {
        return Flux.fromIterable(rule.getSnippetIds())
                .concatMap(snippetManager::get)
                .filter(CodeSnippet::isEnabled)
                .map(CodeSnippet::getCode)
                .collectList()
                .map(codes -> String.join("", codes));
    }
}
