<script lang="ts" setup>
import { computed, ref } from 'vue'
import { VButton } from '@halo-dev/components'
import type { InjectionRule, RulePages } from '@/types'
import { MODE_OPTIONS, POSITION_OPTIONS } from '@/types'
import { compilePages, validatePages } from '@/views/composables/injectorDataUtils'
import MatchRuleNodeEditor from './MatchRuleNodeEditor.vue'
import FormField from './FormField.vue'
import SelectDropdown from './SelectDropdown.vue'

const props = withDefaults(
  defineProps<{
    rule: InjectionRule
    dirtyFields?: Partial<Record<keyof InjectionRule, boolean>>
    includeMatchRule?: boolean
  }>(),
  {
    includeMatchRule: true,
  },
)

const emit = defineEmits<{
  (e: 'update:rule', rule: InjectionRule): void
  (e: 'change'): void
  (e: 'revert-field', field: keyof InjectionRule): void
}>()

const needsTarget = computed(() => props.rule.mode === 'ID' || props.rule.mode === 'SELECTOR')

// 旧数据没有 pages 时默认进入高级（自定义表达式）模式
const customExpression = ref(!hasEffectivePages(props.rule))

function hasEffectivePages(rule: InjectionRule): boolean {
  return !!rule.pages && (rule.pages.include ?? []).some((p) => p?.trim())
}

function patch(partial: Partial<InjectionRule>) {
  emit('update:rule', { ...props.rule, ...partial })
  emit('change')
}

function updateField<K extends keyof InjectionRule>(key: K, value: InjectionRule[K]) {
  patch({ [key]: value } as Partial<InjectionRule>)
}

const pages = computed<RulePages>(
  () => props.rule.pages ?? { include: [], exclude: [], matcher: null },
)

const includeText = computed({
  get: () => pages.value.include.join('\n'),
  set: (text: string) => updatePages(text, pages.value.exclude.join('\n')),
})

const excludeText = computed({
  get: () => pages.value.exclude.join('\n'),
  set: (text: string) => updatePages(pages.value.include.join('\n'), text),
})

