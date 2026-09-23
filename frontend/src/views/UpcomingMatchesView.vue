<template>
  <h1>À venir</h1>
  <p class="subtitle">Tous les matchs pas encore joués, toutes compétitions confondues. Enregistre un score dès qu'un match est terminé, il quittera automatiquement cette liste.</p>

  <div v-if="loaded && matches.length" class="summary">
    <div class="summary-card" :class="{ highlight: todayCount }">
      <span class="summary-value">{{ todayCount }}</span>
      <span class="summary-label">aujourd'hui</span>
    </div>
    <div class="summary-card">
      <span class="summary-value">{{ weekCount }}</span>
      <span class="summary-label">dans les 7 jours</span>
    </div>
    <div class="summary-card">
      <span class="summary-value">{{ matches.length }}</span>
      <span class="summary-label">au total</span>
    </div>
  </div>

  <div class="table-scroll">
    <table v-if="sortedMatches.length">
      <thead>
        <tr>
          <th class="sortable" tabindex="0" @click="toggleSort('competition')" @keydown.enter="toggleSort('competition')">
            Compétition <span class="sort-arrow">{{ sortArrow('competition') }}</span>
          </th>
          <th class="sortable" tabindex="0" @click="toggleSort('date')" @keydown.enter="toggleSort('date')">
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
        <template v-for="m in sortedMatches" :key="m.id">
          <tr v-if="dayHeaders.has(m.id)" class="day-row" :class="{ today: isToday(m) }">
            <td colspan="11">{{ dayHeaders.get(m.id) }}</td>
          </tr>
          <tr
            :class="{ 'row-today': isToday(m) }"
            :style="{ '--league-color': leagueColor(m.competitionCode) }"
          >
            <td>
              <router-link :to="`/competitions/${m.competitionId}`" class="competition-link league-badge">{{ m.competitionCode }}</router-link>
            </td>
            <MatchEditCells v-model="edits[m.id]" :teams="teams" />
            <td>
              <button :disabled="!canSave(edits[m.id])" @click="saveMatch(m)">Enregistrer</button>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
    <p v-else-if="loaded" class="empty">Aucun match à venir : tout est joué 🎉</p>
  </div>

  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../services/api'
import { leagueColor } from '../leagueColors'
import { localIsoDate, longDay } from '../format'
import { canSave, kickoff, toEditForm, toMatchPayload } from '../matchEdit'
import { useSort } from '../composables/useSort'
import MatchEditCells from '../components/MatchEditCells.vue'

const matches = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const edits = reactive({})

const { sortBy, toggleSort, sortArrow, sortList } = useSort('date', (m, field) =>
  field === 'competition' ? m.competitionCode : kickoff(m)
)

const sortedMatches = computed(() => sortList(matches.value))

const today = ref(localIsoDate(new Date()))

function isToday(match) {
  return match.date === today.value
}

const todayCount = computed(() => matches.value.filter(isToday).length)

const weekCount = computed(() => {
  const limit = new Date()
  limit.setDate(limit.getDate() + 7)
  const limitIso = localIsoDate(limit)
  return matches.value.filter(m => m.date >= today.value && m.date < limitIso).length
})

// Intercalaire "Aujourd'hui", "Demain", "samedi 26 septembre"... devant le
// premier match de chaque jour, seulement quand la liste est triee par date.
const dayHeaders = computed(() => {
  const headers = new Map()
  if (sortBy.value !== 'date') return headers
  let previous = null
  for (const m of sortedMatches.value) {
    if (m.date !== previous) {
      headers.set(m.id, longDay(m.date, today.value))
      previous = m.date
    }
  }
  return headers
})

async function load() {
  const [matchList, teamList] = await Promise.all([
    api.getScheduledMatches(),
    api.getTeams()
  ])
  matches.value = matchList
  teams.value = teamList

  for (const m of matchList) {
    edits[m.id] = toEditForm(m)
  }
  today.value = localIsoDate(new Date())
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

onMounted(load)
</script>

<style scoped>
.subtitle {
  color: var(--text-muted);
  margin-top: -8px;
  margin-bottom: 20px;
  max-width: 720px;
}
.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 22px;
}
.summary-card {
  display: flex;
  flex-direction: column;
  min-width: 140px;
  padding: 12px 18px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
}
.summary-card.highlight {
  border-color: rgba(34, 211, 238, 0.5);
  box-shadow: var(--glow);
}
.summary-value {
  font-family: "Outfit", sans-serif;
  font-size: 1.6em;
  font-weight: 800;
}
.highlight .summary-value {
  color: var(--accent);
}
.summary-label {
  font-size: 0.78em;
  color: var(--text-muted);
}
.table-scroll tbody tr td:first-child {
  box-shadow: inset 3px 0 0 var(--league-color);
}
.table-scroll tbody tr.day-row td {
  box-shadow: none;
  padding: 16px 14px 8px;
  font-family: "Outfit", sans-serif;
  font-weight: 700;
  font-size: 0.95em;
  color: var(--text);
  background: var(--panel-alt);
}
.table-scroll tbody tr.day-row.today td {
  color: var(--accent);
}
.table-scroll tbody tr.day-row:hover {
  background: none;
}
.competition-link.league-badge:hover {
  color: var(--league-color);
  filter: brightness(1.2);
}
.table-scroll tbody tr.row-today {
  background: rgba(34, 211, 238, 0.06);
}
.table-scroll tbody tr.row-today:hover {
  background: rgba(34, 211, 238, 0.12);
}
.empty {
  padding: 24px 0;
  color: var(--text-muted);
}
</style>
