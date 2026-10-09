<template>
  <div v-if="loaded && !hasData" class="muted">
    Aucune statistique disponible : aucun détail de match (champions, bans, MVP) n'a
    encore été saisi dans ce périmètre.
  </div>

  <template v-else-if="loaded">
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
        <h3>K/D/A cumulé</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Joueur</th>
                <th>Poste</th>
                <th class="num">Manches</th>
                <th class="num">K</th>
                <th class="num">D</th>
                <th class="num">A</th>
                <th class="num">Ratio</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in kdaRows" :key="p.playerId">
                <td><PlayerCell :player="p" /></td>
                <td><span class="pos-tag" :style="{ '--pos-color': positionColor(p.position) }">{{ p.position }}</span></td>
                <td class="num">{{ p.games }}</td>
                <td class="num">{{ p.kills }}</td>
                <td class="num">{{ p.deaths }}</td>
                <td class="num">{{ p.assists }}</td>
                <td class="num strong">{{ p.ratio.toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </template>
</template>

<script setup>
import { computed, h, ref, watch } from 'vue'
import api, { teamLogoUrl } from '../services/api'
import { championIconUrl } from '../champions'
import { POSITIONS, positionColor } from '../positions'

// scope : { competitionId } | { season } | {} (tout l'historique)
const props = defineProps({
  scope: { type: Object, required: true }
})

const stats = ref({ champions: [], mvps: [], kda: [], positions: [] })
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
    h('span', { class: 'pseudo' }, p.pseudo)
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
}
</style>
