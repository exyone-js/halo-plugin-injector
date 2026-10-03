package com.erzbir.injector.halo.scheme;

import com.erzbir.injector.api.MatchRuleType;
import com.erzbir.injector.api.MatcherType;
import com.erzbir.injector.api.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 在精简的 {@link RulePages} 表达与引擎使用的 {@link MatchRule} 树之间转换。
 *
 * <p>引擎内部保持原有递归树结构不变，本类仅作为存储 / 视图与引擎之间的边界适配。
 *
 * @author Erzbir
 * @since 1.1.0
 */
public final class RuleConfigCodec {

    private RulePages() {
    }

    /**
     * 将精简页面表达编译为引擎匹配树。
     *
     * <p>编译结果为一个 AND 组：第一个 include 为基准（AND），后续 include 以 OR 连接，
     * 每个 exclude 以 AND_NOT 连接。
     *
     * @return 编译后的匹配树；若没有有效的 include 则返回 null
     */
    public static MatchRule compile(RulePages pages) {
        if (pages == null) {
            return null;
        }
        List<String> includes = clean(pages.getInclude());
        List<String> excludes = clean(pages.getExclude());
        if (includes.isEmpty()) {
            return null;
        }
        List<MatchRule> children = new ArrayList<>(includes.size() + excludes.size());
        boolean first = true;
        for (String pattern : includes) {
            MatcherType matcher = resolveMatcher(pattern, pages.getMatcher());
            children.add(MatchRule.pathRule(first ? Operator.AND : Operator.OR, matcher, pattern));
            first = false;
        }
        for (String pattern : excludes) {
            MatcherType matcher = resolveMatcher(pattern, pages.getMatcher());
            children.add(MatchRule.pathRule(Operator.AND_NOT, matcher, pattern));
        }
        return MatchRule.groupRule(Operator.AND, children);
    }

    /**
     * 尝试将引擎匹配树还原为精简表达。
     *
     * <p>仅能还原由 {@link #compile(RulePages)} 生成的标准形态
     * （AND 组内若干 include 后接若干 exclude）。遇到任意嵌套或取反结构时返回 null，
     * 调用方应回退到原始树（高级自定义表达式）。
     */
    public static RulePages decompile(MatchRule rule) {
        if (rule == null || !MatchRuleType.GROUP.equals(rule.getType())
                || rule.getChildren() == null || rule.getChildren().isEmpty()) {
            return null;
        }
        List<MatchRule> children = rule.getChildren();
        RulePages pages = new RulePages();
        boolean seenExclude = false;
        MatcherType commonMatcher = null;
        boolean first = true;
        for (MatchRule child : children) {
            if (child == null || !MatchRuleType.PATH.equals(child.getType())) {
                return null;
            }
            Operator op = child.getOperator();
            if (first) {
                if (op != null && op.isNegated()) {
                    return null;
                }
            }
            boolean isInclude = op == null || (!op.isNegated());
            if (!isInclude) {
                if (!Operator.AND_NOT.equals(op) && !Operator.NOT.equals(op)) {
                    return null;
                }
                seenExclude = true;
            } else if (seenExclude) {
                // include 出现在 exclude 之后，不是标准形态
                return null;
            }
            if (child.getValue() == null || child.getValue().isBlank()) {
                return null;
            }
            if (isInclude) {
                pages.getInclude().add(child.getValue());
            } else {
                pages.getExclude().add(child.getValue());
            }
            if (commonMatcher == null) {
                commonMatcher = child.getMatcher();
            } else if (commonMatcher != child.getMatcher()) {
                commonMatcher = null;
            }
            first = false;
        }
        if (pages.getInclude().isEmpty()) {
            return null;
        }
        pages.setMatcher(commonMatcher);
        return pages;
    }

    /**
     * 根据表达式语法自动识别匹配器。显式指定的 matcher 优先。
     */
    public static MatcherType resolveMatcher(String pattern, MatcherType explicit) {
        if (explicit != null) {
            return explicit;
        }
        if (pattern == null || pattern.isBlank()) {
            return MatcherType.PATH_PATTERN;
        }
        if (pattern.contains("*") || pattern.contains("?") || pattern.contains("{")
                || pattern.contains("}")) {
            // Spring PathPattern 支持 *、? 与 {var} 模板，且与 Ant 对用户直觉等价
            return MatcherType.PATH_PATTERN;
        }
        if (looksRegex(pattern)) {
            return MatcherType.REGEX;
        }
        return MatcherType.EXACT;
    }

    private static boolean looksRegex(String pattern) {
        // 出现强正则特征才视为正则；单独的 "." 在路径（如 .html）中很常见，故不作为判据
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' || c == '^' || c == '$' || c == '+' || c == '|' || c == '('
                    || c == ')' || c == '[') {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验精简表达，返回字段级错误信息；返回空列表表示通过。
     */
    public static List<String> validate(RulePages pages) {
        List<String> errors = new ArrayList<>();
        if (pages == null) {
            errors.add("页面匹配规则不能为空");
            return errors;
        }
        List<String> includes = clean(pages.getInclude());
        List<String> excludes = clean(pages.getExclude());
        if (includes.isEmpty()) {
            errors.add("至少需要配置一个生效页面路径");
        }
        validatePatterns(includes, "include", pages.getMatcher(), errors);
        validatePatterns(excludes, "exclude", pages.getMatcher(), errors);
        return errors;
    }

    private static void validatePatterns(List<String> patterns, String field,
                                         MatcherType explicit, List<String> errors) {
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            MatcherType matcher = resolveMatcher(pattern, explicit);
            if (MatcherType.REGEX.equals(matcher)) {
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    errors.add(field + "[" + i + "] 正则表达式语法无效: " + e.getDescription());
                }
            } else if (!pattern.startsWith("/")) {
                errors.add(field + "[" + i + "] 路径应以 / 开头: " + pattern);
            }
        }
    }

    private static List<String> clean(List<String> source) {
        List<String> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (String s : source) {
            if (s != null && !s.isBlank()) {
                String trimmed = s.trim();
                if (!result.contains(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
