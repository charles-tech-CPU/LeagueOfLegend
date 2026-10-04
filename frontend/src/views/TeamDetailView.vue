<template>
  <router-link to="/teams" class="back">← Toutes les équipes</router-link>

  <header class="hero">
    <div class="hero-main">
      <img v-if="team?.hasLogo" class="hero-logo" :src="teamLogoUrl(team.id)" :alt="team.code" />
      <div>
        <span v-if="team" class="code-badge">{{ team.code }}</span>
        <h1>{{ team?.name ?? '...' }}</h1>
        <p v-if="team?.region" class="hero-sub">{{ team.region }}</p>
        <p v-if="formerNames.length" class="hero-sub former">
          Anciennement {{ formerNames.map(n => n.name).join(', ') }}
        </p>
      </div>
    </div>
    <div v-if="loaded" class="hero-stats">
      <div class="stat">
        <span class="stat-value">{{ players.length }}</span>
        <span class="stat-label">joueurs</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ filledPositions }}<span class="stat-total">/{{ POSITIONS.length }}</span></span>
        <span class="stat-label">postes pourvus</span>
      </div>
    </div>
  </header>

  <div class="tabs" role="tablist">
    <button
      v-for="t in tabs"
      :key="t.key"
      type="button"
      role="tab"
      :aria-selected="activeTab === t.key"
      :class="{ active: activeTab === t.key }"
      @click="activeTab = t.key"
    >
      <span class="tab-icon">{{ t.icon }}</span>{{ t.label }}
    </button>
  </div>

  <section v-if="activeTab === 'history'" class="tab-panel">
    <ol class="name-timeline">
      <li v-for="n in names" :key="n.name" :class="{ current: !n.validTo }">
        <span class="name-dot" aria-hidden="true"></span>
        <div>
          <strong>{{ n.name }}</strong>
          <span v-if="n.shortName" class="name-short">{{ n.shortName }}</span>
          <p class="name-period">{{ namePeriod(n) }}</p>
        </div>
      </li>
    </ol>
  </section>

  <section v-if="activeTab === 'roster'" class="tab-panel">
    <div class="roster">
      <article
        v-for="pos in POSITIONS"
        :key="pos.key"
        class="slot"
        :class="{ vacant: !playersByPosition[pos.key].length }"
        :style="{ '--pos-color': pos.color }"
      >
        <header class="slot-head">
          <span class="slot-code">{{ pos.key }}</span>
          <span class="slot-label">{{ pos.label }}</span>
        </header>

        <ul class="slot-players">
          <li v-for="p in playersByPosition[pos.key]" :key="p.id" class="player">
            <form v-if="editingId === p.id" class="player-edit" @submit.prevent="saveEdit(p)">
              <input v-model="editForm.pseudo" aria-label="Pseudo" required maxlength="50" />
              <CountrySelect v-model="editForm.nationality" />
              <select v-model="editForm.position" aria-label="Poste">
                <option v-for="o in POSITIONS" :key="o.key" :value="o.key">{{ o.key }}</option>
              </select>
              <div class="player-actions">
                <button type="submit">Enregistrer</button>
                <button type="button" class="btn-secondary" @click="cancelEdit">Annuler</button>
              </div>
            </form>
            <template v-else>
              <router-link :to="`/players/${p.id}`" class="pseudo">{{ p.pseudo }}</router-link>
              <CountryFlag v-if="p.nationality" :code="p.nationality" class="nationality" />
              <div class="player-actions">
                <button type="button" class="btn-secondary btn-small" @click="startEdit(p)">Modifier</button>
                <button
                  v-if="pendingDeleteId === p.id"
                  type="button"
                  class="btn-danger btn-small"
                  @click="removePlayer(p)"
                >
                  Confirmer
                </button>
                <button v-else type="button" class="btn-secondary btn-small" @click="pendingDeleteId = p.id">
                  Supprimer
                </button>
              </div>
            </template>
          </li>
        </ul>

        <p v-if="!playersByPosition[pos.key].length" class="vacant-label">Poste vacant</p>
        <button type="button" class="btn-secondary btn-small slot-add" @click="prefill(pos.key)">+ Ajouter</button>
      </article>
    </div>

    <h2>Ajouter un joueur</h2>
    <form class="inline" @submit.prevent="submit">
      <input
        ref="pseudoInput"
        v-model="form.pseudo"
        placeholder="Pseudo (ex: Caps)"
        aria-label="Pseudo"
        required
        maxlength="50"
      />
      <CountrySelect v-model="form.nationality" />
      <select v-model="form.position" aria-label="Poste">
        <option v-for="o in POSITIONS" :key="o.key" :value="o.key">{{ o.key }}</option>
      </select>
      <button type="submit">Ajouter</button>
    </form>
  </section>

  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { POSITIONS } from '../positions'
