<template>
  <header class="page-head">
    <h1>Compétitions</h1>
    <p class="subtitle">Choisis une saison, puis une ligue pour voir son classement, son bracket et ses résultats.</p>
  </header>

  <nav v-if="seasons.length" class="seasons" aria-label="Saisons">
    <router-link
      v-for="s in seasons"
      :key="s.season"
      :to="{ query: { season: s.season } }"
      class="season-chip"
      :class="{ active: s.season === season }"
    >
      {{ s.season }}<span class="season-count">{{ s.competitionCount }}</span>
    </router-link>
  </nav>

  <template v-for="section in sections" :key="section.key">
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
          <span class="state" :class="statusOf(c).tone">{{ statusOf(c).label }}</span>
        </span>
        <span class="comp-name">{{ c.name }}</span>
        <span class="comp-meta">
          {{ c.region ?? 'International' }}<template v-if="c.split"> · {{ splitLabel(c.split) }}</template>
          <template v-if="c.startDate"> · {{ shortDate(c.startDate) }} → {{ shortDate(c.endDate) }}</template>
        </span>
        <span class="comp-progress">
          <span class="bar"><span :style="{ width: `${percent(c)}%` }"></span></span>
          <span class="progress-label">{{ c.playedCount }}/{{ c.matchCount }} matchs</span>
        </span>
        <span class="comp-cta">Voir la compétition →</span>
      </router-link>
    </div>
  </template>
  <p v-if="loaded && !competitions.length" class="muted">
    {{ season ? `Aucune compétition en ${season}.` : "Aucune compétition pour l'instant." }}
  </p>

  <h2>Ajouter une compétition</h2>
  <form class="inline" @submit.prevent="submit">
    <input v-model="form.code" placeholder="Code (ex: LCS)" aria-label="Code" required />
    <input v-model="form.name" placeholder="Nom (ex: EU LCS 2013 Spring)" aria-label="Nom" required />
    <select v-model="form.type" aria-label="Type de compétition">
      <option value="REGIONAL_LEAGUE">Ligue régionale</option>
      <option value="INTERNATIONAL_EVENT">Événement international</option>
    </select>
    <input v-model="form.region" placeholder="Région (ex: EMEA)" aria-label="Région" />
    <input v-model.number="form.season" class="year" type="number" min="2009" placeholder="Saison" aria-label="Saison" required />
    <select v-model="form.split" aria-label="Split">
      <option :value="null">Sans split (événement)</option>
      <option v-for="s in SPLITS" :key="s.key" :value="s.key">{{ s.label }}</option>
    </select>
    <button type="submit">Créer</button>
  </form>
  <p class="hint">Une fois créée, ajoute ses phases (poules, swiss, bracket…) depuis l'onglet <strong>Format</strong> : les matchs sont générés automatiquement.</p>
  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../services/api'
import { leagueColor } from '../leagueColors'
import { SPLITS, splitLabel } from '../formats'

const route = useRoute()
const router = useRouter()

const seasons = ref([])
const competitions = ref([])
const loaded = ref(false)
const error = ref('')

// Saison choisie dans l'URL (?season=2013), la plus recente par defaut.
const season = computed(() => Number(route.query.season) || seasons.value[0]?.season || null)

const sections = computed(() => {
  const regional = competitions.value.filter(c => c.type === 'REGIONAL_LEAGUE')
  return [
    ...SPLITS.map(s => ({ key: s.key, label: `Split ${s.label}`, items: regional.filter(c => c.split === s.key) })),
    { key: 'regional', label: 'Autres compétitions régionales', items: regional.filter(c => !c.split) },
    { key: 'international', label: 'Événements internationaux', items: competitions.value.filter(c => c.type !== 'REGIONAL_LEAGUE') }
  ]
})

function percent(c) {
  return c.matchCount ? Math.round((c.playedCount / c.matchCount) * 100) : 0
}

function statusOf(c) {
  if (!c.matchCount || c.playedCount === 0) return { label: 'À venir', tone: 'soon' }
  if (c.playedCount === c.matchCount) return { label: 'Terminé', tone: 'done' }
  return { label: 'En cours', tone: 'live' }
}

function shortDate(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }) : '?'
}

const form = reactive({
  code: '',
  name: '',
  type: 'REGIONAL_LEAGUE',
  region: '',
  season: new Date().getFullYear(),
  split: null
})

async function loadSeason() {
  competitions.value = season.value ? await api.getCompetitionsBySeason(season.value) : []
  loaded.value = true
}

async function load() {
  seasons.value = await api.getSeasons()
  await loadSeason()
}

async function submit() {
  error.value = ''
  try {
    const created = await api.createCompetition({ ...form })
    router.push(`/competitions/${created.id}?tab=format`)
  } catch (e) {
    error.value = e.response?.data?.error ?? 'Erreur lors de la création.'
  }
}

watch(() => route.query.season, loadSeason)
onMounted(load)
</script>

<style scoped>
.seasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 6px 0 4px;
}
.season-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 14px;
  border-radius: 999px;
  font-family: "Outfit", sans-serif;
  font-weight: 700;
  color: var(--text-muted);
  background: var(--panel);
  border: 1px solid var(--border);
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.season-chip:hover {
  color: var(--text);
  border-color: var(--border-strong);
}
.season-chip.active {
  color: #0a0c18;
  background: var(--accent-grad);
  border-color: transparent;
  box-shadow: 0 6px 18px -8px rgba(129, 140, 248, 0.9);
}
.season-count {
  font-size: 0.75em;
  font-weight: 600;
  opacity: 0.75;
}
.year {
  width: 100px;
}
.hint {
  font-size: 0.86em;
  color: var(--text-muted);
  margin: -12px 0 20px;
}
.hint strong {
  color: var(--text);
}
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
