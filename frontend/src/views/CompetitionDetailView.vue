<template>
  <router-link to="/" class="back">← Toutes les compétitions</router-link>

  <header class="hero" :style="{ '--league-color': leagueColor(competition?.code) }">
    <div class="hero-main">
      <span v-if="competition" class="league-badge">{{ competition.code }}</span>
      <h1>{{ competition?.name ?? '...' }}</h1>
      <p v-if="competition" class="hero-sub">
        {{ competition.type === 'REGIONAL_LEAGUE' ? 'Ligue régionale' : 'Événement international' }}
        <template v-if="competition.region"> · {{ competition.region }}</template>
        · Saison {{ competition.season }}
      </p>
    </div>
    <div v-if="loaded" class="hero-stats">
      <div class="stat">
        <span class="stat-value">{{ playedCount }}<span class="stat-total">/{{ matches.length }}</span></span>
        <span class="stat-label">matchs joués</span>
        <span class="progress"><span :style="{ width: `${progress}%` }"></span></span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ teamCount }}</span>
        <span class="stat-label">équipes</span>
      </div>
      <div v-if="nextMatch" class="stat">
        <span class="stat-value small">{{ longDay(nextMatch.date) }}</span>
        <span class="stat-label">prochain : {{ nextMatch.team1Code ?? '?' }} vs {{ nextMatch.team2Code ?? '?' }}</span>
      </div>
      <div v-else-if="champion" class="stat champion-stat">
        <span class="stat-value small">🏆 {{ champion }}</span>
        <span class="stat-label">champion</span>
      </div>
    </div>
  </header>

  <div class="tabs" role="tablist">
    <button
      v-for="t in tabs"
      :key="t.key"
      type="button"
      role="tab"
      :aria-selected="activeTab === t.key"
      :class="{ active: activeTab === t.key }"
      @click="activeTab = t.key"
    >
      <span class="tab-icon">{{ t.icon }}</span>{{ t.label }}
    </button>
  </div>

  <section v-if="activeTab === 'standings'" class="tab-panel">
    <div class="group-panels">
      <div v-for="p in standingsPanels" :key="p.key" class="group-panel">
        <h3 v-if="p.name">{{ p.name }}</h3>
        <StandingsTable :rows="p.rows" :matches="p.matches" />
      </div>
    </div>
  </section>

  <section v-if="activeTab === 'h2h'" class="tab-panel">
    <p class="hint">Chaque case donne le score de la série <strong>du point de vue de l'équipe en ligne</strong> : vert = victoire, rouge = défaite.</p>
    <div v-for="g in groupedData" :key="g.id ?? 'all'" class="group-block">
      <h3 v-if="g.name">{{ g.name }}</h3>
      <div class="group-panels">
        <div v-for="(legCells, legIndex) in g.legs" :key="legIndex" class="group-panel">
          <h4 v-if="g.legs.length > 1">{{ legLabel(legIndex) }}</h4>
          <HeadToHeadTable :rows="g.standings" :cells="legCells" />
        </div>
      </div>
    </div>
  </section>

  <section v-if="activeTab === 'bracket'" class="tab-panel">
    <PlayoffBracket :matches="playoffMatches" :teams="teams" />
  </section>

  <section v-if="activeTab === 'regionalFinals'" class="tab-panel">
    <PlayoffBracket :matches="regionalFinalsMatches" :teams="teams" />
  </section>

  <section v-if="activeTab === 'calendar'" class="tab-panel">
    <div class="table-scroll">
    <table v-if="sortedMatches.length">
      <thead>
        <tr>
          <th class="sortable" tabindex="0" @click="toggleSort('date')" @keydown.enter="toggleSort('date')">Date <span class="sort-arrow">{{ sortArrow('date') }}</span></th>
          <th>Heure</th>
          <th>Phase</th>
          <th>Round</th>
          <th>BO</th>
          <th>Équipe 1</th>
          <th></th>
          <th></th>
          <th>Équipe 2</th>
          <th>Statut</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in sortedMatches" :key="m.id" :class="{ 'row-scheduled': m.status === 'SCHEDULED' }">
          <MatchEditCells v-model="edits[m.id]" :teams="teams" />
          <td>
            <span class="status-badge" :class="m.status === 'COMPLETED' ? 'completed' : 'scheduled'">
              {{ m.status === 'COMPLETED' ? 'Joué' : 'À venir' }}
            </span>
          </td>
          <td>
            <button :disabled="!canSave(edits[m.id])" @click="saveMatch(m)">Enregistrer</button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    <p v-if="!sortedMatches.length && loaded">Aucun match pour cette compétition.</p>

    <h2>Ajouter un match</h2>
    <form class="inline" @submit.prevent="submitMatch">
      <select v-model.number="newMatch.team1Id" aria-label="Équipe 1">
        <option :value="null">Équipe 1 (à déterminer)</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
      </select>
      <select v-model.number="newMatch.team2Id" aria-label="Équipe 2">
        <option :value="null">Équipe 2 (à déterminer)</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
      </select>
      <input v-model="newMatch.roundLabel" placeholder="Round (ex: W1, QF, F)" aria-label="Round" required />
      <input v-model="newMatch.date" type="date" aria-label="Date" required />
      <input v-model="newMatch.time" type="time" aria-label="Heure" />
      <select v-model="newMatch.bestOf" aria-label="Format (best of)">
        <option value="BO1">BO1</option>
        <option value="BO3">BO3</option>
        <option value="BO5">BO5</option>
      </select>
      <input v-model.number="newMatch.score1" class="score-input" type="number" min="0" placeholder="S1" aria-label="Score équipe 1" />
      <input v-model.number="newMatch.score2" class="score-input" type="number" min="0" placeholder="S2" aria-label="Score équipe 2" />
      <button type="submit">Ajouter</button>
    </form>

    <details class="playoff-options">
      <summary>Options playoffs / bracket (facultatif)</summary>
      <div class="inline">
        <select v-model="newMatch.phase" aria-label="Phase">
          <option value="REGULAR_SEASON">Saison régulière</option>
          <option value="PLAYOFFS">Playoffs</option>
        </select>
        <select v-model="newMatch.bracketSide" aria-label="Côté du bracket">
          <option :value="null">Côté bracket (aucun)</option>
          <option value="GROUP">Poules</option>
          <option value="PLAY_IN">Play-in</option>
          <option value="UPPER">Bracket vainqueurs</option>
          <option value="LOWER">Bracket perdants</option>
        </select>
        <select v-model.number="newMatch.nextMatchId" aria-label="Match suivant du vainqueur">
          <option :value="null">Match suivant (vainqueur) : aucun</option>
          <option v-for="m in playoffMatches" :key="m.id" :value="m.id">
            #{{ m.id }} — {{ m.roundLabel }} ({{ m.team1Code ?? '?' }} vs {{ m.team2Code ?? '?' }})
          </option>
        </select>
        <select v-model.number="newMatch.nextMatchSlot" aria-label="Slot du vainqueur">
          <option :value="null">Slot</option>
          <option :value="1">Équipe 1</option>
          <option :value="2">Équipe 2</option>
        </select>
        <select v-model.number="newMatch.loserNextMatchId" aria-label="Match suivant du perdant">
          <option :value="null">Match suivant (perdant) : aucun</option>
          <option v-for="m in playoffMatches" :key="m.id" :value="m.id">
            #{{ m.id }} — {{ m.roundLabel }} ({{ m.team1Code ?? '?' }} vs {{ m.team2Code ?? '?' }})
          </option>
        </select>
        <select v-model.number="newMatch.loserNextMatchSlot" aria-label="Slot du perdant">
          <option :value="null">Slot</option>
          <option :value="1">Équipe 1</option>
          <option :value="2">Équipe 2</option>
        </select>
      </div>
    </details>
  </section>

  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '../services/api'
