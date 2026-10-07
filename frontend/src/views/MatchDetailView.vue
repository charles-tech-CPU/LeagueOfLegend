<template>
  <router-link v-if="match" :to="`/competitions/${match.competitionId}`" class="back">
    ← {{ match.competitionCode }}
  </router-link>

  <header v-if="match" class="match-hero">
    <p class="meta">
      {{ fullDay(match.date) }}<template v-if="match.time"> · {{ match.time.slice(0, 5) }}</template>
      · {{ match.roundLabel }} · {{ match.bestOf }}
    </p>
    <div class="versus">
      <span v-for="(side, i) in sides" :key="side.slot" class="side" :class="{ right: i === 1, winner: seriesWinner === side.teamId }">
        <span class="logo-box">
          <img v-if="hasLogo(side.teamId)" :src="teamLogoUrl(side.teamId)" :alt="side.code" />
          <span v-else>{{ (side.code ?? '?').slice(0, 2) }}</span>
        </span>
        <span class="side-name">
          <router-link v-if="side.teamId" :to="`/teams/${side.teamId}`" class="code">{{ side.code }}</router-link>
          <span v-else class="code">À déterminer</span>
          <span class="full">{{ side.name }}</span>
        </span>
      </span>
      <span class="series-score">
        <template v-if="match.status === 'COMPLETED'">{{ match.score1 }} – {{ match.score2 }}</template>
        <template v-else>vs</template>
      </span>
    </div>
  </header>

  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="loaded && match">
    <p v-if="!match.team1Id || !match.team2Id" class="hint">
      Les deux équipes doivent être connues pour saisir les détails du match.
    </p>

    <template v-else>
      <p class="hint">
        Tout est <strong>facultatif</strong> : remplis seulement ce que tu connais. Une ligne n'est
        enregistrée que si son champion est renseigné ; le K/D/A peut rester vide.
      </p>

      <div class="series-mvp">
        <label>
          <span class="label">MVP de la série</span>
          <select v-model="seriesMvp">
            <option :value="null">Non renseigné</option>
            <option v-for="p in seriesMvpOptions" :key="p.id" :value="p.id">{{ p.label }}</option>
          </select>
        </label>
      </div>

      <div class="game-tabs" role="tablist">
        <button
          v-for="g in games"
          :key="g.gameNumber"
          type="button"
          role="tab"
          :aria-selected="activeGame === g.gameNumber"
          :class="{ active: activeGame === g.gameNumber }"
          @click="activeGame = g.gameNumber"
        >
          Game {{ g.gameNumber }}<span v-if="isFilled(g)" class="filled-dot" title="Renseignée"></span>
        </button>
      </div>

      <section v-for="g in games" v-show="activeGame === g.gameNumber" :key="g.gameNumber" class="game-panel">
        <div class="game-head">
          <label>
            <span class="label">Vainqueur</span>
            <select v-model="g.winnerTeamId">
              <option :value="null">Non renseigné</option>
              <option v-for="side in sides" :key="side.slot" :value="side.teamId">{{ side.code }}</option>
            </select>
          </label>
          <label>
            <span class="label">MVP de la game</span>
            <select v-model="g.mvpPlayerId">
              <option :value="null">Non renseigné</option>
              <option v-for="p in gameMvpOptions(g)" :key="p.id" :value="p.id">{{ p.label }}</option>
            </select>
          </label>
        </div>

        <div class="lineups">
          <div v-for="side in sides" :key="side.slot" class="lineup" :class="{ won: g.winnerTeamId === side.teamId }">
            <h3>{{ side.code }}<span v-if="g.winnerTeamId === side.teamId" class="win-tag">Victoire</span></h3>
            <p v-if="!rosterOf(side.teamId).length" class="muted small">
              Aucun joueur dans l'effectif {{ season }} de {{ side.code }} :
              <router-link :to="`/teams/${side.teamId}`">ajoute-les depuis la page de l'équipe</router-link>.
            </p>
            <table class="lines">
              <thead>
                <tr>
                  <th>Poste</th>
                  <th>Joueur</th>
                  <th>Champion</th>
                  <th class="num">K</th>
                  <th class="num">D</th>
                  <th class="num">A</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="pos in POSITIONS" :key="pos.key">
                  <td>
                    <span class="pos" :style="{ '--pos': pos.color }">{{ pos.key }}</span>
                  </td>
                  <td>
                    <select v-model="g.lines[side.teamId][pos.key].playerId" :aria-label="`Joueur ${pos.key} ${side.code}`">
                      <option :value="null">—</option>
                      <option v-for="p in rosterOf(side.teamId)" :key="p.id" :value="p.id">{{ p.pseudo }}</option>
                    </select>
                  </td>
                  <td>
                    <input
                      v-model="g.lines[side.teamId][pos.key].champion"
                      class="champion-input"
                      :class="{ invalid: isUnknownChampion(g.lines[side.teamId][pos.key].champion) }"
                      list="champion-list"
                      placeholder="Champion"
                      :aria-label="`Champion ${pos.key} ${side.code}`"
                    />
                  </td>
                  <td v-for="stat in ['kills', 'deaths', 'assists']" :key="stat" class="num">
                    <input
                      v-model.number="g.lines[side.teamId][pos.key][stat]"
                      class="kda-input"
                      type="number"
                      min="0"
                      :aria-label="`${stat} ${pos.key} ${side.code}`"
                    />
                  </td>
                </tr>
              </tbody>
            </table>

            <div class="bans">
              <span class="label">Bans {{ side.code }}</span>
              <div class="ban-list">
                <span v-for="(ban, i) in g.bans[side.teamId]" :key="i" class="ban-row">
                  <input
                    v-model="g.bans[side.teamId][i]"
                    class="champion-input ban-input"
                    :class="{ invalid: isUnknownChampion(ban) }"
                    list="champion-list"
                    placeholder="Champion banni"
                    :aria-label="`Ban ${i + 1} ${side.code}`"
                  />
                  <button type="button" class="ban-remove" title="Retirer ce ban" @click="removeBan(g, side.teamId, i)">✕</button>
                </span>
                <button type="button" class="ban-add" @click="addBan(g, side.teamId)">+ Ban</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <datalist id="champion-list">
        <option v-for="c in CHAMPIONS" :key="c" :value="c"></option>
      </datalist>

      <div class="actions">
        <button type="button" :disabled="saving" @click="save">Enregistrer les détails</button>
        <span v-if="savedMessage" class="saved">{{ savedMessage }}</span>
      </div>
    </template>
  </template>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { POSITIONS } from '../positions'
