<template>
  <p v-if="!stats.career.games" class="muted">
    Aucune statistique pour l'instant : aucune manche de ce joueur n'a encore été saisie (champion, K/D/A).
  </p>

  <template v-else>
    <!-- Carriere -->
    <section class="block">
      <h2>Carrière</h2>
      <div class="tiles">
        <div class="tile">
          <span class="tile-value">{{ c.games }}</span>
          <span class="tile-label">manche{{ c.games > 1 ? 's' : '' }} saisie{{ c.games > 1 ? 's' : '' }}</span>
        </div>
        <div class="tile">
          <span class="tile-value"><span class="win">{{ c.wins }}</span>-<span class="loss">{{ c.losses }}</span></span>
          <span class="tile-label">manches{{ winRate(c) !== null ? ` · ${winRate(c)} % de victoires` : '' }}</span>
          <span v-if="winRate(c) !== null" class="progress"><span :style="{ width: `${winRate(c)}%` }"></span></span>
        </div>
        <div class="tile highlight">
          <span class="tile-value" :class="ratioClass(c.ratio)">{{ kdaRatio(c.ratio) }}</span>
          <span class="tile-label">KDA · {{ c.kills }} / {{ c.deaths }} / {{ c.assists }}</span>
        </div>
        <div class="tile">
          <span class="tile-value small-value">{{ averageKda(c) }}</span>
          <span class="tile-label">K / D / A moyen par manche</span>
        </div>
        <div class="tile">
          <span class="tile-value">{{ percent(c.killParticipation) }}</span>
          <span class="tile-label">participation aux kills</span>
        </div>
        <div class="tile" :class="{ gold: stats.seriesMvp + c.gameMvp }">
          <span class="tile-value">⭐ {{ stats.seriesMvp + c.gameMvp }}</span>
          <span class="tile-label">MVP · {{ stats.seriesMvp }} série{{ stats.seriesMvp > 1 ? 's' : '' }}, {{ c.gameMvp }} manche{{ c.gameMvp > 1 ? 's' : '' }}</span>
        </div>
        <div class="tile">
          <span class="tile-value">{{ c.perfectGames }}</span>
          <span class="tile-label">manche{{ c.perfectGames > 1 ? 's' : '' }} sans mourir</span>
        </div>
        <div class="tile">
          <span class="tile-value">{{ c.championCount }}</span>
          <span class="tile-label">champion{{ c.championCount > 1 ? 's' : '' }} joué{{ c.championCount > 1 ? 's' : '' }}</span>
        </div>
      </div>
    </section>

    <!-- Meilleurs champions -->
    <section class="block">
      <h2>Meilleurs champions</h2>
      <div class="champ-cards">
        <article v-for="(ch, i) in topChampions" :key="ch.champion" class="champ-card">
          <span class="champ-rank">#{{ i + 1 }}</span>
          <img v-if="championIconUrl(ch.champion)" :src="championIconUrl(ch.champion)" :alt="ch.champion" />
          <div class="champ-body">
            <strong>{{ ch.champion }}</strong>
            <span class="muted small">
              {{ ch.stats.games }} manche{{ ch.stats.games > 1 ? 's' : '' }}
              <template v-if="winRate(ch.stats) !== null"> · {{ winRate(ch.stats) }} % V</template>
            </span>
            <span class="small">
              <strong :class="ratioClass(ch.stats.ratio)">{{ kdaRatio(ch.stats.ratio) }}</strong> KDA
              <span class="muted">· {{ averageKda(ch.stats) }}</span>
            </span>
          </div>
        </article>
      </div>
      <details v-if="stats.champions.length > topChampions.length" class="more">
        <summary>Tous les champions ({{ stats.champions.length }})</summary>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Champion</th>
                <th class="num">Manches</th>
                <th class="num">V-D</th>
                <th class="num">% V</th>
                <th class="num">K / D / A moyen</th>
                <th class="num">KDA</th>
                <th class="num">KP</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ch in stats.champions" :key="ch.champion">
                <td class="champion-cell">
                  <img v-if="championIconUrl(ch.champion)" :src="championIconUrl(ch.champion)" :alt="ch.champion" />
                  {{ ch.champion }}
                </td>
                <td class="num">{{ ch.stats.games }}</td>
                <td class="num"><span class="win">{{ ch.stats.wins }}</span>-<span class="loss">{{ ch.stats.losses }}</span></td>
                <td class="num">{{ winRate(ch.stats) ?? '—' }}</td>
                <td class="num">{{ averageKda(ch.stats) }}</td>
                <td class="num strong" :class="ratioClass(ch.stats.ratio)">{{ kdaRatio(ch.stats.ratio) }}</td>
                <td class="num">{{ percent(ch.stats.killParticipation) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </section>

    <!-- Par competition -->
    <section class="block">
      <h2>Par compétition</h2>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Compétition</th>
              <th>Équipe</th>
              <th class="num">Manches</th>
              <th class="num">V-D</th>
              <th class="num">% V</th>
              <th class="num">K / D / A</th>
              <th class="num">Moyenne</th>
              <th class="num">KDA</th>
              <th class="num">KP</th>
              <th class="num">MVP</th>
              <th>Champions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in stats.competitions" :key="line.competitionId">
              <td>
                <router-link :to="`/competitions/${line.competitionId}`" class="comp-link">
                  <span class="league-badge" :style="{ '--league-color': leagueColor(line.code) }">{{ line.code }}</span>
                  {{ line.name }}
                </router-link>
              </td>
              <td>
                <router-link v-if="line.teamId" :to="`/teams/${line.teamId}`">
                  <TeamTag :id="line.teamId" :code="line.teamCode" :has-logo="line.teamHasLogo" />
                </router-link>
              </td>
              <td class="num">{{ line.stats.games }}</td>
              <td class="num"><span class="win">{{ line.stats.wins }}</span>-<span class="loss">{{ line.stats.losses }}</span></td>
              <td class="num">{{ winRate(line.stats) ?? '—' }}</td>
              <td class="num">{{ line.stats.kills }} / {{ line.stats.deaths }} / {{ line.stats.assists }}</td>
              <td class="num muted">{{ averageKda(line.stats) }}</td>
              <td class="num strong" :class="ratioClass(line.stats.ratio)">{{ kdaRatio(line.stats.ratio) }}</td>
              <td class="num">{{ percent(line.stats.killParticipation) }}</td>
              <td class="num">{{ mvpLabel(line.seriesMvp, line.stats.gameMvp) }}</td>
              <td><ChampionIcons :picks="line.topChampions" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Par saison -->
    <section class="block">
      <h2>Par saison</h2>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Saison</th>
              <th class="num">Manches</th>
              <th class="num">V-D</th>
              <th class="num">% V</th>
              <th class="num">K / D / A</th>
              <th class="num">Moyenne</th>
              <th class="num">KDA</th>
              <th class="num">KP</th>
              <th class="num">MVP</th>
              <th>Champions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in stats.seasons" :key="line.season">
              <td>
                <router-link :to="{ path: '/stats', query: { season: line.season } }" class="strong">{{ line.season }}</router-link>
              </td>
              <td class="num">{{ line.stats.games }}</td>
              <td class="num"><span class="win">{{ line.stats.wins }}</span>-<span class="loss">{{ line.stats.losses }}</span></td>
              <td class="num">{{ winRate(line.stats) ?? '—' }}</td>
              <td class="num">{{ line.stats.kills }} / {{ line.stats.deaths }} / {{ line.stats.assists }}</td>
              <td class="num muted">{{ averageKda(line.stats) }}</td>
              <td class="num strong" :class="ratioClass(line.stats.ratio)">{{ kdaRatio(line.stats.ratio) }}</td>
              <td class="num">{{ percent(line.stats.killParticipation) }}</td>
              <td class="num">{{ mvpLabel(line.seriesMvp, line.stats.gameMvp) }}</td>
              <td><ChampionIcons :picks="line.topChampions" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div class="two-cols">
      <!-- Par poste (utile en cas de role swap) -->
      <section v-if="stats.positions.length > 1" class="block">
        <h2>Par poste</h2>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Poste</th>
                <th class="num">Manches</th>
                <th class="num">% V</th>
                <th class="num">KDA</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in stats.positions" :key="p.position">
                <td><span class="pos-tag" :style="{ '--pos-color': positionColor(p.position) }">{{ p.position }}</span></td>
                <td class="num">{{ p.stats.games }}</td>
                <td class="num">{{ winRate(p.stats) ?? '—' }}</td>
                <td class="num strong" :class="ratioClass(p.stats.ratio)">{{ kdaRatio(p.stats.ratio) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Face aux equipes -->
      <section v-if="stats.opponents.length" class="block">
        <h2>Face aux équipes</h2>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Adversaire</th>
                <th class="num">Manches</th>
                <th class="num">V-D</th>
                <th class="num">% V</th>
                <th class="num">KDA</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in stats.opponents" :key="o.teamId">
                <td>
                  <router-link :to="`/teams/${o.teamId}`">
                    <TeamTag :id="o.teamId" :code="o.teamCode" :name="o.teamName" :has-logo="o.teamHasLogo" />
                  </router-link>
                </td>
                <td class="num">{{ o.stats.games }}</td>
                <td class="num"><span class="win">{{ o.stats.wins }}</span>-<span class="loss">{{ o.stats.losses }}</span></td>
                <td class="num">{{ winRate(o.stats) ?? '—' }}</td>
                <td class="num strong" :class="ratioClass(o.stats.ratio)">{{ kdaRatio(o.stats.ratio) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <div class="two-cols">
      <section v-if="stats.bestGames.length" class="block">
        <h2>Meilleures manches</h2>
        <GameList :games="stats.bestGames" />
      </section>
      <section v-if="stats.recentGames.length" class="block">
        <h2>Dernières manches</h2>
        <GameList :games="stats.recentGames" />
      </section>
    </div>
  </template>
</template>

<script setup>
import { computed, h } from 'vue'
import { RouterLink } from 'vue-router'
import { championIconUrl } from '../champions'
import { leagueColor } from '../leagueColors'
import { positionColor } from '../positions'
import { fullDay } from '../format'
import { averageKda, kdaRatio, percent, ratioClass, winRate } from '../statsFormat'
import TeamTag from './TeamTag.vue'

const props = defineProps({
  // Reponse de GET /api/players/{id}/stats
  stats: { type: Object, required: true }
})

const TOP_CHAMPIONS = 6

const c = computed(() => props.stats.career)
const topChampions = computed(() => props.stats.champions.slice(0, TOP_CHAMPIONS))

function mvpLabel(series, games) {
  return series + games ? `${series} / ${games}` : '—'
}

// Icones des champions les plus joues, avec le nombre de manches
const ChampionIcons = (iconProps) =>
  h(
    'span',
    { class: 'champ-icons' },
    (iconProps.picks ?? []).map(p =>
      h('span', { class: 'champ-icon', title: `${p.champion} : ${p.picks} manche(s)` }, [
        championIconUrl(p.champion) ? h('img', { src: championIconUrl(p.champion), alt: p.champion }) : p.champion,
        h('span', p.picks)
      ])
    )
  )
ChampionIcons.props = ['picks']

// Liste de manches : champion, K/D/A, resultat, adversaire, lien vers le match
const GameList = (listProps) =>
  h(
    'ul',
    { class: 'game-list' },
    listProps.games.map(g =>
      h('li', { class: ['game-row', g.won === true ? 'won' : g.won === false ? 'lost' : ''] }, [
        championIconUrl(g.champion)
          ? h('img', { class: 'game-champ', src: championIconUrl(g.champion), alt: g.champion, title: g.champion })
          : h('span', { class: 'game-champ' }, g.champion),
        h('span', { class: 'game-kda' }, [
          h('strong', `${g.kills} / ${g.deaths} / ${g.assists}`),
          h('span', { class: ['small', ratioClass(g.ratio)] }, ` ${kdaRatio(g.ratio)} KDA`)
        ]),
        h('span', { class: 'game-context small muted' }, [
          g.won === null ? '' : h('span', { class: g.won ? 'win' : 'loss' }, g.won ? 'V ' : 'D '),
          g.opponentCode ? `vs ${g.opponentCode}` : '',
          g.competitionCode ? ` · ${g.competitionCode}` : '',
          g.date ? ` · ${fullDay(g.date)}` : ''
        ]),
        g.matchId ? h(RouterLink, { to: `/matches/${g.matchId}`, class: 'game-link small' }, () => 'Voir') : null
      ])
    )
  )
GameList.props = ['games']
</script>

<style scoped>
.block {
  margin-bottom: 28px;
  min-width: 0;
}
.block h2 {
  margin: 0 0 12px;
  font-size: 1.1em;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(165px, 1fr));
  gap: 10px;
}
.tile {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
}
.tile.highlight {
  border-color: rgba(129, 140, 248, 0.45);
  background: rgba(129, 140, 248, 0.08);
}
.tile.gold {
  border-color: rgba(245, 196, 81, 0.45);
  background: rgba(245, 196, 81, 0.08);
}
.tile-value {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.45em;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
}
.small-value {
  font-size: 1.1em;
}
.tile-label {
  font-size: 0.74em;
  color: var(--text-muted);
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
.champ-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.champ-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--border);
}
.champ-card img {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
}
.champ-rank {
  position: absolute;
  top: 6px;
  right: 10px;
  font-size: 0.72em;
  font-weight: 800;
  color: var(--text-dim);
}
.champ-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.more {
  margin-top: 12px;
}
.more summary {
  cursor: pointer;
  color: var(--text-muted);
  font-size: 0.88em;
}
.more summary:hover {
  color: var(--accent);
}
.table-scroll {
  overflow-x: auto;
}
.table-scroll table {
  width: 100%;
  margin: 8px 0 0;
}
.num {
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.strong {
  font-weight: 800;
}
.small {
  font-size: 0.82em;
}
.win {
  color: var(--win);
}
.loss {
  color: var(--loss);
}
.comp-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  color: var(--text);
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
.pos-tag {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 6px;
  font-size: 0.75em;
  font-weight: 800;
  color: var(--pos-color);
  background: color-mix(in srgb, var(--pos-color) 15%, transparent);
}
.two-cols {
  display: flex;
  flex-wrap: wrap;
  gap: 0 24px;
}
.two-cols > .block {
  flex: 1 1 340px;
}
:deep(.champ-icons) {
  display: inline-flex;
  gap: 6px;
}
:deep(.champ-icon) {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 0.75em;
  font-weight: 700;
}
:deep(.champ-icon img) {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  object-fit: cover;
}
:deep(.game-list) {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
:deep(.game-row) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  border-left: 3px solid var(--border-strong);
}
:deep(.game-row.won) {
  border-left-color: var(--win);
}
:deep(.game-row.lost) {
  border-left-color: var(--loss);
}
:deep(.game-champ) {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  object-fit: cover;
}
:deep(.game-kda) {
  font-variant-numeric: tabular-nums;
}
:deep(.game-link) {
  margin-left: auto;
}
</style>
