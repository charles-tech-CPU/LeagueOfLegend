<template>
  <p><router-link to="/">← Toutes les compétitions</router-link></p>
  <h1>{{ competition?.name ?? '...' }}</h1>

  <div class="tabs">
    <button type="button" :class="{ active: activeTab === 'standings' }" @click="activeTab = 'standings'">Classement</button>
    <button type="button" :class="{ active: activeTab === 'bracket' }" @click="activeTab = 'bracket'">Bracket</button>
    <button type="button" :class="{ active: activeTab === 'calendar' }" @click="activeTab = 'calendar'">Calendrier</button>
  </div>

  <section v-if="activeTab === 'standings'">
    <h2>Classement (saison régulière)</h2>
    <StandingsTable :rows="standings" />
  </section>

  <section v-if="activeTab === 'bracket'">
    <h2>Playoffs</h2>
    <PlayoffBracket :matches="playoffMatches" :teams="teams" />
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
import PlayoffBracket from '../components/PlayoffBracket.vue'

const props = defineProps({
  id: { type: [String, Number], required: true }
})

const competition = ref(null)
const matches = ref([])
const standings = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const edits = reactive({})
const activeTab = ref('standings')
const dateSortDir = ref('asc')

const playoffMatches = computed(() => matches.value.filter(m => m.phase === 'PLAYOFFS'))

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

async function load() {
  const competitionId = Number(props.id)
  const [competitions, matchList, standingRows, teamList] = await Promise.all([
    api.getCompetitions(),
    api.getMatchesByCompetition(competitionId),
    api.getStandings(competitionId),
    api.getTeams()
  ])
  competition.value = competitions.find(c => c.id === competitionId) ?? null
  matches.value = matchList
  standings.value = standingRows
  teams.value = teamList

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
  padding: 6px 6px;
  font-size: 0.92em;
}
.date-input {
  width: 118px;
  padding: 6px 4px;
}
.time-input {
  width: 76px;
  padding: 6px 4px;
}
.round-input {
  width: 60px;
  padding: 6px 4px;
}
.table-scroll select {
  padding: 6px 4px;
}
.team-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.team-cell select {
  width: 78px;
}
.table-scroll .team-logo {
  height: 20px;
  max-width: 30px;
  padding: 2px;
}
.playoff-options {
  margin-bottom: 24px;
}
.playoff-options summary {
  cursor: pointer;
  color: var(--text-muted);
  margin-bottom: 8px;
}
</style>
