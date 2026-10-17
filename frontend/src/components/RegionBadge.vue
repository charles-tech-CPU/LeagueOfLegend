<template>
  <span v-if="info" class="region-badge" :style="{ '--region-color': info.color }" :title="region">
    {{ info.label }}
  </span>
  <span v-else class="muted">{{ fallback }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { regionOf } from '../regions'

// region : valeur telle qu'en base ("LEC", "EMEA", "Korea"...), affichee normalisee et coloree
const props = defineProps({
  region: { type: String, default: null },
  fallback: { type: String, default: '—' }
})

const info = computed(() => regionOf(props.region))
</script>

<style scoped>
.region-badge {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 0.78em;
  font-weight: 700;
  white-space: nowrap;
  color: var(--region-color);
  background: color-mix(in srgb, var(--region-color) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--region-color) 35%, transparent);
}
</style>
