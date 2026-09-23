<template>
  <h1>Équipes</h1>

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
          <td>{{ t.code }}</td>
          <td>{{ t.name }}</td>
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
  <p v-if="error" style="color:#ff6b6b">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api, { teamLogoUrl } from '../services/api'

const teams = ref([])
const loaded = ref(false)
const error = ref('')
const form = reactive({ code: '', name: '', region: '' })

const sortBy = ref('name')
const sortDir = ref('asc')
const editingId = ref(null)
const editForm = reactive({ code: '', name: '', region: '' })

const sortedTeams = computed(() => {
  const list = [...teams.value]
  list.sort((a, b) => {
    const va = (a[sortBy.value] ?? '').toString()
    const vb = (b[sortBy.value] ?? '').toString()
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
.sortable {
  cursor: pointer;
  user-select: none;
}
.sortable:hover {
  color: var(--gold-bright);
}
.sort-arrow {
  font-size: 0.8em;
  color: var(--gold);
}
.actions {
  display: flex;
  gap: 6px;
}
.btn-secondary {
  background: var(--panel-alt);
  color: var(--text);
  border-color: var(--border);
}
.btn-secondary:hover {
  background: var(--border);
}
</style>
