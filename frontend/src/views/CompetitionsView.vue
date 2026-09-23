<template>
  <header class="page-head">
    <h1>Compétitions</h1>
    <p class="subtitle">Choisis une ligue pour voir son classement, son bracket et ses résultats.</p>
  </header>

  <template v-for="section in sections" :key="section.type">
    <h2 v-if="section.items.length">{{ section.label }}</h2>
    <div v-if="section.items.length" class="grid">
      <router-link
        v-for="c in section.items"
        :key="c.id"
        :to="`/competitions/${c.id}`"
        class="comp-card"
        :style="{ '--league-color': leagueColor(c.code) }"
      >
        <span class="card-top">
          <span class="league-badge">{{ c.code }}</span>
          <span v-if="stats[c.id]?.status" class="state" :class="stats[c.id].status.tone">{{ stats[c.id].status.label }}</span>
        </span>
        <span class="comp-name">{{ c.name }}</span>
        <span class="comp-meta">{{ c.region ?? 'International' }} · Saison {{ c.season }}</span>
        <span v-if="stats[c.id]" class="comp-progress">
          <span class="bar"><span :style="{ width: `${stats[c.id].percent}%` }"></span></span>
          <span class="progress-label">{{ stats[c.id].played }}/{{ stats[c.id].total }} matchs</span>
        </span>
        <span class="comp-cta">Voir la compétition →</span>
      </router-link>
    </div>
  </template>
  <p v-if="!competitions.length && loaded" class="muted">Aucune compétition pour l'instant.</p>

  <h2>Ajouter une compétition</h2>
  <form class="inline" @submit.prevent="submit">
    <input v-model="form.code" placeholder="Code (ex: LEC)" aria-label="Code" required />
    <input v-model="form.name" placeholder="Nom (ex: LEC 2026)" aria-label="Nom" required />
    <select v-model="form.type" aria-label="Type de compétition">
      <option value="REGIONAL_LEAGUE">Ligue régionale</option>
      <option value="INTERNATIONAL_EVENT">Événement international</option>
    </select>
    <input v-model="form.region" placeholder="Région (ex: EMEA)" aria-label="Région" />
    <input v-model.number="form.season" type="number" placeholder="Saison" aria-label="Saison" required />
    <button type="submit">Ajouter</button>
  </form>
  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../services/api'
import { leagueColor } from '../leagueColors'

const competitions = ref([])
const loaded = ref(false)
const error = ref('')
const stats = reactive({})

const sections = computed(() => [
  { type: 'REGIONAL_LEAGUE', label: 'Ligues régionales', items: competitions.value.filter(c => c.type === 'REGIONAL_LEAGUE') },
  { type: 'INTERNATIONAL_EVENT', label: 'Événements internationaux', items: competitions.value.filter(c => c.type !== 'REGIONAL_LEAGUE') }
])

function statusOf(matches) {
  const played = matches.filter(m => m.status === 'COMPLETED').length
  if (!matches.length || played === 0) return { label: 'À venir', tone: 'soon' }
  if (played === matches.length) return { label: 'Terminé', tone: 'done' }
  return { label: 'En cours', tone: 'live' }
}

// Avancement de chaque competition (charge en tache de fond, la grille
// s'affiche sans attendre).
async function loadStats() {
  await Promise.all(competitions.value.map(async c => {
    const matches = await api.getMatchesByCompetition(c.id)
    const played = matches.filter(m => m.status === 'COMPLETED').length
    stats[c.id] = {
      played,
      total: matches.length,
      percent: matches.length ? Math.round((played / matches.length) * 100) : 0,
      status: statusOf(matches)
    }
  }))
}

const form = reactive({
  code: '',
  name: '',
  type: 'REGIONAL_LEAGUE',
  region: '',
  season: new Date().getFullYear()
})

async function load() {
  competitions.value = await api.getCompetitions()
  loaded.value = true
  loadStats()
}

async function submit() {
  error.value = ''
  try {
    await api.createCompetition({ ...form })
    form.code = ''
    form.name = ''
    form.region = ''
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de la création."
  }
}

onMounted(load)
</script>

<style scoped>
.page-head h1 {
  margin-bottom: 4px;
}
.subtitle {
  margin: 0 0 8px;
  color: var(--text-muted);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}
.comp-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 18px 16px;
  border-radius: 18px;
  color: var(--text);
  overflow: hidden;
  background:
    radial-gradient(260px 140px at 100% 0%, color-mix(in srgb, var(--league-color) 22%, transparent), transparent 70%),
    var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  transition: transform 0.18s, border-color 0.18s, box-shadow 0.18s;
}
.comp-card::before {
  content: "";
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: var(--league-color);
}
.comp-card:hover {
  color: var(--text);
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--league-color) 55%, var(--border));
  box-shadow: 0 16px 36px -16px color-mix(in srgb, var(--league-color) 60%, transparent);
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.state {
  font-size: 0.72em;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: 999px;
}
.state.live {
  color: var(--accent);
  background: rgba(34, 211, 238, 0.12);
}
.state.done {
  color: var(--win);
  background: rgba(52, 211, 153, 0.12);
}
.state.soon {
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.06);
}
.comp-name {
  font-family: "Outfit", sans-serif;
  font-size: 1.2em;
  font-weight: 800;
  line-height: 1.2;
}
.comp-meta {
  font-size: 0.82em;
  color: var(--text-muted);
}
.comp-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
.bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  overflow: hidden;
}
.bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--league-color);
  transition: width 0.5s ease;
}
.progress-label {
  font-size: 0.74em;
  color: var(--text-dim);
  white-space: nowrap;
}
.comp-cta {
  margin-top: 8px;
  font-size: 0.82em;
  font-weight: 700;
  color: var(--league-color);
}
</style>
