<template>
  <h1>Compétitions</h1>

  <table v-if="competitions.length">
    <thead>
      <tr>
        <th>Code</th>
        <th>Nom</th>
        <th>Type</th>
        <th>Saison</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="c in competitions" :key="c.id">
        <td>{{ c.code }}</td>
        <td>{{ c.name }}</td>
        <td>{{ c.type === 'REGIONAL_LEAGUE' ? 'Ligue régionale' : 'Événement international' }}</td>
        <td>{{ c.season }}</td>
        <td><router-link :to="`/competitions/${c.id}`">Voir les matchs →</router-link></td>
      </tr>
    </tbody>
  </table>
  <p v-else-if="loaded">Aucune compétition pour l'instant.</p>

  <h2>Ajouter une compétition</h2>
  <form class="inline" @submit.prevent="submit">
    <input v-model="form.code" placeholder="Code (ex: LEC)" required />
    <input v-model="form.name" placeholder="Nom (ex: LEC 2026)" required />
    <select v-model="form.type">
      <option value="REGIONAL_LEAGUE">Ligue régionale</option>
      <option value="INTERNATIONAL_EVENT">Événement international</option>
    </select>
    <input v-model="form.region" placeholder="Région (ex: EMEA)" />
    <input v-model.number="form.season" type="number" placeholder="Saison" required />
    <button type="submit">Ajouter</button>
  </form>
  <p v-if="error" style="color:#ff6b6b">{{ error }}</p>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '../services/api'

const competitions = ref([])
const loaded = ref(false)
const error = ref('')

const form = reactive({
  code: '',
  name: '',
  type: 'REGIONAL_LEAGUE',
  region: '',
  season: new Date().getFullYear()
})

async function load() {
  competitions.value = await api.getCompetitions()
  loaded.value = true
}

async function submit() {
  error.value = ''
  try {
    await api.createCompetition({ ...form })
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