import StandingsTable from '../components/StandingsTable.vue'
import HeadToHeadTable from '../components/HeadToHeadTable.vue'
import PlayoffBracket from '../components/PlayoffBracket.vue'
import { leagueColor } from '../leagueColors'
import { longDay } from '../format'
import { canSave, kickoff, toEditForm, toMatchPayload } from '../matchEdit'
import { useSort } from '../composables/useSort'
import MatchEditCells from '../components/MatchEditCells.vue'

const props = defineProps({
  id: { type: [String, Number], required: true }
})

const competition = ref(null)
const matches = ref([])
const groupedData = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const edits = reactive({})
const activeTab = ref('standings')

const playoffMatches = computed(() => matches.value.filter(m => m.phase === 'PLAYOFFS' && !(m.bracketSide ?? '').startsWith('REGIONAL_')))
const regionalFinalsMatches = computed(() => matches.value.filter(m => (m.bracketSide ?? '').startsWith('REGIONAL_')))

const tabs = computed(() => [
  { key: 'standings', label: 'Classement', icon: '📊' },
  { key: 'bracket', label: 'Bracket', icon: '🏆' },
  ...(regionalFinalsMatches.value.length ? [{ key: 'regionalFinals', label: 'Regional Finals', icon: '🌏' }] : []),
  { key: 'h2h', label: 'Confrontations', icon: '⚔️' },
  { key: 'calendar', label: 'Calendrier', icon: '📅' }
])

