<template>
  <h1>Équipes <span class="count">{{ teams.length }}</span></h1>

  <table v-if="sortedTeams.length">
    <thead>
      <tr>
        <th></th>
        <th class="sortable" tabindex="0" @click="toggleSort('code')" @keydown.enter="toggleSort('code')">
          Code <span class="sort-arrow">{{ sortArrow('code') }}</span>
        </th>
        <th class="sortable" tabindex="0" @click="toggleSort('name')" @keydown.enter="toggleSort('name')">
          Nom <span class="sort-arrow">{{ sortArrow('name') }}</span>
        </th>
        <th class="sortable" tabindex="0" @click="toggleSort('region')" @keydown.enter="toggleSort('region')">
          Région <span class="sort-arrow">{{ sortArrow('region') }}</span>
        </th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="t in sortedTeams" :key="t.id">
        <td>
          <img v-if="t.hasLogo" class="team-logo" :src="teamLogoUrl(t.id)" :alt="t.code" />
        </td>
        <template v-if="editingId === t.id">
          <td><input v-model="editForm.code" aria-label="Code" /></td>
          <td><input v-model="editForm.name" aria-label="Nom" /></td>
          <td><input v-model="editForm.region" aria-label="Région" /></td>
          <td class="actions">
            <button @click="saveEdit(t)">Enregistrer</button>
            <button type="button" class="btn-secondary" @click="cancelEdit">Annuler</button>
          </td>
        </template>
        <template v-else>
          <td class="code">{{ t.code }}</td>
          <td class="name">{{ t.name }}</td>
          <td>{{ t.region }}</td>
          <td class="actions">
            <button type="button" class="btn-secondary" @click="startEdit(t)">Modifier</button>
          </td>
        </template>
      </tr>
    </tbody>
  </table>
  <p v-else-if="loaded">Aucune équipe pour l'instant.</p>

  <h2>Ajouter une équipe</h2>
  <form class="inline" @submit.prevent="submit">
    <input v-model="form.code" placeholder="Code (ex: G2)" aria-label="Code" required />
    <input v-model="form.name" placeholder="Nom complet (ex: G2 Esports)" aria-label="Nom complet" required />
    <input v-model="form.region" placeholder="Région (ex: EMEA)" aria-label="Région" />
    <button type="submit">Ajouter</button>
  </form>
  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { useSort } from '../composables/useSort'

const teams = ref([])
const loaded = ref(false)
const error = ref('')
const form = reactive({ code: '', name: '', region: '' })

const editingId = ref(null)
const editForm = reactive({ code: '', name: '', region: '' })

const { toggleSort, sortArrow, sortList } = useSort('name', (team, field) => (team[field] ?? '').toString())

const sortedTeams = computed(() => sortList(teams.value))

function startEdit(team) {
  editingId.value = team.id
  editForm.code = team.code
  editForm.name = team.name
  editForm.region = team.region
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(team) {
  error.value = ''
  try {
    await api.updateTeam(team.id, { ...editForm })
    editingId.value = null
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de la modification."
  }
}

async function load() {
  teams.value = await api.getTeams()
  loaded.value = true
}

async function submit() {
  error.value = ''
  try {
    await api.createTeam({ ...form })
    form.code = ''
    form.name = ''
    form.region = ''
    await load()
  } catch (e) {
    error.value = e.response?.data?.error ?? "Erreur lors de la création."
  }
}

onMounted(load)
</script>

<style scoped>
.code {
  font-weight: 800;
}
.name {
  color: var(--text-muted);
}
.actions {
  display: flex;
  gap: 6px;
}
.count {
  font-size: 0.45em;
  padding: 4px 12px;
  border-radius: 999px;
  color: var(--text-muted);
  background: var(--panel);
  border: 1px solid var(--border);
}
</style>
