<template>
  <details class="context-debug">
    <summary>
      <span>{{ title }}</span>
      <small v-if="snapshot?.context">
        {{ snapshot.context.estimatedCharacters }} / {{ snapshot.context.effectiveBudgetCharacters }} chars
      </small>
      <button type="button" :disabled="loading" @click.prevent="$emit('refresh')">
        {{ loading ? loadingLabel : refreshLabel }}
      </button>
    </summary>
    <div class="context-debug__body">
      <p v-if="error" class="context-debug__error">{{ error }}</p>
      <p v-else-if="!snapshot?.context" class="context-debug__empty">{{ emptyLabel }}</p>
      <template v-else>
        <dl class="context-debug__metrics">
          <dt>trace / turn</dt><dd>{{ snapshot.traceId || '—' }} / {{ snapshot.turnId }}</dd>
          <dt>budget</dt><dd>{{ snapshot.context.estimatedCharacters }} / {{ snapshot.context.effectiveBudgetCharacters }} (requested {{ snapshot.context.requestedBudgetCharacters }})</dd>
          <dt>messages</dt><dd>raw {{ snapshot.rawMessageCount }}, canonical {{ snapshot.normalizedMessageCount }}, context {{ snapshot.contextMessageCount }}</dd>
        </dl>

        <DebugSection :title="currentLabel" :source="snapshot.context.currentMessage.source"
          :flags="snapshot.context.currentMessage.truncated ? ['TRUNCATED'] : []">
          <pre>{{ snapshot.context.currentMessage.content || '—' }}</pre>
        </DebugSection>

        <DebugSection :title="`${recentLabel} (${snapshot.context.recentTurns.length})`" source="agent_turns canonical ids">
          <article v-for="turn in snapshot.context.recentTurns" :key="turn.turnId" class="context-debug__turn">
            <small>turn {{ turn.turnId }} · user {{ turn.userMessageId }} · assistant {{ turn.assistantMessageId }} · {{ turn.estimatedCharacters }} chars</small>
            <strong>user</strong><pre>{{ turn.user }}</pre>
            <strong>assistant</strong><pre>{{ turn.assistant }}</pre>
          </article>
          <p v-if="snapshot.context.recentTurns.length === 0">—</p>
        </DebugSection>

        <DebugSection :title="summaryLabel" :source="snapshot.context.sessionSummary.source"
          :flags="[snapshot.context.sessionSummary.present ? 'PRESENT' : 'EMPTY', ...(snapshot.context.sessionSummary.truncated ? ['TRUNCATED'] : [])]">
          <pre>{{ snapshot.context.sessionSummary.content || '—' }}</pre>
        </DebugSection>

        <DebugSection :title="projectLabel" :source="snapshot.context.project?.source || 'not_project_context'">
          <dl v-if="snapshot.context.project">
            <dt>projectId</dt><dd>{{ snapshot.context.project.projectId }}</dd>
            <dt>ProjectVersion</dt><dd>{{ snapshot.context.project.projectVersion }}</dd>
          </dl>
          <p v-else>—</p>
        </DebugSection>

        <DebugSection :title="`${evidenceLabel} (${snapshot.context.evidence.length})`" source="ContextPackage EvidenceLedger">
          <dl v-for="item in snapshot.context.evidence" :key="item.id" class="context-debug__evidence">
            <dt>id</dt><dd>{{ item.id }}</dd>
            <dt>source/status</dt><dd>{{ item.sourceType }} / {{ item.versionStatus }}</dd>
            <dt>file/chunk</dt><dd>{{ item.file || '—' }} / {{ item.chunk || '—' }}</dd>
            <dt>version</dt><dd>{{ item.projectVersion || item.version || '—' }}</dd>
          </dl>
          <p v-if="snapshot.context.evidence.length === 0">—</p>
        </DebugSection>

        <DebugSection :title="memoryLabel" :source="snapshot.context.longTermMemory.source"
          :flags="[`INCLUDED ${snapshot.context.longTermMemory.includedCount}`, `OMITTED ${snapshot.context.longTermMemory.omittedCount}`, ...(snapshot.context.longTermMemory.truncated ? ['TRUNCATED'] : [])]">
          <pre>{{ snapshot.context.longTermMemory.content || '—' }}</pre>
          <small>{{ snapshot.context.longTermMemory.note }}</small>
        </DebugSection>

        <DebugSection :title="sectionsLabel" source="ContextPackage">
          <dl v-for="item in snapshot.context.sections" :key="`${item.type}:${item.note}`" class="context-debug__row">
            <dt>{{ item.type }}</dt><dd>included {{ item.itemCount }} · {{ item.estimatedCharacters }} chars · {{ item.note }}</dd>
          </dl>
        </DebugSection>

        <DebugSection :title="`${droppedLabel} (${snapshot.context.droppedItems.length})`" source="ContextPackage budget/window decisions">
          <dl v-for="item in snapshot.context.droppedItems" :key="`${item.type}:${item.reason}`" class="context-debug__row">
            <dt>{{ item.type }}</dt><dd>{{ item.count }} · {{ item.reason }}</dd>
          </dl>
          <p v-if="snapshot.context.droppedItems.length === 0">—</p>
        </DebugSection>
      </template>
    </div>
  </details>