const playedCount = computed(() => matches.value.filter(m => m.status === 'COMPLETED').length)
const progress = computed(() => (matches.value.length ? Math.round((playedCount.value / matches.value.length) * 100) : 0))
const teamCount = computed(() => new Set(matches.value.flatMap(m => [m.team1Id, m.team2Id]).filter(id => id != null)).size)
const nextMatch = computed(() =>
  matches.value.filter(m => m.status !== 'COMPLETED').sort((a, b) => kickoff(a).localeCompare(kickoff(b)))[0] ?? null
)
const champion = computed(() => {
  const final = matches.value.find(m => m.bracketSide === 'GRAND_FINAL' && m.status === 'COMPLETED')
  if (!final) return null
  return final.score1 > final.score2 ? final.team1Code : final.team2Code
})

const { toggleSort, sortArrow, sortList } = useSort('date', kickoff)

const sortedMatches = computed(() => sortList(matches.value))

function emptyNewMatch() {
  return {
    team1Id: null,
    team2Id: null,
    roundLabel: '',
    date: '',
    time: '',
    bestOf: 'BO3',
    score1: null,
    score2: null,
    phase: 'REGULAR_SEASON',
    bracketSide: null,
    nextMatchId: null,
    nextMatchSlot: null,
    loserNextMatchId: null,
    loserNextMatchSlot: null
  }
}

const newMatch = reactive(emptyNewMatch())

/**
 * Regroupe les matchs termines par paire d'equipes (dans l'ordre chronologique)
 * et construit, pour chaque manche (1ere confrontation, 2eme confrontation...),
 * la liste de cellules { teamAId, teamBId, score } ou "score" est le score
 * exact de CETTE manche du point de vue de teamA (ex: "2-1"), pas un bilan
 * agrege : demande explicite d'afficher les series comme au foot (aller/retour).
 */
function buildLegCells(groupMatches) {
  const pairs = new Map()
  for (const m of groupMatches) {
    if (m.status !== 'COMPLETED' || m.team1Id == null || m.team2Id == null) continue
    const key = [m.team1Id, m.team2Id].sort((a, b) => a - b).join(':')
    if (!pairs.has(key)) pairs.set(key, [])
    pairs.get(key).push(m)
  }
  for (const arr of pairs.values()) {
    arr.sort((a, b) => (a.date + (a.time ?? '')).localeCompare(b.date + (b.time ?? '')))
  }
  const maxLegs = Math.max(0, ...[...pairs.values()].map(arr => arr.length))
  const legs = []
  for (let i = 0; i < maxLegs; i++) {
    const cells = []
    for (const arr of pairs.values()) {
      const m = arr[i]
      if (!m) continue
      cells.push(
        { teamAId: m.team1Id, teamBId: m.team2Id, score: `${m.score1}-${m.score2}` },
        { teamAId: m.team2Id, teamBId: m.team1Id, score: `${m.score2}-${m.score1}` },
      )
    }
    legs.push(cells)
  }
  return legs
}

