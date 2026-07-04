<template>
  <div class="project-preview">
    <section class="project-preview__hero">
      <div class="project-preview__copy">
        <span class="project-preview__eyebrow">Workspace Preview</span>
        <h2>Project is a planned capability. This phase only reserves the entry and information architecture.</h2>
        <p>
          This area will eventually organize papers, code, literature, experiment outputs, and conversations into the
          same research workspace so that multi-turn tasks, RAG, and long-term memory can collaborate around one topic.
        </p>
      </div>
      <div class="project-preview__actions">
        <NTag round type="warning" size="large">Preview Only</NTag>
        <NButton type="primary" size="large" disabled>
          Create Project (Coming Later)
        </NButton>
        <NButton secondary size="large" @click="router.push('/chat')">Return To Workspace</NButton>
      </div>
    </section>

    <section class="project-preview__grid">
      <NCard title="What will connect here later?" class="project-preview__card">
        <ul class="project-preview__list">
          <li>Paper drafts, polished outputs, and exported artifacts grouped around the same topic.</li>
          <li>Literature search results, BibTeX assets, and knowledge-base materials gathered in one place.</li>
          <li>Experiment notes, research decisions, and long-term memory anchored to a shared workspace.</li>
          <li>Future expansion points for code repositories, datasets, and execution history.</li>
        </ul>
      </NCard>

      <NCard title="What this phase will not do" class="project-preview__card">
        <ul class="project-preview__list">
          <li>It will not enable real project creation, member collaboration, or permissions yet.</li>
          <li>It will not force existing chat, paper, or knowledge-base flows into a project-driven model.</li>
          <li>It will not introduce complex asset binding or editing forms ahead of the runtime rebuild.</li>
          <li>It will not make users think Project is already production-ready.</li>
        </ul>
      </NCard>
    </section>

    <NCard title="Why keep this as preview first?" class="project-preview__detail">
      <p>
        The roadmap keeps the current phase focused on task lifecycle, event streams, RAG or memory integration, and
        the complex agent runtime. Leaving Project as a clear preview stabilizes navigation and product direction
        without dragging the mainline into a large project-management rewrite too early.
      </p>
    </NCard>
  </div>
</template>

<script setup lang="ts">
import { NButton, NCard, NTag } from 'naive-ui';
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<style scoped>
.project-preview {
  display: grid;
  gap: 24px;
}

.project-preview__hero {
  display: grid;
  gap: 20px;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.9fr);
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.18), transparent 38%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(30, 41, 59, 0.9));
  color: #f8fafc;
}

.project-preview__copy h2 {
  margin: 10px 0 14px;
  font-size: clamp(1.9rem, 2.6vw, 2.8rem);
  line-height: 1.08;
}

.project-preview__copy p {
  margin: 0;
  max-width: 66ch;
  color: rgba(226, 232, 240, 0.88);
  line-height: 1.7;
}

.project-preview__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  color: #bfdbfe;
  font-size: 0.82rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.project-preview__actions {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 20px;
  border-radius: 22px;
  background: rgba(15, 23, 42, 0.36);
  backdrop-filter: blur(12px);
}

.project-preview__grid {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.project-preview__card,
.project-preview__detail {
  border-radius: 24px;
}

.project-preview__list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 12px;
  line-height: 1.65;
}

.project-preview__detail p {
  margin: 0;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .project-preview__hero,
  .project-preview__grid {
    grid-template-columns: 1fr;
  }
}
</style>