</template>

<script setup lang="ts">
import type { AgentContextSnapshotResponse } from '@/api/agent';
import DebugSection from './ProjectContextDebugSection.vue';

defineProps<{
  snapshot: AgentContextSnapshotResponse | null;
  loading: boolean;
  error: string;
  title: string;
  refreshLabel: string;
  loadingLabel: string;
  emptyLabel: string;
  currentLabel: string;
  recentLabel: string;
  summaryLabel: string;
  projectLabel: string;
  evidenceLabel: string;
  memoryLabel: string;
  sectionsLabel: string;
  droppedLabel: string;
}>();
defineEmits<{ refresh: [] }>();
</script>

<style scoped>
.context-debug { flex: 0 0 auto; min-width: 0; border-top: 1px solid var(--yb-border); background: var(--yb-bg-muted); font: 10px/1.45 ui-monospace, SFMono-Regular, Consolas, monospace; }
.context-debug > summary { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 8px; padding: 7px 9px; cursor: pointer; }
.context-debug > summary span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 700; }
.context-debug > summary small { color: var(--yb-text-muted); white-space: nowrap; }
.context-debug button { border: 0; background: transparent; color: var(--yb-primary); cursor: pointer; }
.context-debug__body { box-sizing: border-box; max-height: min(44vh, 440px); min-width: 0; overflow: auto; padding: 8px; border-top: 1px solid var(--yb-border); }
.context-debug__metrics, .context-debug__row, .context-debug__evidence, .context-debug__turn dl { min-width: 0; display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 3px 8px; margin: 0 0 7px; }
dt { color: var(--yb-text-muted); }
dd { min-width: 0; margin: 0; overflow-wrap: anywhere; }
pre { box-sizing: border-box; max-width: 100%; margin: 4px 0; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.context-debug__turn + .context-debug__turn, .context-debug__evidence + .context-debug__evidence { padding-top: 7px; border-top: 1px dashed var(--yb-border); }
.context-debug__turn strong { display: block; margin-top: 4px; color: var(--yb-text-muted); }
.context-debug__error { color: var(--yb-danger, #dc2626); }
.context-debug__empty { color: var(--yb-text-muted); }
@media (max-width: 390px) {
  .context-debug > summary { grid-template-columns: minmax(0, 1fr) auto; }
  .context-debug > summary small { grid-column: 1 / -1; grid-row: 2; white-space: normal; }
  .context-debug__metrics, .context-debug__row, .context-debug__evidence { grid-template-columns: minmax(0, 1fr); }
}
</style>