import { fullDay } from '../format'
import CountryFlag from '../components/CountryFlag.vue'
import CountrySelect from '../components/CountrySelect.vue'

const props = defineProps({ id: { type: String, required: true } })

const names = ref([])
// Noms portes avant le nom actuel (le dernier de l'historique est le nom actuel).
const formerNames = computed(() => names.value.filter(n => n.validTo))
const tabs = computed(() => [
  { key: 'roster', label: 'Effectif', icon: '👥' },
  ...(names.value.length > 1 ? [{ key: 'history', label: 'Historique du nom', icon: '📜' }] : [])
])
const activeTab = ref('roster')

const team = ref(null)
const players = ref([])
const loaded = ref(false)
const error = ref('')

const form = reactive({ pseudo: '', nationality: '', position: 'TOP' })
const pseudoInput = ref(null)

const editingId = ref(null)
const editForm = reactive({ pseudo: '', nationality: '', position: 'TOP' })
const pendingDeleteId = ref(null)

const playersByPosition = computed(() => {
  const byPosition = Object.fromEntries(POSITIONS.map(p => [p.key, []]))
  for (const p of players.value) {
    byPosition[p.position]?.push(p)
  }
  return byPosition
})

const filledPositions = computed(() => POSITIONS.filter(p => playersByPosition.value[p.key].length).length)

function namePeriod(n) {
  const from = n.validFrom ? fullDay(n.validFrom) : 'Origine'
  const to = n.validTo ? fullDay(n.validTo) : "aujourd'hui"
  return `${from} → ${to}`
}

function apiError(e, fallback) {
  return e.response?.data?.error ?? fallback
}

async function load() {
  error.value = ''
  try {
    const [t, p, n] = await Promise.all([api.getTeam(props.id), api.getPlayers(props.id), api.getTeamNames(props.id)])
    names.value = n
    team.value = t
    players.value = p
  } catch (e) {
    error.value = apiError(e, "Impossible de charger l'équipe.")
  }
  loaded.value = true
}

function prefill(position) {
  form.position = position
  pseudoInput.value?.focus()
}

async function submit() {
  error.value = ''
  try {
    await api.createPlayer(props.id, { ...form })
    form.pseudo = ''
    form.nationality = ''
    players.value = await api.getPlayers(props.id)
  } catch (e) {
    error.value = apiError(e, "Erreur lors de l'ajout du joueur.")
  }
}

function startEdit(player) {
  pendingDeleteId.value = null
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
    players.value = await api.getPlayers(props.id)
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la modification du joueur.')
  }
}

