<template>
  <h1>Joueurs <span class="count">{{ players.length }}</span></h1>

  <div class="filters">
    <input v-model="search" type="search" placeholder="Rechercher un joueur ou une équipe" aria-label="Rechercher" />
    <label class="toggle">
      <input v-model="freeAgentsOnly" type="checkbox" />
      Sans équipe uniquement <span class="muted">({{ freeAgentCount }})</span>
    </label>
  </div>

  <table v-if="sortedPlayers.length">
    <thead>
      <tr>
        <th class="sortable" tabindex="0" @click="toggleSort('pseudo')" @keydown.enter="toggleSort('pseudo')">
          Pseudo <span class="sort-arrow">{{ sortArrow('pseudo') }}</span>
        </th>
        <th class="sortable" tabindex="0" @click="toggleSort('position')" @keydown.enter="toggleSort('position')">
          Poste <span class="sort-arrow">{{ sortArrow('position') }}</span>
        </th>
        <th class="sortable" tabindex="0" @click="toggleSort('nationality')" @keydown.enter="toggleSort('nationality')">
          Nationalité <span class="sort-arrow">{{ sortArrow('nationality') }}</span>
        </th>
        <th class="sortable" tabindex="0" @click="toggleSort('team')" @keydown.enter="toggleSort('team')">
          Équipe <span class="sort-arrow">{{ sortArrow('team') }}</span>
        </th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <template v-for="p in sortedPlayers" :key="p.id">
      <tr>
        <template v-if="editingId === p.id">
          <td><input v-model="editForm.pseudo" class="compact" aria-label="Pseudo" required maxlength="50" /></td>
          <td>
            <select v-model="editForm.position" aria-label="Poste">
              <option v-for="o in POSITIONS" :key="o.key" :value="o.key">{{ o.key }}</option>
            </select>
          </td>
          <td><CountrySelect v-model="editForm.nationality" class="compact" /></td>
          <td class="muted">{{ p.teamCode ?? 'Sans équipe' }}</td>
          <td class="actions">
            <button type="button" :disabled="!editForm.pseudo.trim()" @click="saveEdit(p)">Enregistrer</button>
            <button type="button" class="btn-secondary" @click="cancelEdit">Annuler</button>
          </td>
        </template>
        <template v-else>
          <td class="pseudo">
            <router-link :to="`/players/${p.id}`" class="pseudo-cell">
              <CountryFlag v-if="p.nationality" :code="p.nationality" :show-code="false" />{{ p.pseudo }}
            </router-link>
          </td>
          <td>
            <span class="league-badge" :style="{ '--league-color': positionColor(p.position) }">{{ p.position }}</span>
          </td>
          <td>
            <span v-if="p.nationality" class="muted">{{ countryName(p.nationality) }}</span>
            <span v-else class="muted">—</span>
          </td>
          <td>
            <router-link v-if="p.teamId" :to="`/teams/${p.teamId}`" class="team">
              <img v-if="teamsById[p.teamId]?.hasLogo" class="team-logo" :src="teamLogoUrl(p.teamId)" :alt="p.teamCode" />
              <span class="team-code">{{ p.teamCode }}</span>
              <span class="team-name">{{ p.teamName }}</span>
            </router-link>
            <span v-else class="free-agent">Sans équipe</span>
          </td>
          <td class="actions">
            <button type="button" class="btn-secondary" @click="startEdit(p)">Modifier</button>
            <button type="button" class="btn-secondary" @click="startTransfer(p)">
              {{ p.teamId ? 'Transférer' : 'Faire signer' }}
            </button>
            <button v-if="pendingDeleteId === p.id" type="button" class="btn-danger" @click="removePlayer(p)">
              Confirmer
            </button>
            <button v-else type="button" class="btn-secondary" @click="askDelete(p)">Supprimer</button>
          </td>
        </template>
      </tr>
      <tr v-if="transferId === p.id" class="transfer-row">
        <td colspan="5">
          <TransferForm :player="p" :teams="teams" @done="afterTransfer" @cancel="transferId = null" />
        </td>
      </tr>
      </template>
    </tbody>
  </table>
  <p v-else-if="loaded && players.length" class="muted">Aucun joueur ne correspond à la recherche.</p>
  <p v-else-if="loaded">Aucun joueur pour l'instant. Ajoute-les depuis l'onglet Effectif de la fiche d'une équipe.</p>

  <h2>Ajouter un joueur sans équipe</h2>
  <p class="muted">
    Pour un joueur retraité ou libre : ajoute ensuite ses anciennes équipes depuis sa fiche (passages précédents).
  </p>
  <form class="inline" @submit.prevent="createFree">
    <input v-model="newPlayer.pseudo" placeholder="Pseudo (ex: xPeke)" aria-label="Pseudo" required maxlength="50" />
    <CountrySelect v-model="newPlayer.nationality" />
    <select v-model="newPlayer.position" aria-label="Poste">
      <option v-for="o in POSITIONS" :key="o.key" :value="o.key">{{ o.key }}</option>
    </select>
    <button type="submit" :disabled="!newPlayer.pseudo.trim()">Ajouter</button>
  </form>

  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import api, { teamLogoUrl } from '../services/api'
