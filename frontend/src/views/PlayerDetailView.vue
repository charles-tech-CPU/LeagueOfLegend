<template>
  <router-link to="/players" class="back">← Tous les joueurs</router-link>

  <header class="hero" :style="{ '--pos-color': positionColor(player?.position) }">
    <div class="hero-main">
      <span v-if="player?.nationality && isKnownCountry(player.nationality)" class="hero-flag">
        <span class="fi" :class="`fi-${player.nationality.toLowerCase()}`"></span>
      </span>
      <div>
        <span v-if="player" class="league-badge" :style="{ '--league-color': positionColor(player.position) }">
          {{ player.position }}
        </span>
        <h1>{{ player?.pseudo ?? '...' }}</h1>
        <p v-if="player" class="hero-sub">
          <template v-if="player.nationality">{{ countryName(player.nationality) }} · </template>
          <router-link v-if="player.teamId" :to="`/teams/${player.teamId}`">{{ player.teamName }}</router-link>
          <span v-else>Sans équipe</span>
        </p>
      </div>
    </div>
    <div v-if="loaded" class="hero-stats">
      <div class="stat">
        <span class="stat-value">{{ career.wins }}<span class="stat-total">-{{ career.losses }}</span></span>
        <span class="stat-label">bilan en carrière{{ career.rate !== null ? ` · ${career.rate} %` : '' }}</span>
      </div>
      <div class="stat" :class="{ 'champion-stat': career.titles }">
        <span class="stat-value">{{ career.titles ? `🏆 ${career.titles}` : 0 }}</span>
        <span class="stat-label">{{ career.titles > 1 ? 'titres' : 'titre' }}</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ career.teams }}</span>
        <span class="stat-label">{{ career.teams > 1 ? 'équipes' : 'équipe' }}</span>
      </div>
    </div>
  </header>

  <div v-if="player" class="toolbar">
    <div class="tabs" role="tablist">
      <button type="button" role="tab" aria-selected="true" class="active">
        <span class="tab-icon">🧭</span>Parcours
      </button>
    </div>
    <button v-if="!transferring" type="button" class="btn-secondary" @click="transferring = true">
      {{ player.teamId ? 'Transférer' : 'Faire signer' }}
    </button>
  </div>
  <TransferForm
    v-if="transferring && player"
    class="transfer-panel"
    :player="player"
    :teams="teams"
    @done="afterTransfer"
    @cancel="transferring = false"
  />

  <section class="tab-panel">
    <p class="hint">
      Les résultats d'un passage sont ceux de <strong>l'équipe pendant la période</strong> : on considère que le joueur a
      joué tous ses matchs. Une date d'arrivée inconnue compte tous les matchs de l'équipe jusqu'au départ.
    </p>

    <ol v-if="stints.length" class="timeline">
      <li v-for="s in stints" :key="s.id" class="stint" :class="{ current: s.current }">
        <span class="stint-dot" aria-hidden="true"></span>
        <article class="stint-card">
          <header class="stint-head">
            <router-link :to="`/teams/${s.teamId}`" class="stint-team">
              <img v-if="s.teamHasLogo" class="team-logo" :src="teamLogoUrl(s.teamId)" :alt="s.teamCode" />
              <span class="stint-code">{{ s.teamCode }}</span>
              <span class="stint-name">{{ s.teamName }}</span>
            </router-link>
            <span v-if="s.current" class="current-badge">Équipe actuelle</span>
            <span class="stint-period">{{ period(s) }}</span>
          </header>

          <form v-if="editingId === s.id" class="stint-edit" @submit.prevent="saveStint(s)">
            <select v-model="stintForm.teamId" aria-label="Équipe" :disabled="s.current">
              <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }} · {{ t.name }}</option>
            </select>
            <label>Arrivée <input v-model="stintForm.startDate" type="date" aria-label="Date d'arrivée" /></label>
            <label v-if="!s.current">
              Départ <input v-model="stintForm.endDate" type="date" aria-label="Date de départ" required />
            </label>
            <button type="submit">Enregistrer</button>
            <button type="button" class="btn-secondary" @click="editingId = null">Annuler</button>
          </form>

          <div class="stint-stats">
            <div class="mini-stat">
              <span class="mini-value">
                <span class="win">{{ s.wins }}</span>-<span class="loss">{{ s.losses }}</span>
              </span>
              <span class="mini-label">séries</span>
            </div>
            <div class="mini-stat">
              <span class="mini-value">{{ winRate(s) ?? '—' }}<template v-if="winRate(s) !== null"> %</template></span>
              <span class="mini-label">victoires</span>
              <span class="progress"><span :style="{ width: `${winRate(s) ?? 0}%` }"></span></span>
            </div>
            <div class="mini-stat">
              <span class="mini-value">{{ s.gamesWon }}-{{ s.gamesLost }}</span>
              <span class="mini-label">manches</span>
            </div>
            <div class="mini-stat" :class="{ 'champion-stat': s.titles }">
              <span class="mini-value">{{ s.titles ? `🏆 ${s.titles}` : 0 }}</span>
              <span class="mini-label">{{ s.titles > 1 ? 'titres' : 'titre' }}</span>
            </div>
          </div>

          <ul v-if="s.competitions.length" class="competitions">
            <li v-for="c in s.competitions" :key="c.competitionId">
              <router-link
                :to="`/competitions/${c.competitionId}`"
                class="competition"
                :class="{ champion: c.champion }"
                :style="{ '--league-color': leagueColor(c.code) }"
              >
                <span v-if="c.champion">🏆</span>
                <span class="competition-name">{{ c.name }}</span>
                <span class="competition-record">{{ c.wins }}-{{ c.losses }}</span>
              </router-link>
            </li>
          </ul>
          <p v-else class="muted no-match">Aucun match joué par l'équipe sur cette période.</p>

          <details v-if="s.matches.length" class="matches">
            <summary>Voir les {{ s.matches.length }} matchs</summary>
            <div class="matches-scroll">
            <table>
              <tbody>
                <tr v-for="m in s.matches" :key="m.id">
                  <td class="muted">{{ fullDay(m.date) }}</td>
                  <td>
                    <span class="league-badge" :style="{ '--league-color': leagueColor(m.competitionCode) }">
                      {{ m.competitionCode }}
                    </span>
                  </td>
                  <td class="muted">{{ m.roundLabel }}</td>
                  <td>vs <strong>{{ opponent(s, m) }}</strong></td>
                  <td>
                    <span class="score" :class="result(s, m).won ? 'win' : 'loss'">
                      {{ result(s, m).won ? 'V' : 'D' }} {{ result(s, m).ours }}-{{ result(s, m).theirs }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
            </div>
          </details>

          <div class="stint-actions">
            <button type="button" class="btn-secondary btn-small" @click="startEdit(s)">Modifier les dates</button>
            <button
              v-if="pendingDeleteId === s.id"
              type="button"
              class="btn-danger btn-small"
              @click="removeStint(s)"
            >
              Confirmer la suppression
            </button>
            <button v-else type="button" class="btn-secondary btn-small" @click="pendingDeleteId = s.id">
              Supprimer
            </button>
          </div>
        </article>
      </li>
    </ol>
    <p v-else-if="loaded" class="muted">Aucun passage en équipe pour l'instant.</p>

    <h2>Ajouter un passage précédent</h2>
    <form class="inline" @submit.prevent="addStint">
      <select v-model="newStint.teamId" aria-label="Équipe" required>
        <option :value="null" disabled>Équipe</option>
        <option v-for="t in teams" :key="t.id" :value="t.id">{{ t.code }} · {{ t.name }}</option>
      </select>
      <label class="date-field">Arrivée <input v-model="newStint.startDate" type="date" aria-label="Date d'arrivée" /></label>
      <label class="date-field">
        Départ <input v-model="newStint.endDate" type="date" aria-label="Date de départ" required />
      </label>
      <button type="submit" :disabled="!newStint.teamId || !newStint.endDate">Ajouter</button>
    </form>
  </section>

  <p v-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { positionColor } from '../positions'
import { countryName, isKnownCountry } from '../countries'
import { leagueColor } from '../leagueColors'
import { fullDay } from '../format'
import TransferForm from '../components/TransferForm.vue'

const props = defineProps({ id: { type: String, required: true } })

const player = ref(null)
const stints = ref([])
const teams = ref([])
const loaded = ref(false)
const error = ref('')
const transferring = ref(false)

const editingId = ref(null)
const stintForm = reactive({ teamId: null, startDate: '', endDate: '' })
const pendingDeleteId = ref(null)
const newStint = reactive({ teamId: null, startDate: '', endDate: '' })

const career = computed(() => {
  const wins = stints.value.reduce((sum, s) => sum + s.wins, 0)
  const losses = stints.value.reduce((sum, s) => sum + s.losses, 0)
  return {
    wins,
    losses,
    rate: wins + losses ? Math.round((100 * wins) / (wins + losses)) : null,
    titles: stints.value.reduce((sum, s) => sum + s.titles, 0),
    teams: new Set(stints.value.map(s => s.teamId)).size
  }
})

function apiError(e, fallback) {
  return e.response?.data?.error ?? fallback
}

function period(stint) {
  const from = stint.startDate ? fullDay(stint.startDate) : 'Arrivée inconnue'
  const to = stint.endDate ? fullDay(stint.endDate) : "aujourd'hui"
  return `${from} → ${to}`
}

function winRate(stint) {
  const played = stint.wins + stint.losses
  return played ? Math.round((100 * stint.wins) / played) : null
}

function result(stint, match) {
  const isTeam1 = match.team1Id === stint.teamId
  const ours = isTeam1 ? match.score1 : match.score2
  const theirs = isTeam1 ? match.score2 : match.score1
  return { ours, theirs, won: ours > theirs }
}

function opponent(stint, match) {
  return match.team1Id === stint.teamId ? match.team2Code : match.team1Code
}

async function load() {
  error.value = ''
  try {
    const [p, s, t] = await Promise.all([api.getPlayer(props.id), api.getPlayerStints(props.id), api.getTeams()])
    player.value = p
    stints.value = s
    teams.value = t
  } catch (e) {
    error.value = apiError(e, 'Impossible de charger le joueur.')
  }
  loaded.value = true
}

async function reload() {
  const [p, s] = await Promise.all([api.getPlayer(props.id), api.getPlayerStints(props.id)])
  player.value = p
  stints.value = s
}

async function afterTransfer() {
  transferring.value = false
  await reload()
}

function startEdit(stint) {
  pendingDeleteId.value = null
  editingId.value = stint.id
  stintForm.teamId = stint.teamId
  stintForm.startDate = stint.startDate ?? ''
  stintForm.endDate = stint.endDate ?? ''
}

function stintPayload(form) {
  return { teamId: form.teamId, startDate: form.startDate || null, endDate: form.endDate || null }
}

async function saveStint(stint) {
  error.value = ''
  try {
    await api.updateStint(stint.id, stintPayload(stintForm))
    editingId.value = null
    await reload()
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la modification du passage.')
  }
}

async function removeStint(stint) {
  error.value = ''
  try {
    await api.deleteStint(stint.id)
    pendingDeleteId.value = null
    await reload()
  } catch (e) {
    error.value = apiError(e, 'Erreur lors de la suppression du passage.')
  }
}

async function addStint() {
  error.value = ''
  try {
    await api.addStint(props.id, stintPayload(newStint))
    Object.assign(newStint, { teamId: null, startDate: '', endDate: '' })
    await reload()
  } catch (e) {
    error.value = apiError(e, "Erreur lors de l'ajout du passage.")
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
    radial-gradient(500px 220px at 0% 0%, color-mix(in srgb, var(--pos-color) 24%, transparent), transparent 70%),
    linear-gradient(135deg, var(--panel), var(--bg-elevated));
  border: 1px solid color-mix(in srgb, var(--pos-color) 30%, var(--border));
  box-shadow: var(--shadow);
}
.hero::after {
  content: "";
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  background: linear-gradient(90deg, var(--pos-color), transparent);
}
.hero-main {
  display: flex;
  align-items: center;
  gap: 20px;
}
.hero-flag {
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
}
.hero-flag .fi {
  width: 48px;
  line-height: 36px;
  border-radius: 5px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12);
}
.hero h1 {
  margin: 8px 0 4px;
}
.hero-sub {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.92em;
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
.champion-stat {
  border-color: rgba(245, 196, 81, 0.45);
  background: rgba(245, 196, 81, 0.08);
}
.champion-stat .stat-value,
.champion-stat .mini-value {
  color: var(--gold-bright);
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}
.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 5px;
  border-radius: 999px;
  background: var(--panel-alt);
  border: 1px solid var(--border);
}
.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
  font-size: 0.9em;
  padding: 8px 16px;
}
.tabs button.active {
  color: #0a0c18;
  background: var(--accent-grad);
  box-shadow: 0 6px 18px -8px rgba(129, 140, 248, 0.9);
}
.transfer-panel {
  margin-bottom: 22px;
}
.tab-panel {
  animation: fade-in 0.25s ease;
}
@keyframes fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: none; }
}
.hint {
  font-size: 0.86em;
  color: var(--text-muted);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.2);
  margin: 0 0 20px;
}
.hint strong {
  color: var(--text);
}

