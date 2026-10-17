<template>
  <h1>⚡ Power Ranking</h1>
  <p class="subtitle">
    Classement Elo des équipes sur la saison, toutes compétitions confondues. Il se recalcule dès qu'un résultat est
    enregistré.
  </p>

  <nav v-if="seasons.length" class="seasons" aria-label="Saisons">
    <router-link
      v-for="s in seasons"
      :key="s.season"
      :to="{ query: { ...route.query, season: s.season } }"
      class="season-chip"
      :class="{ active: s.season === season }"
    >
      {{ s.season }}
    </router-link>
  </nav>

  <div v-if="rows.length" class="region-filter" role="group" aria-label="Filtrer par région">
    <button type="button" :class="{ active: region === null }" @click="region = null">Toutes les régions</button>
    <button
      v-for="r in regions"
      :key="r.key"
      type="button"
      :class="{ active: region === r.key }"
      :style="{ '--region-color': r.color }"
      @click="region = region === r.key ? null : r.key"
    >
      {{ r.label }} <span class="region-count">{{ r.count }}</span>
    </button>
  </div>

  <div v-if="rows.length" class="filters">
    <input v-model="search" type="search" placeholder="Rechercher une équipe" aria-label="Rechercher une équipe" />
    <span class="updated muted small" :class="{ flash: justUpdated }">
      Mis à jour à {{ updatedAt }}
    </span>
  </div>

  <div v-if="filteredRows.length" class="table-scroll">
    <table class="ranking">
      <thead>
        <tr>
          <th class="num">#</th>
          <th class="num" title="Places gagnées ou perdues sur les 7 derniers jours de compétition">7 j</th>
          <th>Équipe</th>
          <th>Région</th>
          <th class="num">Note</th>
          <th class="num">Séries</th>
          <th class="num">Manches</th>
          <th>Forme</th>
          <th>Meilleure victoire</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in limitedRows"
          :key="r.teamId"
          :class="{ changed: changedTeams.has(r.teamId) }"
          :style="{ '--region-color': regionOf(r.region)?.color ?? 'transparent' }"
        >
          <td class="num rank" :class="{ top: r.rank <= 3 }">{{ r.rank }}</td>
          <td class="num">
            <span v-if="r.rankChange == null" class="trend new" title="Nouvelle équipe classée">●</span>
            <span v-else-if="r.rankChange > 0" class="trend up">▲ {{ r.rankChange }}</span>
            <span v-else-if="r.rankChange < 0" class="trend down">▼ {{ -r.rankChange }}</span>
            <span v-else class="trend muted">=</span>
          </td>
          <td>
            <router-link :to="`/teams/${r.teamId}`" class="team-link" :title="r.teamName">
              <TeamTag :id="r.teamId" :code="r.teamCode" :name="r.teamName" :has-logo="r.teamHasLogo" />
              <span class="team-name">{{ r.teamName }}</span>
            </router-link>
          </td>
          <td>
            <span class="region-cell">
              <RegionBadge :region="r.region" />
              <span
                class="league muted small"
                :title="r.league ? `Ligue ${r.league} : note de départ ${r.startRating}` : `Sans ligue cette saison : note de départ ${r.startRating}`"
              >{{ r.league ?? '2e div.' }} · {{ r.startRating }}</span>
            </span>
          </td>
          <td class="num">
            <strong class="rating">{{ r.rating }}</strong>
            <span v-if="r.ratingChange" class="small" :class="r.ratingChange > 0 ? 'win' : 'loss'">
              {{ r.ratingChange > 0 ? '+' : '' }}{{ r.ratingChange }}
            </span>
          </td>
          <td class="num"><span class="win">{{ r.seriesWon }}</span>-<span class="loss">{{ r.seriesLost }}</span></td>
          <td class="num muted">{{ r.gamesWon }}-{{ r.gamesLost }}</td>
          <td>
            <span class="form" :aria-label="`Forme : ${r.form.join(' ')}`">
              <span v-for="(f, i) in r.form" :key="i" class="form-dot" :class="f">{{ f === 'W' ? 'V' : f === 'L' ? 'D' : 'N' }}</span>
            </span>
          </td>
          <td>
            <router-link v-if="r.bestWin" :to="`/matches/${r.bestWin.matchId}`" class="best-win">
              {{ r.bestWin.score }} vs <strong>{{ r.bestWin.opponentCode }}</strong>{{ ' ' }}<span class="muted small">({{ r.bestWin.opponentRating }})</span>
            </router-link>
            <span v-else class="muted">—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <button v-if="filteredRows.length > COLLAPSED_ROWS" type="button" class="btn-secondary show-all" @click="expanded = !expanded">
    {{ expanded ? 'Réduire' : `Tout afficher (${filteredRows.length})` }}
  </button>
  <p v-if="loaded && !rows.length" class="muted">Aucun match joué en {{ season }}.</p>
  <p v-else-if="loaded && !filteredRows.length" class="muted">Aucune équipe ne correspond à ces filtres.</p>

  <details class="method">
    <summary>Comment est calculé ce classement ?</summary>
    <ul>
      <li>
        <strong>Coefficient de ligue</strong> : chaque équipe démarre la saison avec la note de sa ligue (celle où
        elle a joué le plus de séries). Dans une ligue, les équipes s'échangent des points entre elles : le niveau
        moyen de la ligue reste donc sa note de départ, que seuls les résultats internationaux corrigent.
        <table class="coefficients">
          <tbody>
            <tr v-for="t in LEAGUE_TIERS" :key="t.rating">
              <td class="num"><strong>{{ t.rating }}</strong></td>
              <td>{{ t.leagues }}</td>
            </tr>
          </tbody>
        </table>
      </li>
      <li>
        <strong>Elo</strong> : après chaque série, le vainqueur
        prend des points au perdant, d'autant plus que la victoire était <strong>inattendue</strong> : battre une
        équipe bien notée rapporte beaucoup, battre une équipe faible presque rien (qualité des adversaires).
      </li>
      <li>
        <strong>Poids de la série</strong> : un BO3 compte ×1,25 et un BO5 ×1,5 par rapport à un BO1 ; une victoire
        nette compte plus (un 3-0 ×1,5, un 2-0 ou 3-1 ×1,25) ; les Worlds, le MSI et le First Stand comptent ×1,5,
        car ce sont les seuls matchs qui comparent les grandes régions entre elles.
      </li>
      <li>
        <strong>Forme récente</strong> : les séries sont prises dans l'ordre chronologique, la note reflète donc
        d'abord les derniers résultats. La colonne « 7 j » montre l'évolution depuis le classement d'il y a 7 jours
        (par rapport au dernier match joué de la saison).
      </li>
      <li>
        Limite : une région qui joue peu à l'international reste mal comparée aux autres, sa note dépend surtout
        de ses matchs internes.
      </li>
    </ul>
  </details>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '../services/api'