async function removePlayer(player) {
  error.value = ''
  try {
    await api.deletePlayer(player.id)
    pendingDeleteId.value = null
    players.value = await api.getPlayers(props.id)
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la suppression du joueur.')
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<style scoped>
.back {
  display: inline-block;
  margin-top: 22px;
  font-size: 0.88em;
  color: var(--text-muted);
}
.back:hover {
  color: var(--accent);
}
.hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 14px 0 22px;
  padding: 26px 28px;
  border-radius: 20px;
  overflow: hidden;
  background:
    radial-gradient(500px 220px at 0% 0%, rgba(129, 140, 248, 0.24), transparent 70%),
    linear-gradient(135deg, var(--panel), var(--bg-elevated));
  border: 1px solid var(--border-strong);
  box-shadow: var(--shadow);
}
.hero::after {
  content: "";
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  background: var(--accent-grad);
}
.hero-main {
  display: flex;
  align-items: center;
  gap: 20px;
}
.hero-logo {
  width: 76px;
  height: 76px;
  object-fit: contain;
  padding: 10px;
  border-radius: 18px;
  background: var(--logo-bg);
}
.hero h1 {
  margin: 8px 0 4px;
}
.hero-sub {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92em;
}
.code-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 800;
  font-size: 0.8em;
  color: var(--accent);
  background: rgba(34, 211, 238, 0.12);
  border: 1px solid rgba(34, 211, 238, 0.4);
}
.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 110px;
  padding: 12px 16px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
}
.stat-value {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.5em;
  line-height: 1.1;
}
.stat-total {
  font-size: 0.6em;
  color: var(--text-dim);
}
.stat-label {
  font-size: 0.74em;
  color: var(--text-muted);
}
.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 5px;
  margin-bottom: 22px;
  border-radius: 999px;
  background: var(--panel-alt);
  border: 1px solid var(--border);
}
.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: none;
  color: var(--text-muted);
  font-weight: 600;
  font-size: 0.9em;
  padding: 8px 16px;
  box-shadow: none;
}
.tabs button:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.05);
  filter: none;
}
.tabs button.active {
  color: #0a0c18;
  background: var(--accent-grad);
  box-shadow: 0 6px 18px -8px rgba(129, 140, 248, 0.9);
}
.tab-panel {
  animation: fade-in 0.25s ease;
}
@keyframes fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: none; }
}

.former {
  margin-top: 4px;
  font-size: 0.82em;
  color: var(--text-dim);
}

/* ---------- Historique du nom ---------- */
.name-timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 26px;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.name-timeline::before {
  content: "";
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: linear-gradient(180deg, var(--border-strong), var(--accent));
}
.name-timeline li {
  position: relative;
  padding: 12px 16px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
}
.name-timeline li.current {
  border-color: rgba(34, 211, 238, 0.4);
  box-shadow: var(--glow);
}
.name-dot {
  position: absolute;
  left: -26px;
  top: 16px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--bg);
  border: 2px solid var(--border-strong);
}
.name-timeline li.current .name-dot {
  border-color: var(--accent);
  background: var(--accent);
}
.name-short {
  margin-left: 8px;
  font-size: 0.8em;
  color: var(--text-muted);
}
.name-period {
  margin: 4px 0 0;
  font-size: 0.85em;
  color: var(--text-muted);
}

/* ---------- Effectif : une carte par poste ---------- */
.roster {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.slot {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 190px;
  padding: 16px;
  border-radius: var(--radius);
  overflow: hidden;
  background:
    radial-gradient(220px 120px at 100% 0%, color-mix(in srgb, var(--pos-color) 20%, transparent), transparent 70%),
    var(--panel);
  border: 1px solid color-mix(in srgb, var(--pos-color) 32%, var(--border));
  box-shadow: var(--shadow);
}
.slot::before {
  content: "";
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, var(--pos-color), transparent);
}
.slot.vacant {
  border-style: dashed;
  background: var(--panel-alt);
}
.slot-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.slot-code {
  font-family: "Outfit", sans-serif;
  font-weight: 900;
  font-size: 1.5em;
  letter-spacing: 0.02em;
  color: var(--pos-color);
  text-shadow: 0 0 18px color-mix(in srgb, var(--pos-color) 55%, transparent);
}
.slot-label {
  font-size: 0.72em;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.slot-players {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.player {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}
.player:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.pseudo {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.2em;
  color: var(--text);
  overflow-wrap: anywhere;
}
a.pseudo:hover {
  color: var(--accent);
}
.nationality {
  font-size: 0.95em;
}
.player-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.player-edit {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.player-edit input,
.player-edit select {
  width: 100%;
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
.vacant-label {
  margin: 0;
  color: var(--text-dim);
  font-style: italic;
  font-size: 0.9em;
}
.slot-add {
  margin-top: auto;
  align-self: flex-start;
}
@media (max-width: 640px) {
  .hero {
    padding: 20px;
  }
  .hero-logo {
    width: 56px;
    height: 56px;
  }
}
</style>
