package com.erzbir.injector.halo.scheme;

import com.erzbir.injector.api.MatchRuleType;
import com.erzbir.injector.api.MatcherType;
import com.erzbir.injector.api.Operator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleConfigCodecTest {

    private RulePages pages(List<String> include, List<String> exclude) {
        RulePages pages = new RulePages();
        pages.setInclude(new java.util.ArrayList<>(include));
        pages.setExclude(new java.util.ArrayList<>(exclude));
        return pages;
    }

    @Test
    void shouldCompileSingleIncludeToAndPathRule() {
        MatchRule tree = RuleConfigCodec.compile(pages(List.of("/posts/**"), List.of()));

        assertEquals(MatchRuleType.GROUP, tree.getType());
        assertEquals(1, tree.getChildren().size());
        MatchRule child = tree.getChildren().get(0);
        assertEquals(MatchRuleType.PATH, child.getType());
        assertEquals(Operator.AND, child.getOperator());
        assertEquals("/posts/**", child.getValue());
        assertTrue(tree.valid());
    }

    @Test
    void shouldCompileMultipleIncludesAsOrAndExcludesAsAndNot() {
        MatchRule tree = RuleConfigCodec.compile(
                pages(List.of("/posts/**", "/archives/**"), List.of("/posts/admin/**")));

        assertEquals(3, tree.getChildren().size());
        assertEquals(Operator.AND, tree.getChildren().get(0).getOperator());
        assertEquals(Operator.OR, tree.getChildren().get(1).getOperator());
        assertEquals(Operator.AND_NOT, tree.getChildren().get(2).getOperator());
        assertTrue(tree.valid());
    }

    @Test
    void shouldReturnNullWhenNoValidInclude() {
        assertNull(RuleConfigCodec.compile(pages(List.of("  "), List.of())));
        assertNull(RuleConfigCodec.compile(null));
    }

    @Test
    void shouldDecompileCompiledTreeBackToPages() {
        RulePages original = pages(List.of("/posts/**", "/archives/**"), List.of("/admin/**"));
        MatchRule tree = RuleConfigCodec.compile(original);
        RulePages restored = RuleConfigCodec.decompile(tree);

        assertNotNull(restored);
        assertEquals(List.of("/posts/**", "/archives/**"), restored.getInclude());
        assertEquals(List.of("/admin/**"), restored.getExclude());
    }

    @Test
    void shouldNotDecompileArbitraryNestedTree() {
        MatchRule nested = MatchRule.groupRule(Operator.AND,
                MatchRule.groupRule(Operator.OR,
                        MatchRule.pathRule(MatcherType.PATH_PATTERN, "/a/**")),
                MatchRule.pathRule(MatcherType.PATH_PATTERN, "/b/**"));

        assertNull(RuleConfigCodec.decompile(nested));
    }

    @Test
    void shouldNotDecompileWhenExcludePrecedesInclude() {
        MatchRule tree = MatchRule.groupRule(Operator.AND,
                MatchRule.pathRule(Operator.AND_NOT, MatcherType.PATH_PATTERN, "/admin/**"),
                MatchRule.pathRule(Operator.OR, MatcherType.PATH_PATTERN, "/**"));

        assertNull(RuleConfigCodec.decompile(tree));
    }

    @Test
    void shouldAutoDetectMatcherBySyntax() {
        assertEquals(MatcherType.PATH_PATTERN, RuleConfigCodec.resolveMatcher("/posts/**", null));
        assertEquals(MatcherType.PATH_PATTERN, RuleConfigCodec.resolveMatcher("/posts/{slug}", null));
        assertEquals(MatcherType.REGEX, RuleConfigCodec.resolveMatcher("/posts/\\d+", null));
        assertEquals(MatcherType.EXACT, RuleConfigCodec.resolveMatcher("/archives", null));
        assertEquals(MatcherType.EXACT, RuleConfigCodec.resolveMatcher("/tags/foo.html", null));
    }

    @Test
    void explicitMatcherShouldWin() {
        assertEquals(MatcherType.ANT, RuleConfigCodec.resolveMatcher("/posts/**", MatcherType.ANT));
    }

    @Test
    void shouldReportErrorWhenIncludeMissing() {
        List<String> errors = RuleConfigCodec.validate(pages(List.of(), List.of()));
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("至少需要配置一个生效页面路径"));
    }

    @Test
    void shouldReportInvalidRegex() {
        RulePages pages = pages(List.of("[invalid("), List.of());
        List<String> errors = RuleConfigCodec.validate(pages);
        assertTrue(errors.stream().anyMatch(e -> e.contains("正则表达式语法无效")));
    }

    @Test
    void shouldReportPathNotStartingWithSlash() {
        RulePages pages = pages(List.of("posts/**"), List.of());
        List<String> errors = RuleConfigCodec.validate(pages);
        assertTrue(errors.stream().anyMatch(e -> e.contains("应以 / 开头")));
    }

    @Test
    void shouldPassForValidPages() {
        assertTrue(RuleConfigCodec.validate(pages(List.of("/posts/**"), List.of("/admin/**"))).isEmpty());
    }
}