import { onMatchChanged } from '../matchEvents'
import TeamTag from '../components/TeamTag.vue'
import RegionBadge from '../components/RegionBadge.vue'
import { regionOf } from '../regions'

const COLLAPSED_ROWS = 25

// Coefficients de ligue (notes de depart), a garder alignes sur PowerRankingService.LEAGUE_RATINGS
const LEAGUE_TIERS = [
  { rating: 1600, leagues: 'LCK (Corée), LPL (Chine)' },
  { rating: 1500, leagues: 'LEC (Europe)' },
  { rating: 1450, leagues: 'LCS / LTA Nord (Amérique du Nord), LCP (Asie-Pacifique)' },
  { rating: 1400, leagues: 'CBLOL / LTA Sud (Brésil), VCS (Vietnam), autres ligues' },
  { rating: 1350, leagues: 'LJL (Japon), TCL (Turquie), LCL (CEI), LCO (Océanie), LLA (Amérique latine), ligues SEA' },
  { rating: 1300, leagues: 'Équipes sans ligue sur la saison (deuxième division : EMEA Masters…)' }
]
// Filet de securite pour les resultats saisis depuis un autre appareil
const REFRESH_MS = 30000

const route = useRoute()
const seasons = ref([])
const rows = ref([])
const loaded = ref(false)
const region = ref(null)
const search = ref('')
const expanded = ref(false)
const updatedAt = ref('')
const justUpdated = ref(false)
// Equipes dont le rang vient de changer, mises en evidence un instant
const changedTeams = ref(new Set())

const season = computed(() => Number(route.query.season) || seasons.value[0]?.season || null)

// Regions presentes dans la saison (variantes regroupees : "LEC", "EMEA" -> Europe), les plus representees d'abord
const regions = computed(() => {
  const byKey = new Map()
  for (const r of rows.value) {
    const info = regionOf(r.region)
    if (!info) continue
    const entry = byKey.get(info.key) ?? { ...info, count: 0 }
    entry.count++
    byKey.set(info.key, entry)
  }
  return [...byKey.values()].sort((a, b) => b.count - a.count || a.label.localeCompare(b.label))
})

