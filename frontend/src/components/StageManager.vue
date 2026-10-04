<template>
  <div class="stage-manager">
    <section class="calendar-card">
      <h3>Calendrier</h3>
      <form class="calendar-form" @submit.prevent="saveCalendar">
        <label>
          Saison
          <input v-model.number="calendar.season" class="year" type="number" min="2009" required />
        </label>
        <label>
          Split
          <select v-model="calendar.split">
            <option :value="null">Sans split (événement)</option>
            <option v-for="s in SPLITS" :key="s.key" :value="s.key">{{ s.label }}</option>
          </select>
        </label>
        <label>Début <input v-model="calendar.startDate" type="date" /></label>
        <label>Fin <input v-model="calendar.endDate" type="date" /></label>
        <button type="submit" class="btn-secondary">Enregistrer</button>
        <span v-if="calendarSaved" class="saved">✓ Enregistré</span>
      </form>
    </section>

    <h3 class="section-title">Phases</h3>
    <ol v-if="stages.length" class="stages">
      <li v-for="s in stages" :key="s.id" class="stage">
        <span class="stage-position">{{ s.position }}</span>
        <div class="stage-body">
          <div class="stage-head">
            <strong class="stage-name">{{ s.name }}</strong>
            <span class="format-badge">{{ formatLabel(s.format) }}</span>
            <span v-if="s.bestOf" class="muted">{{ s.bestOf }}</span>
          </div>
          <p class="stage-meta">
            {{ s.teamCount }} équipes<template v-if="s.groupCount > 1"> · {{ s.groupCount }} groupes</template>
            <template v-if="s.advancing"> · {{ s.advancing }} qualifiés</template>
            · {{ s.playedCount }}/{{ s.matchCount }} matchs joués
          </p>
          <div class="stage-actions">
            <template v-if="s.format === 'SWISS'">
              <input v-model="swissDates[s.id]" type="date" aria-label="Date de la ronde suivante" />
              <button type="button" class="btn-small" @click="nextRound(s)">Générer la ronde suivante</button>
            </template>
            <button
              v-if="pendingDeleteId === s.id"
              type="button"
              class="btn-danger btn-small"
              @click="removeStage(s)"
            >
              Confirmer : supprimer la phase et ses {{ s.matchCount }} matchs
            </button>
            <button v-else type="button" class="btn-secondary btn-small" @click="pendingDeleteId = s.id">Supprimer</button>
          </div>
        </div>
      </li>
    </ol>
    <p v-else class="muted">Aucune phase pour l'instant : décris le format de la compétition ci-dessous.</p>

    <h3 class="section-title">Ajouter une phase</h3>
    <form class="stage-form" @submit.prevent="createStage">
      <div class="row">
        <label class="grow">
          Nom
          <input v-model="form.name" placeholder="ex: Saison régulière, Groupes, Playoffs" required />
        </label>
        <label>
          Format
          <select v-model="form.format">
            <option v-for="f in STAGE_FORMATS" :key="f.key" :value="f.key">{{ f.label }}</option>
          </select>
        </label>
        <label>
          Séries
          <select v-model="form.bestOf">
            <option v-for="b in BEST_OFS" :key="b" :value="b">{{ b }}</option>
          </select>
        </label>
        <label v-if="isBracket">
          Finale
          <select v-model="form.finalBestOf">
            <option :value="null">Comme les autres</option>
            <option v-for="b in BEST_OFS" :key="b" :value="b">{{ b }}</option>
          </select>
        </label>
      </div>
      <p class="format-hint">{{ currentFormat.hint }}</p>

      <div class="row">
        <label>Début <input v-model="form.startDate" type="date" required /></label>
        <label>
          Jours entre deux rondes
          <input v-model.number="form.daysBetweenRounds" class="small-number" type="number" min="0" />
        </label>
        <label>
          Qualifiés <span class="muted">(facultatif)</span>
          <input v-model.number="form.advancing" class="small-number" type="number" min="1" />
        </label>
        <label v-if="isGroupStage">
          Nombre de poules
          <input v-model.number="groupCount" class="small-number" type="number" min="1" max="8" />
        </label>
      </div>

      <div class="groups">
        <div v-for="(group, g) in groups" :key="g" class="group-box">
          <h4>
            {{ isGroupStage ? (groups.length > 1 ? `Groupe ${String.fromCharCode(65 + g)}` : 'Équipes') : 'Têtes de série (de la meilleure à la moins bonne)' }}
            <span class="muted">{{ group.length }}</span>
          </h4>
          <ol class="team-list">
            <li v-for="(teamId, i) in group" :key="teamId">
              <span class="seed">{{ i + 1 }}</span>
              <span class="team-label">{{ teamLabel(teamId) }}</span>
              <button type="button" class="icon-btn" :disabled="i === 0" aria-label="Monter" @click="move(g, i, -1)">↑</button>
              <button type="button" class="icon-btn" :disabled="i === group.length - 1" aria-label="Descendre" @click="move(g, i, 1)">↓</button>
              <button type="button" class="icon-btn" aria-label="Retirer" @click="group.splice(i, 1)">✕</button>
            </li>
          </ol>
          <input
            v-model="pickers[g]"
            :list="`teams-${uid}`"
            placeholder="Ajouter une équipe (tape son code ou son nom)"
            aria-label="Ajouter une équipe"
            @change="pick(g)"
            @keydown.enter.prevent="pick(g)"
          />
        </div>
      </div>
      <datalist :id="`teams-${uid}`">
        <option v-for="t in availableTeams" :key="t.id" :value="teamOptionLabel(t)"></option>
      </datalist>

      <button type="submit" :disabled="!canCreate">Créer la phase et générer les matchs</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import api from '../services/api'
