package com.erzbir.injector.halo.scheme;

import com.erzbir.injector.api.IInjectionRule;
import com.erzbir.injector.api.InjectMode;
import com.erzbir.injector.api.InjectPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Erzbir
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(kind = "InjectionRule", group = "injector.erzbir.com", version = "v1alpha1",
        singular = "injectionRule", plural = "injectionRules")
public class InjectionRule extends AbstractExtension implements IInjectionRule {
    private String name = "";
    private String description = "";
    private Boolean enabled = false;
    @NotNull(message = "InjectionRule mode must not be null")
    private InjectMode mode = InjectMode.HEAD;
    private String match = "";
    @NotNull(message = "InjectionRule position must not be null")
    private InjectPosition position = InjectPosition.APPEND;
    @Valid
    @NotNull(message = "InjectionRule matchRule must not be null")
    private MatchRule matchRule = MatchRule.defaultRule();
    /**
     * 精简的页面匹配表达，可选。新表单优先写入该字段；为空时回退使用 {@link #matchRule}。
     *
     * <p>运行时由 {@link RuleConfigCodec} 编译为引擎匹配树，旧数据仅有 matchRule 时行为不变。
     */
    @Valid
    private RulePages pages;
    @NotNull(message = "InjectionRule snippetIds must not be null")
    private Set<@NotBlank(message = "InjectionRule snippetId must not be blank") String> snippetIds = new LinkedHashSet<>();

    @Override
    public String getId() {
        return getMetadata().getName();
    }

    @Override
    public String getName() {
        if (this.name == null || this.name.isBlank()) {
            return getId();
        }
        return this.name;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 返回运行时实际生效的匹配树：存在合法 pages 时由其编译，否则回退到历史 matchRule。
     */
    public MatchRule getEffectiveMatchRule() {
        if (pages != null) {
            MatchRule compiled = RuleConfigCodec.compile(pages);
            if (compiled != null) {
                return compiled;
            }
        }
        return getMatchRule();
    }

    public boolean valid() {
        // 显式使用精简表达时，先对 pages 做字段级校验（含空 include / 非法正则 / 路径前缀）
        if (pages != null && !RuleConfigCodec.validate(pages).isEmpty()) {
            return false;
        }
        MatchRule effective = getEffectiveMatchRule();
        if (effective == null || !effective.valid()) {
            return false;
        }

        if (InjectMode.ID.equals(getMode()) || InjectMode.SELECTOR.equals(getMode())) {
            return getMatch() != null && !getMatch().isBlank();
        }

        return true;
    }

    @AssertTrue(message = "InjectionRule is invalid for current mode or matchRule")
    @SuppressWarnings("unused")
    private boolean isInjectionRuleValid() {
        return valid();
    }
}
