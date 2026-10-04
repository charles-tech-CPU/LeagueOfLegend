<template>
  <form class="transfer" @submit.prevent="submit">
    <span class="transfer-label">
      {{ player.teamId ? `Transférer ${player.pseudo} (${player.teamCode}) vers` : `Faire signer ${player.pseudo} chez` }}
    </span>
    <select v-model="teamId" aria-label="Nouvelle équipe">
      <option v-if="player.teamId" :value="null">Aucune (sans équipe)</option>
      <option v-for="t in otherTeams" :key="t.id" :value="t.id">{{ t.code }} · {{ t.name }}</option>
    </select>
    <label class="transfer-date">
      à partir du
      <input v-model="date" type="date" aria-label="Date du transfert" required />
    </label>
    <button type="submit" :disabled="!date || (!player.teamId && teamId === null)">
      {{ teamId === null ? "Retirer de l'équipe" : 'Valider le transfert' }}
    </button>
    <button type="button" class="btn-secondary" @click="emit('cancel')">Annuler</button>
    <p class="transfer-hint">
      Le passage chez l'équipe actuelle se termine la veille ; l'historique et les résultats sont conservés.
    </p>
    <p v-if="error" class="error">{{ error }}</p>
  </form>
</template>

<script setup>
import { computed, ref } from 'vue'
import api from '../services/api'
import { localIsoDate } from '../format'

const props = defineProps({
  player: { type: Object, required: true },
  teams: { type: Array, required: true }
})
const emit = defineEmits(['done', 'cancel'])

const otherTeams = computed(() => props.teams.filter(t => t.id !== props.player.teamId))
const teamId = ref(props.player.teamId ? null : (otherTeams.value[0]?.id ?? null))
const date = ref(localIsoDate(new Date()))
const error = ref('')

async function submit() {
  error.value = ''
  try {
    await api.transferPlayer(props.player.id, { teamId: teamId.value, date: date.value })
    emit('done')
  } catch (e) {
    error.value = e.response?.data?.error ?? 'Erreur lors du transfert.'
  }
}
</script>

<style scoped>
.transfer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: var(--radius);
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.3);
}
.transfer-label {
  font-weight: 700;
}
.transfer-date {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.9em;
}
.transfer select {
  max-width: 260px;
}
.transfer-hint {
  flex-basis: 100%;
  margin: 0;
  font-size: 0.8em;
  color: var(--text-muted);
}
.transfer .error {
  flex-basis: 100%;
  margin: 0;
}
</style>