import { localIsoDate } from '../format'
import { BEST_OFS, SPLITS, STAGE_FORMATS, formatLabel } from '../formats'

const props = defineProps({
  competition: { type: Object, required: true },
  stages: { type: Array, required: true },
  teams: { type: Array, required: true }
})
const emit = defineEmits(['changed'])

const uid = Math.random().toString(36).slice(2, 8)
const error = ref('')
const pendingDeleteId = ref(null)
const swissDates = reactive({})

// ---------- Calendrier de la competition ----------

const calendar = reactive({ season: null, split: null, startDate: '', endDate: '' })
const calendarSaved = ref(false)

watch(
  () => props.competition,
  c => {
    calendar.season = c.season
    calendar.split = c.split
    calendar.startDate = c.startDate ?? ''
    calendar.endDate = c.endDate ?? ''
  },
  { immediate: true }
)

async function saveCalendar() {
  error.value = ''
  calendarSaved.value = false
  try {
    const c = props.competition
    await api.updateCompetition(c.id, {
      code: c.code,
      name: c.name,
      type: c.type,
      region: c.region,
      season: calendar.season,
      split: calendar.split,
      startDate: calendar.startDate || null,
      endDate: calendar.endDate || null
    })
    calendarSaved.value = true
    emit('changed')
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de l'enregistrement du calendrier."
  }
}

// ---------- Nouvelle phase ----------

const form = reactive({
  name: '',
  format: 'DOUBLE_ROUND_ROBIN',
  bestOf: 'BO1',
  finalBestOf: null,
  startDate: props.competition.startDate ?? localIsoDate(new Date()),
  daysBetweenRounds: 7,
  advancing: null
})
const groupCount = ref(1)
const groups = ref([[]])
const pickers = reactive({})

const currentFormat = computed(() => STAGE_FORMATS.find(f => f.key === form.format))
const isGroupStage = computed(() => form.format === 'ROUND_ROBIN' || form.format === 'DOUBLE_ROUND_ROBIN')
const isBracket = computed(() => form.format === 'SINGLE_ELIMINATION' || form.format === 'DOUBLE_ELIMINATION')
const usedIds = computed(() => new Set(groups.value.flat()))
const availableTeams = computed(() => props.teams.filter(t => !usedIds.value.has(t.id)))
const canCreate = computed(() => form.name.trim() && groups.value.every(g => g.length >= 2))

watch(
  () => form.format,
  () => {
    if (!isGroupStage.value) groupCount.value = 1
    if (isBracket.value && form.bestOf === 'BO1') form.bestOf = 'BO3'
  }
)

watch(groupCount, n => {
  const count = Math.min(Math.max(Number(n) || 1, 1), 8)
  while (groups.value.length < count) groups.value.push([])
  // Les equipes d'un groupe supprime retournent dans le dernier groupe conserve.
  while (groups.value.length > count) groups.value[count - 1].push(...groups.value.pop())
})