import { CHAMPIONS, championName } from '../champions'
import { fullDay } from '../format'

const props = defineProps({ id: { type: String, required: true } })

const MAX_GAMES = { BO1: 1, BO2: 2, BO3: 3, BO5: 5 }
const STATS = ['kills', 'deaths', 'assists']

const match = ref(null)
const teams = ref([])
const rosters = ref({})
const games = ref([])
const seriesMvp = ref(null)
const activeGame = ref(1)
const loaded = ref(false)
const saving = ref(false)
const error = ref('')
const savedMessage = ref('')

const season = computed(() => match.value ? Number(match.value.date.slice(0, 4)) : null)

const sides = computed(() => {
  const m = match.value
  if (!m) return []
  return [
    { slot: 1, teamId: m.team1Id, code: m.team1Code, name: m.team1Name },
    { slot: 2, teamId: m.team2Id, code: m.team2Code, name: m.team2Name }
  ]
})

const seriesWinner = computed(() => {
  const m = match.value
  if (!m || m.status !== 'COMPLETED' || m.score1 === m.score2) return null
  return m.score1 > m.score2 ? m.team1Id : m.team2Id
})

const playersById = computed(() => {
  const map = new Map()
  for (const list of Object.values(rosters.value)) {
    for (const p of list) map.set(p.id, p)
  }
  return map
})