/* ---------- Parcours : frise des passages ---------- */
.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 26px;
  position: relative;
}
.timeline::before {
  content: "";
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: linear-gradient(180deg, var(--accent), var(--border-strong));
}
.stint {
  position: relative;
  margin-bottom: 18px;
}
.stint-dot {
  position: absolute;
  left: -26px;
  top: 22px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--bg);
  border: 2px solid var(--border-strong);
}
.stint.current .stint-dot {
  border-color: var(--accent);
  background: var(--accent);
  box-shadow: 0 0 14px var(--accent);
}
.stint-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}
.stint.current .stint-card {
  border-color: rgba(34, 211, 238, 0.4);
  box-shadow: var(--glow);
}
.stint-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}
.stint-team {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text);
}
.stint-team:hover .stint-name {
  color: var(--accent);
}
.stint-code {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.25em;
}
.stint-name {
  color: var(--text-muted);
}
.current-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.72em;
  font-weight: 700;
  color: var(--accent);
  background: rgba(34, 211, 238, 0.12);
  border: 1px solid rgba(34, 211, 238, 0.4);
}
.stint-period {
  margin-left: auto;
  font-size: 0.85em;
  color: var(--text-muted);
}
.stint-edit {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.stint-edit label,
.date-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.88em;
  color: var(--text-muted);
}
.stint-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.mini-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 96px;
  padding: 9px 13px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
}
.mini-value {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.15em;
}
.mini-label {
  font-size: 0.72em;
  color: var(--text-muted);
}
.win {
  color: var(--win);
}
.loss {
  color: var(--loss);
}
.progress {
  display: block;
  height: 4px;
  margin-top: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  overflow: hidden;
}
.progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent-grad);
}
.competitions {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.competition {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 0.85em;
  color: var(--text);
  background: color-mix(in srgb, var(--league-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--league-color) 40%, transparent);
}
.competition:hover {
  color: var(--text);
  background: color-mix(in srgb, var(--league-color) 22%, transparent);
}
.competition.champion {
  border-color: rgba(245, 196, 81, 0.7);
  box-shadow: 0 0 14px -4px rgba(245, 196, 81, 0.7);
}
.competition-name {
  font-weight: 700;
}
.competition-record {
  color: var(--text-muted);
  font-weight: 600;
}
.no-match {
  margin: 0;
  font-size: 0.9em;
}
.matches summary {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 0.88em;
}
.matches summary:hover {
  color: var(--accent);
}
.matches-scroll {
  overflow-x: auto;
}
.matches table {
  margin: 10px 0 0;
  box-shadow: none;
}
.matches td {
  padding: 8px 12px;
  font-size: 0.9em;
}
.score {
  font-weight: 800;
}
.stint-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
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
@media (max-width: 640px) {
  .hero {
    padding: 20px;
  }
  .hero-flag {
    width: 56px;
    height: 56px;
  }
  .stint-period {
    margin-left: 0;
    flex-basis: 100%;
  }
}
</style>