function legLabel(index) {
  if (index === 0) return 'Match aller'
  if (index === 1) return 'Match retour'
  return `Manche ${index + 1}`
}

/**
 * Classement calcule cote client pour les competitions sans vraie saison
 * reguliere (LCP : phase suisse modelisee en phase=PLAYOFFS, voir V5) : a
 * defaut, /api/standings renvoie une liste vide. On retallie a partir des
 * matchs bracketSide='SWISS_STAGE' pour donner un classement quand meme.
 */
function computeSwissStandings(swissMatches, teamList) {
  const teamById = new Map(teamList.map(t => [t.id, t]))
  const tally = new Map()
  function ensure(id) {
    if (!tally.has(id)) {
      const t = teamById.get(id)
      tally.set(id, {
        teamId: id,
        teamCode: t?.code ?? '?',
        teamName: t?.name ?? '?',
        teamHasLogo: t?.hasLogo ?? false,
        seriesWon: 0,
        seriesLost: 0,
        gamesWon: 0,
        gamesLost: 0
      })
    }
    return tally.get(id)
  }
  for (const m of swissMatches) {
    if (m.status !== 'COMPLETED' || m.team1Id == null || m.team2Id == null) continue
    const a = ensure(m.team1Id)
    const b = ensure(m.team2Id)
    a.gamesWon += m.score1
    a.gamesLost += m.score2
    b.gamesWon += m.score2
    b.gamesLost += m.score1
    if (m.score1 > m.score2) {
      a.seriesWon++
      b.seriesLost++
    } else {
      b.seriesWon++
      a.seriesLost++
    }
  }
  return [...tally.values()].sort((x, y) =>
    y.seriesWon - x.seriesWon || x.seriesLost - y.seriesLost || y.gamesWon - x.gamesWon
  )
}

const swissStandings = ref([])

const standingsPanels = computed(() => {
  const panels = groupedData.value
    .filter(g => !(g.id === null && g.standings.length === 0 && swissStandings.value.length > 0))
    .map(g => ({ key: g.id ?? 'all', name: g.name, rows: g.standings, matches: g.matches }))
  if (swissStandings.value.length) {
    panels.push({ key: 'swiss', name: 'Phase suisse', rows: swissStandings.value, matches: matches.value.filter(m => m.bracketSide === 'SWISS_STAGE') })
  }
  return panels
})

async function load() {
  const competitionId = Number(props.id)
  const [competitions, matchList, teamList] = await Promise.all([
    api.getCompetitions(),
    api.getMatchesByCompetition(competitionId),
    api.getTeams()
  ])
  competition.value = competitions.find(c => c.id === competitionId) ?? null
  matches.value = matchList
  teams.value = teamList

  const groups = [...new Map(
    matchList.filter(m => m.groupId != null).map(m => [m.groupId, m.groupName])
  ).entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.id - b.id)

  const targets = groups.length ? groups : [{ id: null, name: null }]
  groupedData.value = await Promise.all(targets.map(async g => {
    const standingRows = await api.getStandings(competitionId, g.id ?? undefined)
    const groupMatches = matchList.filter(m =>
      m.phase === 'REGULAR_SEASON' && (g.id == null || m.groupId === g.id)
    )
    return { ...g, standings: standingRows, matches: groupMatches, legs: buildLegCells(groupMatches) }
  }))

  swissStandings.value = computeSwissStandings(
    matchList.filter(m => m.bracketSide === 'SWISS_STAGE'),
    teamList
  )

  for (const m of matchList) {
    edits[m.id] = toEditForm(m)
  }
  loaded.value = true
}

async function saveMatch(match) {
  error.value = ''
  try {
    await api.updateMatch(match.id, toMatchPayload(match, edits[match.id]))
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de l'enregistrement du match."
  }
}