// MVP de la serie : un joueur aligne dans une des games, a defaut tout l'effectif des deux equipes.
const seriesMvpOptions = computed(() => {
  const ids = new Set(games.value.flatMap(selectedPlayers))
  const pool = ids.size ? [...ids] : [...playersById.value.keys()]
  return pool.map(option).sort((a, b) => a.label.localeCompare(b.label))
})

function apiError(e, fallback) {
  return e.response?.data?.error ?? fallback
}

function hasLogo(teamId) {
  return teams.value.find(t => t.id === teamId)?.hasLogo ?? false
}

function rosterOf(teamId) {
  return rosters.value[teamId] ?? []
}

function option(playerId) {
  const p = playersById.value.get(playerId)
  if (!p) return { id: playerId, label: `#${playerId}` }
  const team = sides.value.find(s => s.teamId === p.teamOfMatch)
  return { id: playerId, label: team ? `${p.pseudo} (${team.code})` : p.pseudo }
}

function selectedPlayers(game) {
  return Object.values(game.lines).flatMap(byPos => Object.values(byPos))
    .map(line => line.playerId)
    .filter(id => id != null)
}

function gameMvpOptions(game) {
  return selectedPlayers(game).map(option)
}

function isUnknownChampion(value) {
  return !!value?.trim() && !championName(value)
}

function isBlank(value) {
  return value === '' || value == null
}

function emptyLine() {
  return { playerId: null, champion: '', kills: '', deaths: '', assists: '' }
}

// Nombre de bans proposes par defaut par equipe (format habituel) : on peut en ajouter
// ou en retirer librement, rien n'impose cette limite.
const DEFAULT_BANS = 5

function isFilled(game) {
  return game.winnerTeamId != null || game.mvpPlayerId != null ||
    Object.values(game.lines).some(byPos => Object.values(byPos).some(l => l.champion.trim())) ||
    Object.values(game.bans).some(list => list.some(c => c.trim()))
}

function addBan(game, teamId) {
  game.bans[teamId].push('')
}

function removeBan(game, teamId, index) {
  game.bans[teamId].splice(index, 1)
}

/** Formulaire d'une game : chaque poste pre-rempli avec le joueur de l'effectif a ce poste. */
function buildGame(gameNumber, saved) {
  const lines = {}
  const bans = {}
  for (const side of sides.value) {
    lines[side.teamId] = {}
    for (const pos of POSITIONS) {
      const atPosition = rosterOf(side.teamId).filter(p => p.position === pos.key)
      lines[side.teamId][pos.key] = { ...emptyLine(), playerId: atPosition.length === 1 ? atPosition[0].id : null }
    }
    const savedBans = (saved?.bans ?? []).filter(b => b.teamId === side.teamId).map(b => b.champion)
    bans[side.teamId] = savedBans.length
      ? [...savedBans, ...Array(Math.max(0, DEFAULT_BANS - savedBans.length)).fill('')]
      : Array(DEFAULT_BANS).fill('')
  }
  for (const l of saved?.players ?? []) {
    lines[l.teamId][l.position] = {
      playerId: l.playerId,
      champion: l.champion,
      kills: l.kills ?? '',
      deaths: l.deaths ?? '',
      assists: l.assists ?? ''
    }
  }
  return {
    gameNumber,
    winnerTeamId: saved?.winnerTeamId ?? null,
    mvpPlayerId: saved?.mvpPlayerId ?? null,
    lines,
    bans
  }
}

function fillForm(details) {
  const m = match.value
  const played = m.status === 'COMPLETED' ? m.score1 + m.score2 : 0
  const savedNumbers = details.games.map(g => g.gameNumber)
  const count = Math.max(played || MAX_GAMES[m.bestOf] || 1, ...savedNumbers, 1)
  games.value = Array.from({ length: count }, (_, i) =>
    buildGame(i + 1, details.games.find(g => g.gameNumber === i + 1)))
  seriesMvp.value = details.mvpPlayerId
  if (!games.value.some(g => g.gameNumber === activeGame.value)) activeGame.value = 1
}

