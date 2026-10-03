import type { ItemList, MatchRule, MatchRuleMatcher, RulePages } from '@/types'
import { uniqueStrings } from './util'

export function emptyList<T>(): ItemList<T> {
  return {
    first: false,
    hasNext: false,
    hasPrevious: false,
    last: false,
    page: 0,
    size: 0,
    totalPages: 0,
    items: [],
    total: 0,
  }
}

export function isValidMatchRule(rule?: MatchRule): boolean {
  if (!rule) return false
  const nodeOperator = rule.operator ?? 'AND'
  if (!['AND', 'OR', 'NOT', 'AND_NOT', 'OR_NOT'].includes(nodeOperator)) return false
  if (rule.type === 'GROUP') {
    const children = rule.children ?? []
    return children.length > 0 && children.every(isValidMatchRule)
  }
  if (!rule.matcher || !rule.value?.trim()) return false
  if (rule.matcher === 'REGEX') {
    try {
      new RegExp(rule.value)
    } catch {
      return false
    }
  }
  if (rule.type === 'PATH') {
    return ['PATH_PATTERN', 'ANT', 'REGEX', 'EXACT'].includes(rule.matcher)
  }
  return false
}

export function cloneValue<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/**
 * 按路径语法自动识别匹配器，规则与后端 RuleConfigCodec 保持一致。
 */
export function resolveMatcher(pattern: string, explicit?: MatchRuleMatcher | null): MatchRuleMatcher {
  if (explicit) return explicit
  if (/[*?{}]/.test(pattern)) return 'PATH_PATTERN'
  if (/[\\^$+|()\[]/.test(pattern)) return 'REGEX'
  return 'EXACT'
}

function cleanPatterns(list?: string[] | null): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const raw of list ?? []) {
    const v = raw?.trim()
    if (v && !seen.has(v)) {
      seen.add(v)
      out.push(v)
    }
  }
  return out
}

/**
 * 将精简 pages 表达编译为引擎 matchRule 树：
 * include 首项 AND、其余 OR，exclude 一律 AND_NOT。
 */
export function compilePages(pages?: RulePages | null): MatchRule | null {
  if (!pages) return null
  const include = cleanPatterns(pages.include)
  const exclude = cleanPatterns(pages.exclude)
  if (include.length === 0) return null
  const children: MatchRule[] = include.map((value, i) => ({
    type: 'PATH',
    operator: i === 0 ? 'AND' : 'OR',
    matcher: resolveMatcher(value, pages.matcher),
    value,
  }))
  for (const value of exclude) {
    children.push({
      type: 'PATH',
      operator: 'AND_NOT',
      matcher: resolveMatcher(value, pages.matcher),
      value,
    })
  }
  return { type: 'GROUP', operator: 'AND', children }
}

/**
 * 校验 pages，返回字段级中文错误信息；空数组表示通过。
 */
export function validatePages(pages?: RulePages | null): string[] {
  const errors: string[] = []
  if (!pages) {
    errors.push('页面匹配规则不能为空')
    return errors
  }
  const include = cleanPatterns(pages.include)
  if (include.length === 0) {
    errors.push('至少需要配置一个生效页面路径')
  }
  for (const [field, list] of [['include', include], ['exclude', cleanPatterns(pages.exclude)]] as const) {
    list.forEach((pattern, i) => {
      const matcher = resolveMatcher(pattern, pages.matcher)
      if (matcher === 'REGEX') {
        try {
          new RegExp(pattern)
        } catch (e) {
          errors.push(`${field}[${i}] 正则表达式语法无效: ${(e as Error).message}`)
        }
      } else if (!pattern.startsWith('/')) {
        errors.push(`${field}[${i}] 路径应以 / 开头: ${pattern}`)
      }
    })
  }
  return errors
}

export function isValidPages(pages?: RulePages | null): boolean {
  return validatePages(pages).length === 0
}

export function normalizedIds(ids: string[]) {
  return [...uniqueStrings(ids)].sort()
}

export function isSameJson(a: unknown, b: unknown) {
  return JSON.stringify(a) === JSON.stringify(b)
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== 'object') return fallback
  const responseMessage = (error as { response?: { data?: { message?: unknown } } }).response?.data
    ?.message
  const directMessage = (error as { message?: unknown }).message
  const detail =
    typeof responseMessage === 'string'
      ? responseMessage.trim()
      : typeof directMessage === 'string'
        ? directMessage.trim()
        : ''
  return detail ? `${fallback}: ${detail}` : fallback
}
