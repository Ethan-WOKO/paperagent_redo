<template>
  <section class="debug-section">
    <header>
      <strong>{{ title }}</strong>
      <span v-for="flag in flags || []" :key="flag">{{ flag }}</span>
      <small>{{ source }}</small>
    </header>
    <div><slot /></div>
  </section>
</template>

<script setup lang="ts">
defineProps<{ title: string; source: string; flags?: string[] }>();
</script>

<style scoped>
.debug-section { min-width: 0; padding: 8px 0; border-top: 1px solid var(--yb-border); }
.debug-section:first-of-type { border-top: 0; }
header { min-width: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 5px; }
header strong { margin-right: auto; }
header span { padding: 1px 4px; border: 1px solid var(--yb-border); border-radius: 4px; color: var(--yb-text-secondary); }
header small { flex-basis: 100%; color: var(--yb-text-muted); overflow-wrap: anywhere; }
.debug-section > div { min-width: 0; }
</style>
