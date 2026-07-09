<template>
  <AppLayout>
    <div class="workbench-page scholar-page project-page">
      <WorkspaceHero
        kicker="Projects"
        title="Project Workspace Preview"
        subtitle="Reserve the entry, structure, and future shared context for papers, literature, notes, artifacts, and long-running research tasks without turning the current product into a full project manager yet."
        storage-key="yanban.hero.projects"
      >
        <template #actions>
          <NSpace align="center">
            <NTag round type="warning">Preview only</NTag>
            <NButton secondary @click="router.push('/chat')">Open Workspace</NButton>
            <NButton type="primary" disabled>Create Project</NButton>
          </NSpace>
        </template>
      </WorkspaceHero>

      <div class="scholar-metric-strip">
        <article class="scholar-metric-card">
          <span>Status</span>
          <strong>Planned</strong>
        </article>
        <article class="scholar-metric-card">
          <span>Current scope</span>
          <strong>IA + navigation</strong>
        </article>
        <article class="scholar-metric-card">
          <span>Shared assets later</span>
          <strong>Papers, KB, chat</strong>
        </article>
        <article class="scholar-metric-card">
          <span>Collaboration</span>
          <strong>Not enabled</strong>
        </article>
      </div>

      <div class="project-page__grid">
        <NCard class="workbench-card scholar-card" :bordered="false">
          <template #header>
            <div class="section-title">What will connect here later?</div>
          </template>
          <ul class="project-page__list">
            <li>Paper drafts, polish outputs, and export artifacts grouped around the same topic.</li>
            <li>Literature search results, BibTeX assets, and knowledge-base material gathered in one place.</li>
            <li>Experiment notes, research decisions, and long-term memory anchored to a shared workspace.</li>
            <li>Future expansion points for repositories, datasets, execution history, and research milestones.</li>
          </ul>
        </NCard>

        <NCard class="workbench-card scholar-card" :bordered="false">
          <template #header>
            <div class="section-title">What this phase will not do</div>
          </template>
          <ul class="project-page__list">
            <li>It will not enable real project creation, permissions, or multi-member collaboration yet.</li>
            <li>It will not force existing chat, paper, or knowledge-base flows into a project-only workflow.</li>
            <li>It will not introduce complex asset binding or editing forms ahead of the runtime rebuild.</li>
            <li>It will not present Project as a finished production surface before the backend is ready.</li>
          </ul>
        </NCard>
      </div>

      <NCard class="workbench-card scholar-card project-page__detail" :bordered="false">
        <template #header>
          <div class="section-title">Why keep it as preview first?</div>
        </template>
        <p>
          The current roadmap is still focused on task lifecycle, event streams, retrieval quality, and agent runtime
          behavior. Keeping Projects as a clear preview lets the product grow a stable information architecture first,
          without dragging the core workflows into a premature project-management rewrite.
        </p>
      </NCard>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { NButton, NCard, NSpace, NTag } from 'naive-ui';
import { useRouter } from 'vue-router';
import AppLayout from '@/components/AppLayout.vue';
import WorkspaceHero from '@/components/WorkspaceHero.vue';

const router = useRouter();
</script>

<style scoped>
.project-page {
  display: grid;
  gap: 16px;
}

.project-page__grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.project-page__list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 10px;
  line-height: 1.58;
}

.project-page__detail p {
  margin: 0;
  line-height: 1.62;
}

@media (max-width: 980px) {
  .project-page__grid {
    grid-template-columns: 1fr;
  }
}
</style>
