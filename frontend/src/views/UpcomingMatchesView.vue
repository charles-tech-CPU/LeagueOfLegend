<template>
  <h1>À venir</h1>
  <p class="subtitle">Tous les matchs pas encore joués, toutes compétitions confondues. Enregistre un score dès qu'un match est terminé, il quittera automatiquement cette liste.</p>

  <p v-if="todayCount" class="today-summary">
    <span class="today-badge">Aujourd'hui</span>
    {{ todayCount }} match{{ todayCount > 1 ? 's' : '' }} au programme
  </p>

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
        <tr
          v-for="m in sortedMatches"
          :key="m.id"
          :class="{ 'row-today': isToday(m) }"
          :style="{ '--league-color': leagueColor(m.competitionCode) }"
        >
          <td>
            <router-link :to="`/competitions/${m.competitionId}`" class="competition-link league-badge">{{ m.competitionCode }}</router-link>
            <span v-if="isToday(m)" class="today-badge">Aujourd'hui</span>
          </td>
          <MatchEditCells v-model="edits[m.id]" :teams="teams" />
          <td>
            <button :disabled="!canSave(edits[m.id])" @click="saveMatch(m)">Enregistrer</button>
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
import api from '../services/api'
import { leagueColor } from '../leagueColors'
import { canSave, kickoff, toEditForm, toMatchPayload } from '../matchEdit'
import { useSort } from '../composables/useSort'
import MatchEditCells from '../components/MatchEditCells.vue'

const matches = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const edits = reactive({})

const { toggleSort, sortArrow, sortList } = useSort('date', (m, field) =>
  field === 'competition' ? m.competitionCode : kickoff(m)
)

const sortedMatches = computed(() => sortList(matches.value))

// Date locale (et non UTC) au format ISO "AAAA-MM-JJ", comme m.date renvoye par l'API
function localIsoDate(d) {
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

const today = ref(localIsoDate(new Date()))

function isToday(match) {
  return match.date === today.value
}

const todayCount = computed(() => matches.value.filter(isToday).length)

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
}
.table-scroll tbody tr {
  border-left: 3px solid var(--league-color);
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
.table-scroll tbody tr.row-today {
  background: rgba(10, 200, 185, 0.1);
  box-shadow: inset 0 1px 0 rgba(10, 200, 185, 0.45), inset 0 -1px 0 rgba(10, 200, 185, 0.45);
}
.table-scroll tbody tr.row-today:hover {
  background: rgba(10, 200, 185, 0.16);
}
.today-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.75em;
  font-weight: 700;
  letter-spacing: 0.03em;
  color: var(--bg);
  background: var(--cyan);
  white-space: nowrap;
}
.today-summary {
  color: var(--text);
  margin-bottom: 16px;
}
.today-summary .today-badge {
  margin-left: 0;
  margin-right: 6px;
}
.empty {
  padding: 24px 0;
  color: var(--text-muted);
}
</style>
