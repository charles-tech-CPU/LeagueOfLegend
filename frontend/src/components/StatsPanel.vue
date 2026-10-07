<template>
  <div v-if="loaded && !hasData" class="muted">
    Aucune statistique disponible : aucun détail de match (champions, bans, MVP) n'a
    encore été saisi dans ce périmètre.
  </div>

  <div v-else-if="loaded" class="stats-grid">
    <section class="stats-block">
      <h3>Champions</h3>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Champion</th>
              <th class="num">Picks</th>
              <th class="num">Bans</th>
              <th class="num">Pick + Ban</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in stats.champions" :key="c.champion">
              <td class="champion-cell">
                <img v-if="championIconUrl(c.champion)" :src="championIconUrl(c.champion)" :alt="c.champion" />
                {{ c.champion }}
              </td>
              <td class="num">{{ c.picks }}</td>
              <td class="num">{{ c.bans }}</td>
              <td class="num strong">{{ c.pickAndBan }}</td>
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
              <th>Équipe</th>
              <th class="num">MVP série</th>
              <th class="num">MVP manche</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in stats.mvps" :key="p.playerId">
              <td>{{ p.pseudo }}</td>
              <td>{{ p.teamCode }}</td>
              <td class="num">{{ p.seriesMvp }}</td>
              <td class="num">{{ p.gameMvp }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="stats-block">
      <h3>K/D/A cumulé</h3>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Joueur</th>
              <th>Équipe</th>
              <th class="num">K</th>
              <th class="num">D</th>
              <th class="num">A</th>
              <th class="num">Ratio</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in stats.kda" :key="p.playerId">
              <td>{{ p.pseudo }}</td>
              <td>{{ p.teamCode }}</td>
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

<script setup>
import { computed, ref, watch } from 'vue'
import api from '../services/api'
import { championIconUrl } from '../champions'

// scope : { competitionId } | { season } | {} (tout l'historique)
const props = defineProps({
  scope: { type: Object, required: true }
})

const stats = ref({ champions: [], mvps: [], kda: [] })
const loaded = ref(false)

const hasData = computed(() => stats.value.champions.length || stats.value.mvps.length || stats.value.kda.length)

async function load() {
  loaded.value = false
  stats.value = await api.getStats(props.scope)
  loaded.value = true
}

watch(() => props.scope, load, { immediate: true, deep: true })
</script>

<style scoped>
.stats-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.stats-block {
  flex: 1 1 320px;
  min-width: 0;
}
.stats-block h3 {
  margin: 0 0 12px;
  font-size: 1em;
}
.table-scroll {
  container-type: inline-size;
  overflow-x: auto;
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
</style>
