<template>
  <span class="cdp">
    <span v-if="label" class="cdp-label">{{ label }}</span>
    <select
      v-if="groups.length"
      v-model="choice"
      class="cdp-select"
      :aria-label="`${label || 'Date'} : choisir une compétition`"
      @change="pick"
    >
      <option value="">{{ mode === 'start' ? 'Dès la compétition…' : "Jusqu'à la compétition…" }}</option>
      <optgroup v-for="g in groups" :key="g.season" :label="`Saison ${g.season}`">
        <option :value="`s:${g.season}`">
          {{ mode === 'start' ? 'Début' : 'Fin' }} de la saison {{ g.season }} · {{ fullDay(seasonDate(g)) }}
        </option>
        <option v-for="c in g.competitions" :key="c.competitionId" :value="`c:${c.competitionId}`">
          {{ c.name }} · {{ fullDay(competitionDate(c)) }}
        </option>
      </optgroup>
    </select>
    <input
      :value="modelValue"
      type="date"
      :aria-label="label || 'Date'"
      :required="required"
      @input="onInput"
    />
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import api from '../services/api'
import { fullDay } from '../format'

// Saisie d'une date de passage en choisissant une competition jouee par l'equipe : "start"
// prend la date de son premier match, "end" celle de son dernier. offsetDays decale la
// date retenue (ex : +1 pour quitter l'equipe le lendemain de son dernier match).
// La date reste modifiable a la main.
const props = defineProps({
  modelValue: { type: String, default: '' },
  teamId: { type: [Number, String], default: null },
  mode: { type: String, default: 'start' },
  label: { type: String, default: '' },
  required: { type: Boolean, default: false },
  offsetDays: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue'])

// Competitions deja chargees par equipe (changer d'equipe puis revenir ne refait pas l'appel)
const cache = new Map()

const competitions = ref([])
const choice = ref('')

const groups = computed(() => {
  const bySeason = new Map()
  for (const c of competitions.value) {
    if (!bySeason.has(c.season)) bySeason.set(c.season, [])
    bySeason.get(c.season).push(c)
  }
  return [...bySeason.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([season, list]) => ({
      season,
      competitions: [...list].sort((a, b) => a.firstMatchDate.localeCompare(b.firstMatchDate))
    }))
})

function competitionDate(c) {
  return shift(props.mode === 'start' ? c.firstMatchDate : c.lastMatchDate)
}

function seasonDate(g) {
  const dates = g.competitions.map(c => (props.mode === 'start' ? c.firstMatchDate : c.lastMatchDate)).sort()
  return shift(props.mode === 'start' ? dates[0] : dates[dates.length - 1])
}

function shift(iso) {
  if (!iso || !props.offsetDays) return iso
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + props.offsetDays)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

function pick() {
  if (!choice.value) return
  const [kind, id] = choice.value.split(':')
  const date =
    kind === 's'
      ? seasonDate(groups.value.find(g => String(g.season) === id))
      : competitionDate(competitions.value.find(c => String(c.competitionId) === id))
  emit('update:modelValue', date)
}

function onInput(event) {
  choice.value = ''
  emit('update:modelValue', event.target.value)
}

async function load(teamId) {
  choice.value = ''
  if (!teamId) {
    competitions.value = []
    return
  }
  if (!cache.has(teamId)) cache.set(teamId, api.getTeamCompetitions(teamId).catch(() => []))
  const list = await cache.get(teamId)
  if (teamId === props.teamId) competitions.value = list
}

watch(() => props.teamId, load, { immediate: true })
</script>

<style scoped>
.cdp {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 0.88em;
  color: var(--text-muted);
}
.cdp-select {
  max-width: 300px;
}
</style>
