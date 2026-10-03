<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { type CodeSnippet, type InjectionRule, makeRule } from '@/types'
import BaseFormModal from './BaseFormModal.vue'
import RuleFields from './RuleFields.vue'
import RelationPicker from './RelationPicker.vue'
import { isSameJson, isValidMatchRule, isValidPages } from '@/views/composables/injectorDataUtils'

defineProps<{
  snippets: CodeSnippet[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'submit', rule: InjectionRule, snippetIds: string[]): void
}>()

const rule = ref<InjectionRule>(makeRule())
const selectedSnippetIds = ref<string[]>([])
const dirty = computed(
  () => !isSameJson(rule.value, makeRule()) || selectedSnippetIds.value.length > 0,
)
const valid = computed(
  () =>
    selectedSnippetIds.value.length > 0 &&
    (rule.value.pages ? isValidPages(rule.value.pages) : isValidMatchRule(rule.value.matchRule)) &&
    (!['SELECTOR', 'ID'].includes(rule.value.mode) || !!rule.value.match.trim()),
)

onMounted(reset)

function reset() {
  rule.value = makeRule()
  selectedSnippetIds.value = []
}

function toggleSnippet(id: string) {
  const idx = selectedSnippetIds.value.indexOf(id)
  if (idx === -1) selectedSnippetIds.value.push(id)
  else selectedSnippetIds.value.splice(idx, 1)
}

function handleSubmit() {
  emit('submit', rule.value, selectedSnippetIds.value)
}

function handleRuleUpdate(nextRule: InjectionRule) {
  rule.value = nextRule
}
</script>

<template>
  <BaseFormModal
    :saving="saving"
    :dirty="dirty"
    :valid="valid"
    title="新建注入规则"
    @close="emit('close')"
    @submit="handleSubmit"
  >
    <template #form>
      <RuleFields :rule="rule" @update:rule="handleRuleUpdate" />
    </template>

    <template #picker>
      <RelationPicker
        label="关联代码片段"
        :items="snippets"
        :selected-ids="selectedSnippetIds"
        empty-text="暂无代码片段, 请先创建"
        @toggle="toggleSnippet"
      />
    </template>
  </BaseFormModal>
</template>
