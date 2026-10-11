<template>
  <div v-if="loaded && !hasData" class="muted">
    Aucune statistique disponible : aucun détail de match (champions, bans, MVP) n'a
    encore été saisi dans ce périmètre.
  </div>

  <template v-else-if="loaded">
    <div v-if="stats.summary" class="summary">
      <div class="tile">
        <span class="tile-value">{{ stats.summary.games }}</span>
        <span class="tile-label">manche{{ stats.summary.games > 1 ? 's' : '' }} saisie{{ stats.summary.games > 1 ? 's' : '' }}</span>
      </div>
      <div class="tile">
        <span class="tile-value">{{ stats.summary.kills }}</span>
        <span class="tile-label">kills · {{ stats.summary.killsPerGame.toFixed(1) }} par manche</span>
      </div>
      <div class="tile">
        <span class="tile-value">{{ stats.summary.championsPicked }}</span>
        <span class="tile-label">champions joués</span>
      </div>
      <div class="tile">
        <span class="tile-value">{{ stats.summary.championsPickedOrBanned }}</span>
        <span class="tile-label">champions joués ou bannis</span>
      </div>
      <div v-if="stats.kda.length" class="tile">
        <span class="tile-value">{{ stats.kda.length }}</span>
        <span class="tile-label">joueurs avec un K/D/A</span>
      </div>
    </div>

    <div class="position-filter" role="group" aria-label="Filtrer par poste">
      <button type="button" :class="{ active: position === null }" @click="position = null">Tous les postes</button>
      <button
        v-for="p in POSITIONS"
        :key="p.key"
        type="button"
        :class="{ active: position === p.key }"
        :style="{ '--pos-color': p.color }"
        @click="position = p.key"
      >{{ p.key }}</button>
    </div>

    <section v-if="positionCards.length" class="stats-block positions-block">
      <h3>Par poste</h3>
      <div class="position-cards">
        <article
          v-for="p in positionCards"
          :key="p.position"
          class="position-card"
          :class="{ dimmed: position && position !== p.position }"
          :style="{ '--pos-color': positionColor(p.position) }"
        >
          <header>
            <span class="pos-tag">{{ p.position }}</span>
            <span class="muted small">{{ p.games }} manche{{ p.games > 1 ? 's' : '' }}</span>
          </header>
          <div class="pos-kda">
            <span>{{ p.kills }} / {{ p.deaths }} / {{ p.assists }}</span>
            <strong>{{ p.ratio.toFixed(2) }}</strong>
          </div>
          <div class="pos-champs">
            <span v-for="c in p.topChampions" :key="c.champion" class="pos-champ" :title="`${c.champion} : ${c.picks} pick(s)`">
              <img v-if="championIconUrl(c.champion)" :src="championIconUrl(c.champion)" :alt="c.champion" />
              <span>{{ c.picks }}</span>
            </span>
          </div>
          <div v-if="bestKda.get(p.position)" class="pos-best small">
            Meilleur KDA :
            <strong>{{ bestKda.get(p.position).pseudo }}</strong>
            {{ ' ' }}<span class="muted">{{ bestKda.get(p.position).teamCode }}</span>
          </div>
        </article>
      </div>
    </section>

    <div class="stats-grid">
      <section class="stats-block">
        <h3>Champions<span v-if="position" class="muted small"> · picks en {{ position }}</span></h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Champion</th>
                <th class="num">Picks</th>
                <th v-if="!position" class="num">Bans</th>
                <th v-if="!position" class="num">Pick + Ban</th>
                <th v-if="!position" class="num" title="Part des manches où le champion est pické ou banni">Présence</th>
                <th v-if="!position" class="num">V-D</th>
                <th v-if="!position" class="num">% V</th>
                <th v-if="!position" class="num">KDA</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in championRows" :key="c.champion">
                <td class="champion-cell">
                  <img v-if="championIconUrl(c.champion)" :src="championIconUrl(c.champion)" :alt="c.champion" />
                  {{ c.champion }}
                </td>
                <td class="num" :class="{ strong: position }">{{ c.picks }}</td>
                <td v-if="!position" class="num">{{ c.bans }}</td>
                <td v-if="!position" class="num strong">{{ c.pickAndBan }}</td>
                <td v-if="!position" class="num">{{ presence(c) }}</td>
                <td v-if="!position" class="num">
                  <template v-if="c.wins + c.losses">
                    <span class="win">{{ c.wins }}</span>-<span class="loss">{{ c.losses }}</span>
                  </template>
                  <template v-else>—</template>
                </td>
                <td v-if="!position" class="num">{{ winRate(c) ?? '—' }}</td>
                <td v-if="!position" class="num" :class="hasKda(c) ? ratioClass(c.ratio) : ''">
                  {{ hasKda(c) ? kdaRatio(c.ratio) : '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="stats-block">
        <h3>MVP</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Joueur</th>
                <th>Poste</th>
                <th class="num">MVP série</th>
                <th class="num">MVP manche</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in mvpRows" :key="p.playerId">
                <td><PlayerCell :player="p" /></td>
                <td><span class="pos-tag" :style="{ '--pos-color': positionColor(p.position) }">{{ p.position }}</span></td>
                <td class="num">{{ p.seriesMvp }}</td>
                <td class="num">{{ p.gameMvp }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="stats-block wide">
        <h3>Joueurs · K/D/A et champions</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Joueur</th>
                <th>Poste</th>
                <th class="num">Manches</th>
                <th class="num">V-D</th>
                <th class="num">% V</th>
                <th class="num">K</th>
                <th class="num">D</th>
                <th class="num">A</th>
                <th class="num">Moyenne</th>
                <th class="num">KDA</th>
                <th class="num" title="Participation aux kills de l'équipe">KP</th>
                <th class="num" title="Manches sans mourir">0 mort</th>
                <th>Meilleurs champions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in kdaRows" :key="p.playerId">
                <td><PlayerCell :player="p" /></td>
                <td><span class="pos-tag" :style="{ '--pos-color': positionColor(p.position) }">{{ p.position }}</span></td>
                <td class="num">{{ p.games }}</td>
                <td class="num"><span class="win">{{ p.wins }}</span>-<span class="loss">{{ p.losses }}</span></td>
                <td class="num">{{ winRate(p) ?? '—' }}</td>
                <td class="num">{{ p.kills }}</td>
                <td class="num">{{ p.deaths }}</td>
                <td class="num">{{ p.assists }}</td>
                <td class="num muted">{{ averageKda(p) }}</td>
                <td class="num strong" :class="ratioClass(p.ratio)">{{ kdaRatio(p.ratio) }}</td>
                <td class="num">{{ percent(p.killParticipation) }}</td>
                <td class="num">{{ p.perfectGames }}</td>
                <td>
                  <span class="mini-champs">
                    <span
                      v-for="c in p.topChampions"
                      :key="c.champion"
                      class="pos-champ"
                      :title="`${c.champion} : ${c.picks} manche(s)`"
                    >
                      <img v-if="championIconUrl(c.champion)" :src="championIconUrl(c.champion)" :alt="c.champion" />
                      <span>{{ c.picks }}</span>
                    </span>
                    <span v-if="p.championCount > p.topChampions.length" class="muted small">
                      +{{ p.championCount - p.topChampions.length }}
                    </span>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="stats.teams.length && !position" class="stats-block wide">
        <h3>Équipes</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Équipe</th>
                <th class="num">Manches</th>
                <th class="num">V-D</th>
                <th class="num">% V</th>
                <th class="num">Kills</th>
                <th class="num">Morts</th>
                <th class="num">Kills / manche</th>
                <th>Champion favori</th>
                <th>Ban favori</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in stats.teams" :key="t.teamId">
                <td>
                  <router-link :to="`/teams/${t.teamId}`" class="team-link">
                    <TeamTag :id="t.teamId" :code="t.teamCode" :name="t.teamName" :has-logo="t.teamHasLogo" />
                    <span class="muted small">{{ t.teamName }}</span>
                  </router-link>
                </td>
                <td class="num">{{ t.games }}</td>
                <td class="num"><span class="win">{{ t.wins }}</span>-<span class="loss">{{ t.losses }}</span></td>
                <td class="num">{{ winRate(t) ?? '—' }}</td>
                <td class="num">{{ t.kills }}</td>
                <td class="num">{{ t.deaths }}</td>
                <td class="num strong">{{ t.killsPerGame.toFixed(1) }}</td>
                <td><ChampionChip :pick="t.mostPicked" /></td>
                <td><ChampionChip :pick="t.mostBanned" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="hasRecords" class="stats-block wide">
        <h3>Records sur une manche</h3>
        <div class="records">
          <div v-for="r in recordLists" :key="r.key" class="record-col">
            <h4>{{ r.title }}</h4>
            <ol class="record-list">
              <li v-for="(g, i) in r.games" :key="`${r.key}-${i}`">
                <span class="record-rank">{{ i + 1 }}</span>
                <img v-if="championIconUrl(g.champion)" :src="championIconUrl(g.champion)" :alt="g.champion" :title="g.champion" />
                <span class="record-body">
                  <router-link :to="`/players/${g.playerId}`" class="pseudo">{{ g.pseudo }}</router-link>
                  <span class="muted small">
                    {{ g.teamCode }}<template v-if="g.opponentCode"> vs {{ g.opponentCode }}</template>
                    <template v-if="g.won !== null">
                      · <span :class="g.won ? 'win' : 'loss'">{{ g.won ? 'V' : 'D' }}</span>
                    </template>
                  </span>
                </span>
                <component
                  :is="g.matchId ? RouterLink : 'span'"
                  :to="g.matchId ? `/matches/${g.matchId}` : undefined"
                  class="record-value"
                  :class="r.key === 'bestKda' ? ratioClass(g.ratio) : ''"
                >
                  {{ r.value(g) }}
                </component>
              </li>
            </ol>
          </div>
        </div>
      </section>
    </div>
  </template>
</template>

<script setup>
import { computed, h, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api, { teamLogoUrl } from '../services/api'
import { championIconUrl } from '../champions'
import { POSITIONS, positionColor } from '../positions'
import { averageKda, kdaRatio, percent, ratioClass, winRate } from '../statsFormat'
import TeamTag from './TeamTag.vue'

// scope : { competitionId } | { season } | {} (tout l'historique)
const props = defineProps({
  scope: { type: Object, required: true }
})

const stats = ref({ summary: null, champions: [], mvps: [], kda: [], positions: [], teams: [], records: null })
const loaded = ref(false)
// Poste filtre (null = tous)
const position = ref(null)

const hasData = computed(() => stats.value.champions.length || stats.value.mvps.length || stats.value.kda.length)

const positionCards = computed(() => stats.value.positions ?? [])

const championRows = computed(() => {
  if (!position.value) return stats.value.champions
  return stats.value.champions
    .map(c => ({ ...c, picks: c.picksByPosition?.[position.value] ?? 0 }))
    .filter(c => c.picks > 0)
    .sort((a, b) => b.picks - a.picks || a.champion.localeCompare(b.champion))
})

const mvpRows = computed(() => stats.value.mvps.filter(p => !position.value || p.position === position.value))
const kdaRows = computed(() => stats.value.kda.filter(p => !position.value || p.position === position.value))

// Part des manches du perimetre ou le champion est pick ou ban
function presence(c) {
  const games = stats.value.summary?.games
  return games ? `${Math.round((100 * c.pickAndBan) / games)} %` : '—'
}

function hasKda(c) {
  return c.picks > 0 && c.kills + c.deaths + c.assists > 0
}

// Top 5 des performances sur une manche, filtrees par poste le cas echeant
const recordLists = computed(() => {
  const r = stats.value.records
  if (!r) return []
  return [
    { key: 'mostKills', title: 'Plus de kills', games: r.mostKills, value: g => `${g.kills} kills` },
    { key: 'mostAssists', title: "Plus d'assists", games: r.mostAssists, value: g => `${g.assists} assists` },
    {
      key: 'bestKda',
      title: 'Meilleur KDA',
      games: r.bestKda,
      value: g => `${g.kills}/${g.deaths}/${g.assists} · ${kdaRatio(g.ratio)}`
    }
  ].map(list => ({ ...list, games: list.games.filter(g => !position.value || g.position === position.value) }))
})
const hasRecords = computed(() => recordLists.value.some(r => r.games.length))

// Icone et nom d'un champion, avec son nombre d'occurrences
const ChampionChip = (chipProps) => {
  const p = chipProps.pick
  if (!p) return h('span', { class: 'muted' }, '—')
  return h('span', { class: 'champion-cell' }, [
    championIconUrl(p.champion) ? h('img', { src: championIconUrl(p.champion), alt: p.champion }) : null,
    `${p.champion} `,
    h('span', { class: 'muted small' }, `×${p.picks}`)
  ])
}
ChampionChip.props = ['pick']

// Meilleur ratio K/D/A de chaque poste (la liste kda est deja triee par ratio decroissant)
const bestKda = computed(() => {
  const best = new Map()
  for (const p of stats.value.kda) {
    if (p.position && !best.has(p.position)) best.set(p.position, p)
  }
  return best
})

// Joueur avec le logo et le trigramme de son equipe
const PlayerCell = (cellProps) => {
  const p = cellProps.player
  return h('span', { class: 'player-cell' }, [
    h('span', { class: 'logo-box', title: p.teamCode ?? '' }, [
      p.teamHasLogo
        ? h('img', { src: teamLogoUrl(p.teamId), alt: p.teamCode ?? '' })
        : h('span', (p.teamCode ?? '').slice(0, 2))
    ]),
    h('span', { class: 'team-code' }, p.teamCode ?? '—'),
    h(RouterLink, { to: `/players/${p.playerId}`, class: 'pseudo' }, () => p.pseudo)
  ])
}
PlayerCell.props = ['player']

async function load() {
  loaded.value = false
  stats.value = await api.getStats(props.scope)
  loaded.value = true
}

watch(() => props.scope, load, { immediate: true, deep: true })
</script>

<style scoped>
.position-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
}
.position-filter button {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--border, rgba(255, 255, 255, 0.12));
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.85em;
  cursor: pointer;
}
.position-filter button.active {
  color: var(--text);
  border-color: var(--pos-color, var(--accent));
  background: color-mix(in srgb, var(--pos-color, var(--accent)) 18%, transparent);
}
.positions-block {
  margin-bottom: 24px;
}
.position-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}
.position-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border, rgba(255, 255, 255, 0.1));
  border-top: 3px solid var(--pos-color);
  transition: opacity 0.2s;
}
.position-card.dimmed {
  opacity: 0.4;
}
.position-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pos-tag {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 6px;
  font-size: 0.75em;
  font-weight: 800;
  color: var(--pos-color);
  background: color-mix(in srgb, var(--pos-color) 15%, transparent);
}
.pos-kda {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-variant-numeric: tabular-nums;
}
.pos-kda strong {
  font-size: 1.2em;
}
.pos-champs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pos-champ {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 0.78em;
  font-weight: 700;
}
.pos-champ img {
  width: 24px;
  height: 24px;
  border-radius: 5px;
  object-fit: cover;
}
.small {
  font-size: 0.82em;
}
.stats-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.stats-block {
  flex: 1 1 320px;
  min-width: 0;
}
.stats-block.wide {
  flex-basis: 100%;
}
.stats-block h3 {
  margin: 0 0 12px;
  font-size: 1em;
}
.table-scroll {
  container-type: inline-size;
  overflow-x: auto;
}
.table-scroll table {
  width: 100%;
  margin: 0;
}
.num {
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.strong {
  font-weight: 800;
}
.champion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.champion-cell img {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  object-fit: cover;
}
:deep(.player-cell) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
:deep(.logo-box) {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 7px;
  background: var(--logo-bg);
  font-size: 0.65em;
  font-weight: 800;
  color: var(--text-muted);
}
:deep(.logo-box img) {
  width: 21px;
  height: 21px;
  object-fit: contain;
}
:deep(.team-code) {
  min-width: 34px;
  font-size: 0.8em;
  font-weight: 800;
  color: var(--text-muted);
}
:deep(.pseudo) {
  font-weight: 700;
  color: var(--text);
}
:deep(.pseudo:hover) {
  color: var(--accent);
}
.summary {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}
.tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border, rgba(255, 255, 255, 0.1));
}
.tile-value {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.35em;
  font-variant-numeric: tabular-nums;
}
.tile-label {
  font-size: 0.74em;
  color: var(--text-muted);
}
.win {
  color: var(--win);
}
.loss {
  color: var(--loss);
}
.mini-champs {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.team-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  color: var(--text);
}
.records {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}
.record-col h4 {
  margin: 0 0 8px;
  font-size: 0.88em;
  color: var(--text-muted);
}
.record-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.record-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border, rgba(255, 255, 255, 0.1));
}
.record-list li:first-child {
  border-color: rgba(245, 196, 81, 0.45);
}
.record-list img {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  object-fit: cover;
}
.record-rank {
  width: 16px;
  font-weight: 800;
  color: var(--text-dim);
  text-align: center;
}
.record-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.record-value {
  margin-left: auto;
  font-weight: 800;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
</style>
