import type { Metadata } from '@halo-dev/api-client'

export interface CodeSnippet {
  apiVersion: 'injector.erzbir.com/v1alpha1'
  kind: 'CodeSnippet'
  metadata: Metadata
  id: string
  name: string
  code: string
  description: string
  enabled: boolean
  /** @deprecated InjectionRule.snippetIds is the canonical relation source. */
  ruleIds: string[]
}

export type InjectionMode = 'HEAD' | 'FOOTER' | 'ID' | 'SELECTOR'
export type InjectionPosition = 'APPEND' | 'PREPEND' | 'BEFORE' | 'AFTER' | 'REPLACE'
export type MatchRuleType = 'GROUP' | 'PATH'
export type MatchRuleOperator = 'AND' | 'OR' | 'NOT' | 'AND_NOT' | 'OR_NOT'
export type MatchRuleMatcher = 'PATH_PATTERN' | 'ANT' | 'REGEX' | 'EXACT'

export interface MatchRule {
  type: MatchRuleType
  operator?: MatchRuleOperator
  matcher?: MatchRuleMatcher
  value?: string
  children?: MatchRule[]
}

/**
 * 精简的页面匹配表达：include 之间为 OR，exclude 恒为 AND_NOT。
 * 由前端编译为 matchRule 树并随规则一起提交，后端优先使用 pages。
 */
export interface RulePages {
  include: string[]
  exclude: string[]
  /** 显式匹配器，留空时按语法自动识别（后端 RuleConfigCodec 负责识别）。 */
  matcher?: MatchRuleMatcher | null
}

// Backward-compatible type for legacy editor component.
export interface PathMatchRule {
  pathPattern: string
}

export interface InjectionRule {
  apiVersion: 'injector.erzbir.com/v1alpha1'
  kind: 'InjectionRule'
  metadata: Metadata
  id: string
  name: string
  description: string
  enabled: boolean
  mode: InjectionMode
  match: string
  position: InjectionPosition
  matchRule: MatchRule
  /** 精简页面匹配表达，新表单优先使用；缺省时后端回退到 matchRule。 */
  pages?: RulePages | null
  snippetIds: string[]
}

export interface ItemList<T> {
  page: number
  size: number
  total: number
  items: Array<T>
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
  totalPages: number
}

export type ActiveTab = 'snippets' | 'rules'

export const MODE_OPTIONS: { value: InjectionMode; label: string }[] = [
  { value: 'HEAD', label: 'head' },
  { value: 'FOOTER', label: 'footer' },
  { value: 'ID', label: 'Element ID' },
  { value: 'SELECTOR', label: 'CSS Selector' },
]

export const POSITION_OPTIONS: { value: InjectionPosition; label: string }[] = [
  { value: 'APPEND', label: '内部末尾 (append)' },
  { value: 'PREPEND', label: '内部开头 (prepend)' },
  { value: 'BEFORE', label: '元素之前 (before)' },
  { value: 'AFTER', label: '元素之后 (after)' },
  { value: 'REPLACE', label: '替换元素 (replace)' },
]

export const MATCH_RULE_NODE_OPTIONS: { value: MatchRuleOperator; label: string }[] = [
  { value: 'AND', label: '与前项 AND' },
  { value: 'OR', label: '与前项 OR' },
]

export const PATH_MATCHER_OPTIONS: { value: MatchRuleMatcher; label: string }[] = [
  { value: 'PATH_PATTERN', label: 'Spring 路径模式' },
  { value: 'ANT', label: 'Ant 风格' },
  { value: 'REGEX', label: '正则表达式' },
  { value: 'EXACT', label: '精确匹配' },
]

export function makePathMatchRule(override: Partial<MatchRule> = {}): MatchRule {
  return {
    type: 'PATH',
    operator: 'AND',
    matcher: 'PATH_PATTERN',
    value: '/**',
    ...override,
  }
}

export function makeMatchRuleGroup(override: Partial<MatchRule> = {}): MatchRule {
  return {
    type: 'GROUP',
    operator: 'AND',
    children: [],
    ...override,
  }
}

export function makeSnippet(override: Partial<CodeSnippet> = {}): CodeSnippet {
  return {
    apiVersion: 'injector.erzbir.com/v1alpha1',
    kind: 'CodeSnippet',
    metadata: { name: '', generateName: 'CodeSnippet-' },
    id: '',
    name: '',
    code: '',
    description: '',
    enabled: true,
    ruleIds: [],
    ...override,
  }
}

export function makeRulePages(override: Partial<RulePages> = {}): RulePages {
  return {
    include: ['/**'],
    exclude: [],
    matcher: null,
    ...override,
  }
}

export function makeRule(override: Partial<InjectionRule> = {}): InjectionRule {
  return {
    apiVersion: 'injector.erzbir.com/v1alpha1',
    kind: 'InjectionRule',
    metadata: { name: '', generateName: 'InjectionRule-' },
    id: '',
    name: '',
    description: '',
    enabled: true,
    mode: 'HEAD',
    match: '',
    position: 'APPEND',
    matchRule: makePathMatchRule(),
    pages: makeRulePages(),
    snippetIds: [],
    ...override,
  }
}
