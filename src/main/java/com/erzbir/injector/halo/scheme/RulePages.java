package com.erzbir.injector.halo.scheme;

import com.erzbir.injector.api.MatcherType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 精简的页面匹配表达，用于替代直接暴露递归 {@link MatchRule} 树。
 *
 * <p>语义：{@code include} 之间为 OR，{@code exclude} 恒为 AND_NOT 取反，
 * 即命中任意 include 且不命中任何 exclude 时匹配成功。
 *
 * <p>该类型只作为请求 / 视图层的精简表达，落库时由
 * {@link RuleConfigCodec} 编译为引擎使用的 {@link MatchRule} 树。
 *
 * @author Erzbir
 * @since 1.1.0
 */
@Data
public class RulePages {
    /**
     * 需要命中的路径表达式，彼此为 OR 关系。
     */
    private List<String> include = new ArrayList<>();

    /**
     * 需要排除的路径表达式，彼此为 AND_NOT 关系。
     */
    private List<String> exclude = new ArrayList<>();

    /**
     * 显式指定匹配器；为空时按表达式语法自动识别。
     */
    private MatcherType matcher;

    public static RulePages all() {
        RulePages pages = new RulePages();
        pages.getInclude().add("/**");
        return pages;
    }
}
