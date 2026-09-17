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
          <th class="sortable" @click="toggleDateSort">Date <span class="sort-arrow">{{ dateSortDir === 'asc' ? '▲' : '▼' }}</span></th>
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
          <td><input class="date-input" type="date" v-model="edits[m.id].date" /></td>
          <td><input class="time-input" type="time" v-model="edits[m.id].time" /></td>
          <td>
            <select v-model="edits[m.id].phase">
              <option value="REGULAR_SEASON">Saison rég.</option>
              <option value="PLAYOFFS">Playoffs</option>
            </select>
          </td>
          <td><input class="round-input" v-model="edits[m.id].roundLabel" /></td>
          <td>
            <select v-model="edits[m.id].bestOf">
              <option value="BO1">BO1</option>
              <option value="BO3">BO3</option>
              <option value="BO5">BO5</option>
            </select>
          </td>
          <td class="team-cell">
            <img v-if="hasLogo(edits[m.id].team1Id)" class="team-logo" :src="teamLogoUrl(edits[m.id].team1Id)" alt="" />
            <select v-model.number="edits[m.id].team1Id">
              <option :value="null">À déterminer</option>
              <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
            </select>
          </td>
          <td>
            <input class="score-input" type="number" min="0" v-model.number="edits[m.id].score1" :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id" />
          </td>
          <td>
            <input class="score-input" type="number" min="0" v-model.number="edits[m.id].score2" :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id" />
          </td>
          <td class="team-cell">
            <img v-if="hasLogo(edits[m.id].team2Id)" class="team-logo" :src="teamLogoUrl(edits[m.id].team2Id)" alt="" />
            <select v-model.number="edits[m.id].team2Id">
              <option :value="null">À déterminer</option>
              <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
            </select>
          </td>
          <td>
            <span class="status-badge" :class="m.status === 'COMPLETED' ? 'completed' : 'scheduled'">
              {{ m.status === 'COMPLETED' ? 'Joué' : 'À venir' }}
            </span>
          </td>
          <td>
            <button @click="saveMatch(m)" :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id">Enregistrer</button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    <p v-if="!sortedMatches.length && loaded">Aucun match pour cette compétition.</p>

    <h2>Ajouter un match</h2>
    <form class="inline" @submit.prevent="submitMatch">
      <select v-model.number="newMatch.team1Id">
        <option :value="null">Équipe 1 (à déterminer)</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
      </select>
      <select v-model.number="newMatch.team2Id">
        <option :value="null">Équipe 2 (à déterminer)</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
      </select>
      <input v-model="newMatch.roundLabel" placeholder="Round (ex: W1, QF, F)" required />
      <input v-model="newMatch.date" type="date" required />
      <input v-model="newMatch.time" type="time" />
      <select v-model="newMatch.bestOf">
        <option value="BO1">BO1</option>
        <option value="BO3">BO3</option>
        <option value="BO5">BO5</option>
      </select>
      <input class="score-input" type="number" min="0" v-model.number="newMatch.score1" placeholder="S1" />
      <input class="score-input" type="number" min="0" v-model.number="newMatch.score2" placeholder="S2" />
      <button type="submit">Ajouter</button>
    </form>

    <details class="playoff-options">
      <summary>Options playoffs / bracket (facultatif)</summary>
      <div class="inline">
        <select v-model="newMatch.phase">
          <option value="REGULAR_SEASON">Saison régulière</option>
          <option value="PLAYOFFS">Playoffs</option>
        </select>
        <select v-model="newMatch.bracketSide">
          <option :value="null">Côté bracket (aucun)</option>
          <option value="GROUP">Poules</option>
          <option value="PLAY_IN">Play-in</option>
          <option value="UPPER">Bracket vainqueurs</option>
          <option value="LOWER">Bracket perdants</option>
        </select>
        <select v-model.number="newMatch.nextMatchId">
          <option :value="null">Match suivant (vainqueur) : aucun</option>
          <option v-for="m in playoffMatches" :key="m.id" :value="m.id">
            #{{ m.id }} — {{ m.roundLabel }} ({{ m.team1Code ?? '?' }} vs {{ m.team2Code ?? '?' }})
          </option>
        </select>
        <select v-model.number="newMatch.nextMatchSlot">
          <option :value="null">Slot</option>
          <option :value="1">Équipe 1</option>
          <option :value="2">Équipe 2</option>
        </select>
        <select v-model.number="newMatch.loserNextMatchId">
          <option :value="null">Match suivant (perdant) : aucun</option>
          <option v-for="m in playoffMatches" :key="m.id" :value="m.id">
            #{{ m.id }} — {{ m.roundLabel }} ({{ m.team1Code ?? '?' }} vs {{ m.team2Code ?? '?' }})
          </option>
        </select>
        <select v-model.number="newMatch.loserNextMatchSlot">
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
import api, { teamLogoUrl } from '../services/api'
import StandingsTable from '../components/StandingsTable.vue'
import HeadToHeadTable from '../components/HeadToHeadTable.vue'
import PlayoffBracket from '../components/PlayoffBracket.vue'
import { leagueColor } from '../leagueColors'

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
const dateSortDir = ref('asc')

