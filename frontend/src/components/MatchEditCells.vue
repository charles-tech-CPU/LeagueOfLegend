<template>
  <td><input v-model="edit.date" class="date-input" type="date" aria-label="Date" /></td>
  <td><input v-model="edit.time" class="time-input" type="time" aria-label="Heure" /></td>
  <td>
    <select v-model="edit.phase" aria-label="Phase">
      <option value="REGULAR_SEASON">Saison rég.</option>
      <option value="PLAYOFFS">Playoffs</option>
    </select>
  </td>
  <td><input v-model="edit.roundLabel" class="round-input" aria-label="Round" /></td>
  <td>
    <select v-model="edit.bestOf" aria-label="Format (best of)">
      <option value="BO1">BO1</option>
      <option value="BO3">BO3</option>
      <option value="BO5">BO5</option>
    </select>
  </td>
  <td class="team-cell">
    <img v-if="hasLogo(edit.team1Id)" class="team-logo" :src="teamLogoUrl(edit.team1Id)" alt="" />
    <select v-model.number="edit.team1Id" aria-label="Équipe 1">
      <option :value="null">À déterminer</option>
      <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
    </select>
  </td>
  <td>
    <input v-model.number="edit.score1" class="score-input" type="number" min="0" aria-label="Score équipe 1" :disabled="!canSave(edit)" />
  </td>
  <td>
    <input v-model.number="edit.score2" class="score-input" type="number" min="0" aria-label="Score équipe 2" :disabled="!canSave(edit)" />
  </td>
  <td class="team-cell">
    <img v-if="hasLogo(edit.team2Id)" class="team-logo" :src="teamLogoUrl(edit.team2Id)" alt="" />
    <select v-model.number="edit.team2Id" aria-label="Équipe 2">
      <option :value="null">À déterminer</option>
      <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }}</option>
    </select>
  </td>
</template>

<script setup>
import { teamLogoUrl } from '../services/api'
import { canSave } from '../matchEdit'

// Cellules editables d'une ligne de match (date -> equipe 2), partagees par le
// calendrier d'une competition et la page "A venir". Le formulaire (voir
// toEditForm) est modifie en place, la ligne parente gere l'enregistrement.
const edit = defineModel({ type: Object, required: true })

const props = defineProps({
  teams: { type: Array, required: true }
})

function hasLogo(teamId) {
  return props.teams.find(t => t.id === teamId)?.hasLogo ?? false
}
</script>

<style scoped>
.date-input {
  width: 140px;
  padding: 9px 6px;
}
.time-input {
  width: 100px;
  padding: 9px 6px;
}
.round-input {
  width: 70px;
  padding: 9px 6px;
}
select {
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
.team-logo {
  height: 26px;
  width: 26px;
  padding: 2px;
}
.score-input {
  width: 56px;
  padding: 9px 6px;
}
</style>