/** Ajoute aux effectifs les joueurs deja saisis qui n'y sont plus (transfert depuis). */
function addSavedPlayers(details) {
  for (const g of details.games) {
    for (const l of g.players) {
      const roster = rosters.value[l.teamId] ?? (rosters.value[l.teamId] = [])
      if (!roster.some(p => p.id === l.playerId)) {
        roster.push({ id: l.playerId, pseudo: l.pseudo, position: l.position, teamOfMatch: l.teamId })
      }
    }
  }
}

/** Ligne d'un poste prete a envoyer, null si vide ; leve une erreur si elle est incomplete. */
function linePayload(g, side, pos) {
  const line = g.lines[side.teamId][pos.key]
  const champion = line.champion.trim()
  const where = `Game ${g.gameNumber} : `
  if (!champion) {
    if (STATS.some(s => !isBlank(line[s]))) throw new Error(`${where}champion manquant pour ${side.code} ${pos.key}`)
    return null
  }
  const official = championName(champion)
  if (!official) throw new Error(`${where}champion inconnu « ${champion} »`)
  if (line.playerId == null) throw new Error(`${where}joueur manquant pour ${side.code} ${pos.key}`)
  return {
    playerId: line.playerId,
    teamId: side.teamId,
    position: pos.key,
    champion: official,
    ...Object.fromEntries(STATS.map(s => [s, isBlank(line[s]) ? null : line[s]]))
  }
}

/** Bans d'une equipe prets a envoyer ; leve une erreur si un champion saisi est inconnu. */
function bansPayload(g, side) {
  return g.bans[side.teamId]
    .map(c => c.trim())
    .filter(c => c)
    .map(champion => {
      const official = championName(champion)
      if (!official) throw new Error(`Game ${g.gameNumber} : champion banni inconnu « ${champion} »`)
      return { teamId: side.teamId, champion: official }
    })
}

function toPayload() {
  const payloadGames = games.value.map(g => {
    const players = sides.value
      .flatMap(side => POSITIONS.map(pos => linePayload(g, side, pos)))
      .filter(line => line != null)
    const bans = sides.value.flatMap(side => bansPayload(g, side))
    return { gameNumber: g.gameNumber, winnerTeamId: g.winnerTeamId, mvpPlayerId: g.mvpPlayerId, players, bans }
  })
  return {
    mvpPlayerId: seriesMvp.value,
    games: payloadGames.filter(g => g.winnerTeamId != null || g.mvpPlayerId != null || g.players.length || g.bans.length)
  }
}

async function save() {
  error.value = ''
  savedMessage.value = ''
  let payload
  try {
    payload = toPayload()
  } catch (e) {
    error.value = e.message
    return
  }
  saving.value = true
  try {
    const details = await api.saveMatchDetails(match.value.id, payload)
    addSavedPlayers(details)
    fillForm(details)
    savedMessage.value = payload.games.length || payload.mvpPlayerId ? 'Détails enregistrés ✓' : 'Détails effacés'
  } catch (e) {
    error.value = apiError(e, "Impossible d'enregistrer les détails")
  } finally {
    saving.value = false
  }
}

async function load() {
  try {
    const [m, details, allTeams] = await Promise.all([
      api.getMatch(props.id),
      api.getMatchDetails(props.id),
      api.getTeams()
    ])
    match.value = m
    teams.value = allTeams
    const year = Number(m.date.slice(0, 4))
    const teamIds = [m.team1Id, m.team2Id].filter(id => id != null)
    const lists = await Promise.all(teamIds.map(id => api.getPlayers(id, year)))
    // Un role swap fait apparaitre le joueur deux fois dans l'effectif de la saison
    rosters.value = Object.fromEntries(teamIds.map((id, i) => [id, lists[i]
      .filter((p, j, all) => all.findIndex(q => q.id === p.id) === j)
      .map(p => ({ ...p, teamOfMatch: id }))]))
    if (teamIds.length === 2) {
      addSavedPlayers(details)
      fillForm(details)
    }
  } catch (e) {
    error.value = apiError(e, 'Impossible de charger le match')
  } finally {
    loaded.value = true
  }
}

