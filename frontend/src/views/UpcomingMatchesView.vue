<template>
  <h1>À venir</h1>
  <p class="subtitle">Tous les matchs pas encore joués, toutes compétitions confondues. Enregistre un score dès qu'un match est terminé, il quittera automatiquement cette liste.</p>

  <div class="table-scroll">
    <table v-if="sortedMatches.length">
      <thead>
        <tr>
          <th class="sortable" @click="toggleSort('competition')">
            Compétition <span class="sort-arrow">{{ sortArrow('competition') }}</span>
          </th>
          <th class="sortable" @click="toggleSort('date')">
            Date <span class="sort-arrow">{{ sortArrow('date') }}</span>
          </th>
          <th>Heure</th>
          <th>Phase</th>
          <th>Round</th>
          <th>BO</th>
          <th>Équipe 1</th>
          <th></th>
          <th></th>
          <th>Équipe 2</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="m in sortedMatches" :key="m.id" :style="{ '--league-color': leagueColor(m.competitionCode) }">
          <td>
            <router-link :to="`/competitions/${m.competitionId}`" class="competition-link league-badge">{{ m.competitionCode }}</router-link>
          </td>
          <td><input v-model="edits[m.id].date" class="date-input" type="date" /></td>
          <td><input v-model="edits[m.id].time" class="time-input" type="time" /></td>
          <td>
            <select v-model="edits[m.id].phase">
              <option value="REGULAR_SEASON">Saison rég.</option>
              <option value="PLAYOFFS">Playoffs</option>
            </select>
          </td>
          <td><input v-model="edits[m.id].roundLabel" class="round-input" /></td>
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
            <input v-model.number="edits[m.id].score1" class="score-input" type="number" min="0" :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id" />
          </td>
          <td>
            <input v-model.number="edits[m.id].score2" class="score-input" type="number" min="0" :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id" />
          </td>
          <td class="team-cell">
            <img v-if="hasLogo(edits[m.id].team2Id)" class="team-logo" :src="teamLogoUrl(edits[m.id].team2Id)" alt="" />
            <select v-model.number="edits[m.id].team2Id">
              <option :value="null">À déterminer</option>
              <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
            </select>
          </td>
          <td>
            <button :disabled="!edits[m.id].team1Id || !edits[m.id].team2Id" @click="saveMatch(m)">Enregistrer</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="loaded" class="empty">Aucun match à venir : tout est joué 🎉</p>
  </div>

  <p v-if="error" style="color:#ff6b6b">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { leagueColor } from '../leagueColors'

const matches = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const edits = reactive({})

const sortBy = ref('date')
const sortDir = ref('asc')

const sortedMatches = computed(() => {
  const list = [...matches.value]
  list.sort((a, b) => {
    let va, vb
    if (sortBy.value === 'competition') {
      va = a.competitionCode
      vb = b.competitionCode
    } else {
      va = a.date + (a.time ?? '')
      vb = b.date + (b.time ?? '')
    }
    const cmp = va.localeCompare(vb)
    return sortDir.value === 'asc' ? cmp : -cmp
  })
  return list
})

function toggleSort(field) {
  if (sortBy.value === field) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = field
    sortDir.value = 'asc'
  }
}

function sortArrow(field) {
  if (sortBy.value !== field) return ''
  return sortDir.value === 'asc' ? '▲' : '▼'
}

function hasLogo(teamId) {
  return teams.value.find(t => t.id === teamId)?.hasLogo ?? false
}

function formatTime(t) {
  return t ? t.slice(0, 5) : ''
}

async function load() {
  const [matchList, teamList] = await Promise.all([
    api.getScheduledMatches(),
    api.getTeams()
  ])
  matches.value = matchList
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

onMounted(load)
</script>

<style scoped>
.subtitle {
  color: var(--text-muted);
  margin-top: -8px;
  margin-bottom: 20px;
}
.table-scroll {
  overflow-x: auto;
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
.table-scroll tbody tr {
  border-left: 3px solid var(--league-color);
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
.competition-link.league-badge {
  display: inline-block;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.85em;
  letter-spacing: 0.03em;
  color: var(--league-color);
  background: color-mix(in srgb, var(--league-color) 18%, transparent);
  border: 1px solid color-mix(in srgb, var(--league-color) 45%, transparent);
}
.competition-link.league-badge:hover {
  color: var(--league-color);
  filter: brightness(1.2);
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
.empty {
  padding: 24px 0;
  color: var(--text-muted);
}
</style>