const playoffMatches = computed(() => matches.value.filter(m => m.phase === 'PLAYOFFS' && !(m.bracketSide ?? '').startsWith('REGIONAL_')))
const regionalFinalsMatches = computed(() => matches.value.filter(m => (m.bracketSide ?? '').startsWith('REGIONAL_')))

const sortedMatches = computed(() => {
  const list = [...matches.value]
  list.sort((a, b) => {
    const ka = a.date + (a.time ?? '')
    const kb = b.date + (b.time ?? '')
    const cmp = ka.localeCompare(kb)
    return dateSortDir.value === 'asc' ? cmp : -cmp
  })
  return list
})

function toggleDateSort() {
  dateSortDir.value = dateSortDir.value === 'asc' ? 'desc' : 'asc'
}

function hasLogo(teamId) {
  return teams.value.find(t => t.id === teamId)?.hasLogo ?? false
}

function formatTime(t) {
  return t ? t.slice(0, 5) : ''
}

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
      cells.push({ teamAId: m.team1Id, teamBId: m.team2Id, score: `${m.score1}-${m.score2}` })
      cells.push({ teamAId: m.team2Id, teamBId: m.team1Id, score: `${m.score2}-${m.score1}` })
    }
    legs.push(cells)
  }
  return legs
}

function legLabel(index) {
  return index === 0 ? 'Match aller' : index === 1 ? 'Match retour' : `Manche ${index + 1}`
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
    edits[m.id] = {
      team1Id: m.team1Id,
      team2Id: m.team2Id,
      roundLabel: m.roundLabel,
      date: m.date,
      time: formatTime(m.time),
      bestOf: m.bestOf,
      phase: m.phase,
      score1: m.score1,
      score2: m.score2
    }
  }
  loaded.value = true
}

async function saveMatch(match) {
  error.value = ''
  const edit = edits[match.id]
  try {
    await api.updateMatch(match.id, {
      competitionId: match.competitionId,
      groupId: match.groupId,
      roundLabel: edit.roundLabel,
      date: edit.date,
      time: edit.time || null,
      bestOf: edit.bestOf,
      team1Id: edit.team1Id,
      team2Id: edit.team2Id,
      score1: edit.score1,
      score2: edit.score2,
      phase: edit.phase,
      bracketSide: match.bracketSide,
      nextMatchId: match.nextMatchId,
      nextMatchSlot: match.nextMatchSlot,
      loserNextMatchId: match.loserNextMatchId,
      loserNextMatchSlot: match.loserNextMatchSlot
    })
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de l'enregistrement du match."
  }
}

async function submitMatch() {
  error.value = ''
  try {
    await api.createMatch({
      competitionId: Number(props.id),
      groupId: null,
      roundLabel: newMatch.roundLabel,
      date: newMatch.date,
      time: newMatch.time || null,
      bestOf: newMatch.bestOf,
      team1Id: newMatch.team1Id,
      team2Id: newMatch.team2Id,
      score1: newMatch.score1,
      score2: newMatch.score2,
      phase: newMatch.phase,
      bracketSide: newMatch.bracketSide,
      nextMatchId: newMatch.nextMatchId,
      nextMatchSlot: newMatch.nextMatchSlot,
      loserNextMatchId: newMatch.loserNextMatchId,
      loserNextMatchSlot: newMatch.loserNextMatchSlot
    })
    Object.assign(newMatch, emptyNewMatch())
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de la création du match."
  }
}

watch(() => props.id, load)
onMounted(load)
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
.sortable {
  cursor: pointer;
  user-select: none;
}
.sortable:hover {
  color: var(--gold-bright);
}
.sort-arrow {
  font-size: 0.9em;
}
.table-scroll {
  overflow-x: auto;
  /* Sort du conteneur centre (#app, max-width 1080px) pour utiliser toute la
     largeur de la fenetre : le calendrier a trop de colonnes editables pour
     tenir dans une largeur de contenu classique. */
  width: 100vw;
  position: relative;
  left: 50%;
  right: 50%;
  margin-left: -50vw;
  margin-right: -50vw;
  padding: 0 16px;
}
.table-scroll table {
  width: auto;
}
.table-scroll th,
.table-scroll td {
  padding: 12px 10px;
  font-size: 0.95em;
}
.date-input {
  width: 130px;
  padding: 9px 6px;
}
.time-input {
  width: 84px;
  padding: 9px 6px;
}
.round-input {
  width: 70px;
  padding: 9px 6px;
}
.table-scroll select {
  padding: 9px 6px;
}
.team-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.team-cell select {
  width: 88px;
}
.table-scroll .team-logo {
  height: 24px;
  max-width: 34px;
  padding: 2px;
}
.table-scroll .score-input {
  width: 56px;
  padding: 9px 6px;
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