function splitLines(text: string): string[] {
  return text
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

function updatePages(includeRaw: string, excludeRaw: string) {
  const next: RulePages = {
    include: splitLines(includeRaw),
    exclude: splitLines(excludeRaw),
    matcher: pages.value.matcher ?? null,
  }
  // 同步编译 matchRule，保证提交体始终带一棵合法树（后端以 pages 为准）
  const compiled = compilePages(next)
  patch({ pages: next, matchRule: compiled ?? props.rule.matchRule })
}

const pageErrors = computed(() => validatePages(pages.value))

function useCustomExpression(enabled: boolean) {
  customExpression.value = enabled
  if (!enabled) {
    // 切回精简模式时确保有可用 pages
    if (!hasEffectivePages(props.rule)) {
      const fallback: RulePages = { include: ['/**'], exclude: [], matcher: null }
      patch({ pages: fallback, matchRule: compilePages(fallback) ?? props.rule.matchRule })
    }
  } else {
    // 进入高级模式：清空 pages，后端回退使用 matchRule
    patch({ pages: null })
  }
}
</script>

<template>
  <FormField label="注入模式" required>
    <template #action>
      <VButton
        :class="dirtyFields?.mode ? '' : ':uno: invisible pointer-events-none'"
        size="xs"
        @click="emit('revert-field', 'mode')"
      >
        撤销修改
      </VButton>
    </template>
    <SelectDropdown
      :model-value="rule.mode"
      :options="MODE_OPTIONS"
      @update:model-value="updateField('mode', $event as InjectionRule['mode'])"
    />
    <p
      v-if="rule.mode === 'SELECTOR' || rule.mode === 'ID'"
      class=":uno: mt-2 rounded-md border border-yellow-200 bg-yellow-50 px-3 py-2 text-xs text-yellow-700"
    >
      使用此模式会带来额外性能开销, 建议仅在必要场景使用
    </p>
  </FormField>

  <template v-if="needsTarget">
    <FormField :label="rule.mode === 'SELECTOR' ? 'CSS 选择器' : '元素 ID'" required>
      <template #action>
        <VButton
          :class="dirtyFields?.match ? '' : ':uno: invisible pointer-events-none'"
          size="xs"
          @click="emit('revert-field', 'match')"
        >
          撤销修改
        </VButton>
      </template>
      <textarea
        rows="1"
        :placeholder="rule.mode === 'SELECTOR' ? 'div[class=content]' : 'main-content'"
        :value="rule.match"
        :class="
          rule.match.trim()
            ? ':uno: w-full min-h-[34px] resize-y rounded-md border border-gray-200 px-3 py-1.5 text-sm font-mono focus:border-primary focus:outline-none'
            : ':uno: w-full min-h-[34px] resize-y rounded-md border border-red-400 px-3 py-1.5 text-sm font-mono focus:border-red-500 focus:outline-none'
        "
        @input="updateField('match', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>
    </FormField>

    <FormField label="插入位置">
      <template #action>
        <VButton
          :class="dirtyFields?.position ? '' : ':uno: invisible pointer-events-none'"
          size="xs"
          @click="emit('revert-field', 'position')"
        >
          撤销修改
        </VButton>
      </template>
      <SelectDropdown
        :model-value="rule.position"
        :options="POSITION_OPTIONS"
        @update:model-value="updateField('position', $event as InjectionRule['position'])"
      />
    </FormField>
  </template>

  <template v-if="includeMatchRule">
    <FormField label="生效页面" required>
      <template #action>
        <VButton
          :class="dirtyFields?.matchRule ? '' : ':uno: invisible pointer-events-none'"
          size="xs"
          @click="emit('revert-field', 'matchRule')"
        >
          撤销修改
        </VButton>
      </template>

      <div v-if="!customExpression">
        <textarea
          rows="2"
          :value="includeText"
          placeholder="每行一条路径, 例如 /posts/**"
          class=":uno: w-full min-h-[34px] resize-y rounded-md border border-gray-200 px-3 py-1.5 font-mono text-sm focus:border-primary focus:outline-none"
          @input="includeText = ($event.target as HTMLTextAreaElement).value"
        ></textarea>
        <p class=":uno: mt-1 text-xs text-gray-500">多个路径之间为「或」关系, 命中任意一条即生效</p>

        <details class=":uno: mt-2">
          <summary class=":uno: cursor-pointer text-xs text-gray-500">排除页面（可选）</summary>
          <textarea
            rows="2"
            :value="excludeText"
            placeholder="每行一条路径, 例如 /posts/admin/**"
            class=":uno: mt-1 w-full min-h-[34px] resize-y rounded-md border border-gray-200 px-3 py-1.5 font-mono text-sm focus:border-primary focus:outline-none"
            @input="excludeText = ($event.target as HTMLTextAreaElement).value"
          ></textarea>
          <p class=":uno: mt-1 text-xs text-gray-500">命中排除路径时不注入</p>
        </details>

        <ul v-if="pageErrors.length" class=":uno: mt-2 space-y-1">
          <li
            v-for="(err, i) in pageErrors"
            :key="i"
            class=":uno: rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs text-red-600"
          >
            {{ err }}
          </li>
        </ul>
      </div>

      <div v-else>
        <MatchRuleNodeEditor
          :model-value="rule.matchRule"
          @change="emit('change')"
          @update:model-value="updateField('matchRule', $event)"
        />
      </div>

      <button
        type="button"
        class=":uno: mt-2 text-xs text-primary hover:underline"
        @click="useCustomExpression(!customExpression)"
      >
        {{ customExpression ? '返回简单页面配置' : '高级：自定义匹配表达式（AND/OR/正则/嵌套）' }}
      </button>
    </FormField>
  </template>

  <details class=":uno: rounded-md border border-gray-200 px-3 py-2">
    <summary class=":uno: cursor-pointer text-sm text-gray-600">名称与描述（可选）</summary>

    <FormField label="名称" class=":uno: mt-3">
      <template #action>
        <VButton
          :class="dirtyFields?.name ? '' : ':uno: invisible pointer-events-none'"
          size="xs"
          @click="emit('revert-field', 'name')"
        >
          撤销修改
        </VButton>
      </template>
      <input
        :value="rule.name"
        class=":uno: w-full rounded-md border border-gray-200 px-3 py-1.5 text-sm focus:border-primary focus:outline-none"
        placeholder="不填默认为 ID"
        @input="updateField('name', ($event.target as HTMLInputElement).value)"
      />
    </FormField>

    <FormField label="描述">
      <template #action>
        <VButton
          :class="dirtyFields?.description ? '' : ':uno: invisible pointer-events-none'"
          size="xs"
          @click="emit('revert-field', 'description')"
        >
          撤销修改
        </VButton>
      </template>
      <textarea
        rows="1"
        :value="rule.description"
        class=":uno: w-full min-h-[34px] resize-y rounded-md border border-gray-200 px-3 py-1.5 text-sm focus:border-primary focus:outline-none"
        placeholder="说明此规则的用途"
        @input="updateField('description', ($event.target as HTMLTextAreaElement).value)"
      ></textarea>
    </FormField>
  </details>
</template>