onMounted(load)
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
.match-hero {
  margin: 14px 0 22px;
  padding: 22px 26px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--panel), var(--bg-elevated));
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
}
.meta {
  margin: 0 0 14px;
  font-size: 0.85em;
  color: var(--text-muted);
}
.versus {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 18px;
}
.side {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.side.right {
  grid-column: 3;
  flex-direction: row-reverse;
  text-align: right;
}
.series-score {
  grid-column: 2;
  grid-row: 1;
  font-family: "Outfit", sans-serif;
  font-weight: 900;
  font-size: 2em;
  font-variant-numeric: tabular-nums;
}
.side-name {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.code {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.3em;
  color: var(--text);
}
.side.winner .code {
  color: var(--gold-bright);
}
.full {
  font-size: 0.82em;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logo-box {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--logo-bg);
  font-weight: 800;
  color: var(--text-muted);
}
.logo-box img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}
.hint {
  font-size: 0.86em;
  color: var(--text-muted);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.2);
  margin: 0 0 18px;
}
.hint strong {
  color: var(--text);
}
.label {
  display: block;
  font-size: 0.72em;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-dim);
  margin-bottom: 5px;
}
.series-mvp {
  margin-bottom: 18px;
}
.game-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 5px;
  margin-bottom: 16px;
  border-radius: 999px;
  background: var(--panel-alt);
  border: 1px solid var(--border);
}
.game-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: none;
  color: var(--text-muted);
  box-shadow: none;
}
.game-tabs button:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.05);
  filter: none;
}
.game-tabs button.active {
  color: #0a0c18;
  background: var(--accent-grad);
}
.filled-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--win);
}
.game-head {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 16px;
}
.lineups {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}
.lineup {
  flex: 1 1 460px;
  min-width: 0;
  overflow-x: auto;
}
.lineup h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px;
  font-size: 1.05em;
}
.win-tag {
  font-size: 0.7em;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 999px;
  color: #1b1405;
  background: var(--gold);
}
.lineup.won .lines {
  border-color: rgba(245, 196, 81, 0.45);
}
.lines th,
.lines td {
  padding: 7px 8px;
}
.lines .num {
  text-align: center;
}
.pos {
  font-size: 0.75em;
  font-weight: 800;
  color: var(--pos);
}
.lines select {
  max-width: 130px;
}
.champion-input {
  width: 150px;
}
.champion-input.invalid {
  border-color: var(--loss);
}
.bans {
  margin-top: 14px;
}
.ban-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.ban-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ban-input {
  width: 130px;
}
.ban-remove {
  padding: 4px 7px;
  font-size: 0.75em;
  color: var(--text-muted);
  background: none;
  box-shadow: none;
}
.ban-remove:hover {
  color: var(--loss);
}
.ban-add {
  font-size: 0.82em;
  padding: 7px 12px;
}
.kda-input {
  width: 48px;
  padding: 8px 4px;
  text-align: center;
}
.small {
  font-size: 0.85em;
}
.actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 8px 0 24px;
}
.saved {
  color: var(--win);
  font-weight: 600;
  font-size: 0.9em;
}
@media (max-width: 640px) {
  .match-hero {
    padding: 18px;
  }
  .code {
    font-size: 1.05em;
  }
  .series-score {
    font-size: 1.5em;
  }
  .logo-box {
    width: 38px;
    height: 38px;
  }
  .logo-box img {
    width: 30px;
    height: 30px;
  }
}
</style>
