<template>
  <p><router-link to="/">← Toutes les compétitions</router-link></p>
  <h1>
    <span v-if="competition" class="league-badge" :style="{ '--league-color': leagueColor(competition.code) }">{{ competition.code }}</span>
    {{ competition?.name ?? '...' }}
  </h1>

  <div class="tabs">
    <button type="button" :class="{ active: activeTab === 'standings' }" @click="activeTab = 'standings'">Classement</button>
    <button type="button" :class="{ active: activeTab === 'bracket' }" @click="activeTab = 'bracket'">Bracket</button>
    <button v-if="regionalFinalsMatches.length" type="button" :class="{ active: activeTab === 'regionalFinals' }" @click="activeTab = 'regionalFinals'">Regional Finals</button>
    <button type="button" :class="{ active: activeTab === 'h2h' }" @click="activeTab = 'h2h'">Confrontations</button>
    <button type="button" :class="{ active: activeTab === 'calendar' }" @click="activeTab = 'calendar'">Calendrier</button>
  </div>

  <section v-if="activeTab === 'standings'">
    <h2>Classement</h2>
    <div class="group-panels">
      <div v-for="p in standingsPanels" :key="p.key" class="group-panel">
        <h3 v-if="p.name">{{ p.name }}</h3>
        <StandingsTable :rows="p.rows" />
      </div>
    </div>
  </section>

  <section v-if="activeTab === 'h2h'">
    <h2>Confrontations directes</h2>
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

  <section v-if="activeTab === 'bracket'">
    <h2>Playoffs</h2>
    <PlayoffBracket :matches="playoffMatches" :teams="teams" />
  </section>

  <section v-if="activeTab === 'regionalFinals'">
    <h2>Regional Finals</h2>
    <PlayoffBracket :matches="regionalFinalsMatches" :teams="teams" />
  </section>

  <section v-if="activeTab === 'calendar'">
    <h2>Calendrier</h2>
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

  <p v-if="error" style="color:#ff6b6b">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '../services/api'
import StandingsTable from '../components/StandingsTable.vue'
import HeadToHeadTable from '../components/HeadToHeadTable.vue'
import PlayoffBracket from '../components/PlayoffBracket.vue'
import { leagueColor } from '../leagueColors'
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
    .map(g => ({ key: g.id ?? 'all', name: g.name, rows: g.standings }))
  if (swissStandings.value.length) {
    panels.push({ key: 'swiss', name: 'Phase suisse', rows: swissStandings.value })
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
    return { ...g, standings: standingRows, legs: buildLegCells(groupMatches) }
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
.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.tabs button {
  background: none;
  border: none;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  color: var(--text-muted);
  padding: 10px 18px;
}
.tabs button:hover {
  color: var(--gold-bright);
  background: rgba(200, 170, 110, 0.06);
}
.tabs button.active {
  color: var(--gold-bright);
  border-bottom-color: var(--gold);
}
.league-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.5em;
  letter-spacing: 0.03em;
  vertical-align: middle;
  margin-right: 10px;
  color: var(--league-color);
  background: color-mix(in srgb, var(--league-color) 18%, transparent);
  border: 1px solid color-mix(in srgb, var(--league-color) 45%, transparent);
  -webkit-text-fill-color: var(--league-color);
}
.playoff-options {
  margin-bottom: 24px;
}
.playoff-options summary {
  cursor: pointer;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.group-panels {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}
.group-panel {
  flex: 1 1 420px;
  min-width: 0;
}
.group-panel h3 {
  color: var(--gold);
  font-size: 0.95em;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 10px;
}
.group-block {
  margin-bottom: 32px;
}
.group-block > h3 {
  color: var(--gold);
  font-size: 1.05em;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
}
.group-panel h4 {
  color: var(--text-muted);
  font-size: 0.82em;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}
</style>
