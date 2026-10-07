<template>
  <h1>📈 Statistiques</h1>

  <nav v-if="seasons.length" class="seasons" aria-label="Niveau de statistiques">
    <router-link :to="{ query: {} }" class="season-chip" :class="{ active: !selectedSeason }">
      Toutes saisons
    </router-link>
    <router-link
      v-for="s in seasons"
      :key="s.season"
      :to="{ query: { season: s.season } }"
      class="season-chip"
      :class="{ active: s.season === selectedSeason }"
    >
      {{ s.season }}
    </router-link>
  </nav>

  <StatsPanel :scope="scope" />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../services/api'
import StatsPanel from '../components/StatsPanel.vue'

const route = useRoute()
const seasons = ref([])

const selectedSeason = computed(() => Number(route.query.season) || null)
const scope = computed(() => (selectedSeason.value ? { season: selectedSeason.value } : {}))

onMounted(async () => {
  seasons.value = await api.getSeasons()
})
</script>

<style scoped>
.seasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 24px;
}
.season-chip {
  padding: 7px 16px;
  border-radius: 999px;
  background: var(--panel-alt);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-weight: 600;
  font-size: 0.88em;
}
.season-chip:hover {
  color: var(--text);
}
.season-chip.active {
  color: #0a0c18;
  background: var(--accent-grad);
  border-color: transparent;
}
</style>