import { useSort } from '../composables/useSort'
import { POSITIONS, positionColor, positionIndex } from '../positions'
import { countryName } from '../countries'
import CountryFlag from '../components/CountryFlag.vue'
import CountrySelect from '../components/CountrySelect.vue'
import TransferForm from '../components/TransferForm.vue'

const players = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')

const search = ref('')
const freeAgentsOnly = ref(false)

const editingId = ref(null)
const editForm = reactive({ pseudo: '', nationality: '', position: 'TOP' })
const pendingDeleteId = ref(null)
const transferId = ref(null)
const newPlayer = reactive({ pseudo: '', nationality: '', position: 'TOP' })
const router = useRouter()

const teamsById = computed(() => Object.fromEntries(teams.value.map(t => [t.id, t])))
const freeAgentCount = computed(() => players.value.filter(p => !p.teamId).length)

function sortKey(player, field) {
  if (field === 'position') return String(positionIndex(player.position))
  if (field === 'team') return player.teamName ?? ''
  if (field === 'nationality') return player.nationality ? countryName(player.nationality) : ''
  return (player[field] ?? '').toString()
}

const { toggleSort, sortArrow, sortList } = useSort('pseudo', sortKey)

const filteredPlayers = computed(() => {
  const q = search.value.trim().toLowerCase()
  return players.value.filter(p => {
    if (freeAgentsOnly.value && p.teamId) return false
    if (!q) return true
    return [p.pseudo, p.teamCode, p.teamName, p.nationality && countryName(p.nationality)].some(v => v?.toLowerCase().includes(q))
  })
})

const sortedPlayers = computed(() => sortList(filteredPlayers.value))

function apiError(e, fallback) {
  return e.response?.data?.error ?? fallback
}

async function load() {
  error.value = ''
  try {
    const [p, t] = await Promise.all([api.getAllPlayers(), api.getTeams()])
    players.value = p
    teams.value = t
  } catch (e) {
    error.value = apiError(e, 'Impossible de charger les joueurs.')
  }
  loaded.value = true
}

function startEdit(player) {
  pendingDeleteId.value = null
  transferId.value = null
  editingId.value = player.id
  editForm.pseudo = player.pseudo
  editForm.nationality = player.nationality ?? ''
  editForm.position = player.position
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(player) {
  error.value = ''
  try {
    await api.updatePlayer(player.id, { ...editForm })
    editingId.value = null
    players.value = await api.getAllPlayers()
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la modification du joueur.')
  }
}

function startTransfer(player) {
  editingId.value = null
  pendingDeleteId.value = null
  transferId.value = player.id
}

async function afterTransfer() {
  transferId.value = null
  players.value = await api.getAllPlayers()
}

function askDelete(player) {
  editingId.value = null
  transferId.value = null
  pendingDeleteId.value = player.id
}

async function removePlayer(player) {
  error.value = ''
  try {
    await api.deletePlayer(player.id)
    pendingDeleteId.value = null
    players.value = await api.getAllPlayers()
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la suppression du joueur.')
  }
}

// Cree le joueur puis ouvre sa fiche, pour y ajouter son historique.
async function createFree() {
  error.value = ''
  try {
    const created = await api.createFreePlayer({ ...newPlayer })
    router.push(`/players/${created.id}`)
  } catch (e) {
    error.value = apiError(e, "Erreur lors de l'ajout du joueur.")
  }
}

onMounted(load)
</script>

<style scoped>
.count {
  font-size: 0.45em;
  padding: 4px 12px;
  border-radius: 999px;
  color: var(--text-muted);
  background: var(--panel);
  border: 1px solid var(--border);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}
.filters input[type="search"] {
  flex: 0 1 340px;
  min-width: 0;
}
.toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9em;
  cursor: pointer;
}
.toggle input {
  accent-color: var(--accent);
}
.pseudo {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.05em;
}
.team {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
}
.team:hover .team-name {
  color: var(--accent);
}
.team-code {
  font-weight: 800;
}
.team-name {
  color: var(--text-muted);
}
.free-agent {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.78em;
  font-weight: 700;
  color: var(--text-dim);
  border: 1px dashed var(--border-strong);
}
.compact {
  width: 140px;
}
.pseudo-cell {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text);
}
.pseudo-cell:hover {
  color: var(--accent);
}
.transfer-row td {
  background: var(--panel-alt);
}
.actions {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}
.btn-danger {
  background: rgba(251, 113, 133, 0.15);
  color: var(--loss);
  box-shadow: inset 0 0 0 1px rgba(251, 113, 133, 0.5);
}
</style>
