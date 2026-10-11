<template>
  <span class="team-tag" :title="name || code || ''">
    <span class="logo-box">
      <img v-if="hasLogo && id" :src="teamLogoUrl(id)" :alt="code ?? ''" />
      <span v-else>{{ (code ?? '').slice(0, 2) }}</span>
    </span>
    <span class="team-code">{{ code ?? '—' }}</span>
  </span>
</template>

<script setup>
import { teamLogoUrl } from '../services/api'

// Logo (ou initiales) et trigramme d'une equipe
defineProps({
  id: { type: [Number, String], default: null },
  code: { type: String, default: null },
  name: { type: String, default: null },
  hasLogo: { type: Boolean, default: false }
})
</script>

<style scoped>
.team-tag {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
}
.logo-box {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 7px;
  background: var(--logo-bg);
  font-size: 0.65em;
  font-weight: 800;
  color: var(--text-muted);
}
.logo-box img {
  width: 19px;
  height: 19px;
  object-fit: contain;
}
.team-code {
  font-size: 0.82em;
  font-weight: 800;
  color: var(--text-muted);
}
</style>