async function submitMatch() {
  error.value = ''
  try {
    await api.createMatch(toMatchPayload({ ...newMatch, competitionId: Number(props.id), groupId: null }, newMatch))
    Object.assign(newMatch, emptyNewMatch())
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de la création du match."
  }
}

// Competition sans classement (ex: EMEA Masters LCQ, uniquement des groupes
// GSL) : on ouvre directement sur le bracket plutot que sur un onglet vide.
async function initialLoad() {
  activeTab.value = 'standings'
  await load()
  if (standingsPanels.value.every(p => p.rows.length === 0) && playoffMatches.value.length) {
    activeTab.value = 'bracket'
  }
}

watch(() => props.id, initialLoad)
onMounted(initialLoad)
</script>

<style scoped>
.back {
  display: inline-block;
  margin-top: 22px;
  font-size: 0.88em;
  color: var(--text-muted);
}
.back:hover {
  color: var(--accent);
}
.hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 14px 0 22px;
  padding: 26px 28px;
  border-radius: 20px;
  overflow: hidden;
  background:
    radial-gradient(500px 220px at 0% 0%, color-mix(in srgb, var(--league-color) 28%, transparent), transparent 70%),
    linear-gradient(135deg, var(--panel), var(--bg-elevated));
  border: 1px solid color-mix(in srgb, var(--league-color) 30%, var(--border));
  box-shadow: var(--shadow);
}
.hero::after {
  content: "";
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  background: linear-gradient(90deg, var(--league-color), transparent);
}
.hero h1 {
  margin: 10px 0 6px;
}
.hero-sub {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92em;
}
.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 130px;
  padding: 12px 16px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
}
.stat-value {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.5em;
  line-height: 1.1;
}
.stat-value.small {
  font-size: 1.1em;
  padding: 4px 0 3px;
}
.stat-total {
  font-size: 0.6em;
  color: var(--text-dim);
}
.stat-label {
  font-size: 0.74em;
  color: var(--text-muted);
}
.champion-stat {
  border-color: rgba(245, 196, 81, 0.45);
  background: rgba(245, 196, 81, 0.08);
}
.champion-stat .stat-value {
  color: var(--gold-bright);
}
.progress {
  display: block;
  height: 5px;
  margin-top: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  overflow: hidden;
}
.progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--league-color);
}
.tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 5px;
  margin-bottom: 22px;
  border-radius: 999px;
  background: var(--panel-alt);
  border: 1px solid var(--border);
}
.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: none;
  color: var(--text-muted);
  font-weight: 600;
  font-size: 0.9em;
  padding: 8px 16px;
  box-shadow: none;
}
.tabs button:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.05);
  filter: none;
}
.tabs button.active {
  color: #0a0c18;
  background: var(--accent-grad);
  box-shadow: 0 6px 18px -8px rgba(129, 140, 248, 0.9);
}
.tab-panel {
  animation: fade-in 0.25s ease;
}
@keyframes fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: none; }
}
.hint {
  font-size: 0.86em;
  color: var(--text-muted);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.2);
  margin: 0 0 20px;
}
.hint strong {
  color: var(--text);
}
.playoff-options {
  margin-bottom: 24px;
}
.playoff-options summary {
  cursor: pointer;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.playoff-options .inline {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.group-panels {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.group-panel {
  flex: 1 1 460px;
  min-width: 0;
}
.group-panel h3,
.group-block > h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.05em;
  font-weight: 700;
  margin: 0 0 12px;
}
.group-panel h3::before,
.group-block > h3::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-2);
  box-shadow: 0 0 10px var(--accent-2);
}
.group-block {
  margin-bottom: 32px;
}
.group-panel h4 {
  color: var(--text-muted);
  font-size: 0.8em;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0 0 8px;
}
@media (max-width: 640px) {
  .hero {
    padding: 20px;
  }
  .tabs {
    display: flex;
    border-radius: var(--radius);
  }
}
</style>