function normalize(text) {
  return (text ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

const filteredRows = computed(() => {
  const q = normalize(search.value.trim())
  return rows.value.filter(r =>
    (!region.value || regionOf(r.region)?.key === region.value) &&
    (!q || [r.teamName, r.teamCode, regionOf(r.region)?.label].some(v => normalize(v).includes(q)))
  )
})
const limitedRows = computed(() => (expanded.value ? filteredRows.value : filteredRows.value.slice(0, COLLAPSED_ROWS)))

let loading = null
let flashTimer = null

async function load() {
  if (!season.value) return
  // Une seule requete a la fois ; la plus recente demande gagne
  const requested = season.value
  const request = api.getPowerRanking(requested)
  loading = request
  const data = await request
  if (loading !== request || requested !== season.value) return
  const before = new Map(rows.value.map(r => [r.teamId, r.rank]))
  const sameSeason = rows.value.length && loaded.value
  rows.value = data
  loaded.value = true
  updatedAt.value = new Date().toLocaleTimeString('fr-FR')
  if (sameSeason) highlightChanges(before, data)
}

function highlightChanges(before, data) {
  const changed = new Set(data.filter(r => before.has(r.teamId) && before.get(r.teamId) !== r.rank).map(r => r.teamId))
  if (!changed.size) return
  changedTeams.value = changed
  justUpdated.value = true
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => {
    changedTeams.value = new Set()
    justUpdated.value = false
  }, 2500)
}

function onVisible() {
  if (document.visibilityState === 'visible') load()
}

let unsubscribe = null
let timer = null

onMounted(async () => {
  seasons.value = await api.getSeasons()
  await load()
  unsubscribe = onMatchChanged(load)
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, REFRESH_MS)
  document.addEventListener('visibilitychange', onVisible)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  clearInterval(timer)
  clearTimeout(flashTimer)
  document.removeEventListener('visibilitychange', onVisible)
})

watch(season, () => {
  loaded.value = false
  rows.value = []
  region.value = null
  expanded.value = false
  load()
})
</script>

<style scoped>
.subtitle {
  max-width: 720px;
}
.seasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 18px;
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
.region-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.region-filter button {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--region-color, var(--text-muted)) 40%, transparent);
  background: transparent;
  color: var(--region-color, var(--text-muted));
  font-weight: 700;
  font-size: 0.82em;
  cursor: pointer;
}
.region-filter button.active {
  color: var(--text);
  border-color: var(--region-color, var(--accent));
  background: color-mix(in srgb, var(--region-color, var(--accent)) 22%, transparent);
}
.region-count {
  opacity: 0.6;
  font-weight: 600;
}
/* Liseré de la couleur de la region a gauche de chaque ligne */
.ranking tbody td:first-child {
  box-shadow: inset 3px 0 0 var(--region-color);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.filters input[type="search"] {
  width: min(280px, 100%);
}
.updated {
  margin-left: auto;
  transition: color 0.3s;
}
.updated.flash {
  color: var(--accent);
}
.table-scroll {
  overflow-x: auto;
}
.ranking {
  width: 100%;
}
.num {
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.rank {
  font-weight: 800;
  color: var(--text-muted);
}
.rank.top {
  color: var(--gold, #f5c451);
}
.trend {
  font-size: 0.8em;
  font-weight: 700;
}
.trend.up {
  color: var(--win);
}
.trend.down {
  color: var(--loss);
}
.trend.new {
  color: var(--accent);
}
.team-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  color: var(--text);
}
.team-name {
  font-size: 0.85em;
  color: var(--text-muted);
}
.rating {
  font-size: 1.05em;
  margin-right: 4px;
}
.small {
  font-size: 0.8em;
}
.win {
  color: var(--win);
}
.loss {
  color: var(--loss);
}
.form {
  display: inline-flex;
  gap: 3px;
}
.form-dot {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  font-size: 0.68em;
  font-weight: 800;
  color: #0a0c18;
}
.form-dot.W {
  background: var(--win);
}
.form-dot.L {
  background: var(--loss);
}
.form-dot.D {
  background: var(--text-dim);
}
.best-win {
  white-space: nowrap;
  color: var(--text);
}
.best-win:hover {
  color: var(--accent);
}
tr.changed td {
  animation: flash 2.5s ease-out;
}
@keyframes flash {
  from {
    background: color-mix(in srgb, var(--accent) 22%, transparent);
  }
  to {
    background: transparent;
  }
}
.show-all {
  margin-top: 10px;
}
.method {
  margin-top: 28px;
  color: var(--text-muted);
  font-size: 0.9em;
  max-width: 820px;
}
.method summary {
  cursor: pointer;
  font-weight: 700;
  color: var(--text);
}
.region-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.coefficients {
  margin: 8px 0 4px;
  width: auto;
}
.coefficients td {
  padding: 3px 10px;
}
.method li {
  margin: 6px 0;
}
@media (max-width: 640px) {
  .team-name {
    display: none;
  }
  .updated {
    margin-left: 0;
  }
}
</style>