function teamOptionLabel(team) {
  return `${team.code} · ${team.name}`
}

function teamLabel(teamId) {
  const team = props.teams.find(t => t.id === teamId)
  return team ? teamOptionLabel(team) : `#${teamId}`
}

function pick(g) {
  const value = (pickers[g] ?? '').trim().toLowerCase()
  if (!value) return
  const team =
    availableTeams.value.find(t => teamOptionLabel(t).toLowerCase() === value) ??
    availableTeams.value.find(t => t.code.toLowerCase() === value)
  if (team) {
    groups.value[g].push(team.id)
    pickers[g] = ''
  }
}

function move(g, i, delta) {
  const list = groups.value[g]
  const [id] = list.splice(i, 1)
  list.splice(i + delta, 0, id)
}

async function createStage() {
  error.value = ''
  try {
    await api.createStage(props.competition.id, {
      ...form,
      name: form.name.trim(),
      finalBestOf: isBracket.value ? form.finalBestOf : null,
      advancing: form.advancing || null,
      groups: groups.value
    })
    form.name = ''
    groups.value = [[]]
    groupCount.value = 1
    emit('changed')
  } catch (e) {
    error.value = e.response?.data?.error ?? 'Erreur lors de la création de la phase.'
  }
}

async function nextRound(stage) {
  error.value = ''
  try {
    await api.nextSwissRound(stage.id, swissDates[stage.id] || undefined)
    emit('changed')
  } catch (e) {
    error.value = e.response?.data?.error ?? 'Erreur lors de la génération de la ronde.'
  }
}

async function removeStage(stage) {
  error.value = ''
  try {
    await api.deleteStage(stage.id)
    pendingDeleteId.value = null
    emit('changed')
  } catch (e) {
    error.value = e.response?.data?.error ?? 'Erreur lors de la suppression de la phase.'
  }
}
</script>

<style scoped>
.stage-manager {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
h3 {
  font-family: "Outfit", sans-serif;
  font-size: 1.05em;
  margin: 0;
}
.section-title {
  margin-top: 10px;
}
.calendar-card,
.stage-form {
  padding: 16px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
}
.calendar-card h3 {
  margin-bottom: 12px;
}
.calendar-form,
.row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 0.8em;
  font-weight: 600;
  color: var(--text-muted);
}
label.grow {
  flex: 1 1 240px;
}
.year,
.small-number {
  width: 100px;
}
.saved {
  color: var(--win);
  font-size: 0.85em;
  font-weight: 700;
}
.stages {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.stage {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}
.stage-position {
  display: grid;
  place-items: center;
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  color: #0a0c18;
  background: var(--accent-grad);
}
.stage-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.stage-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.stage-name {
  font-size: 1.05em;
}
.format-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.75em;
  font-weight: 700;
  color: var(--accent-2);
  background: rgba(167, 139, 250, 0.12);
  border: 1px solid rgba(167, 139, 250, 0.4);
}
.stage-meta {
  margin: 0;
  font-size: 0.85em;
  color: var(--text-muted);
}
.stage-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.stage-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-style: dashed;
  border-color: var(--border-strong);
}
.stage-form > button {
  align-self: flex-start;
}
.format-hint {
  margin: -4px 0 0;
  font-size: 0.82em;
  color: var(--text-muted);
}
.groups {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.group-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: var(--radius-sm);
  background: var(--panel-alt);
  border: 1px solid var(--border);
}
.group-box h4 {
  display: flex;
  justify-content: space-between;
  margin: 0;
  font-size: 0.85em;
}
.team-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.team-list li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88em;
}
.seed {
  width: 22px;
  color: var(--text-dim);
  font-weight: 700;
  text-align: right;
}
.team-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.icon-btn {
  padding: 2px 7px;
  font-size: 0.8em;
  background: transparent;
  color: var(--text-muted);
  box-shadow: inset 0 0 0 1px var(--border);
}
.icon-btn:hover:not(:disabled) {
  color: var(--text);
  filter: none;
}
.btn-small {
  font-size: 0.75em;
  padding: 5px 11px;
}
.btn-danger {
  background: rgba(251, 113, 133, 0.15);
  color: var(--loss);
  box-shadow: inset 0 0 0 1px rgba(251, 113, 133, 0.5);
}
</style>
