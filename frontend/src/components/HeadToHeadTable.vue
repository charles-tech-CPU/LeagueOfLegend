<template>
  <div v-if="rows.length" class="h2h-scroll">
    <table class="h2h-table">
      <thead>
        <tr>
          <th class="corner"></th>
          <th v-for="col in rows" :key="col.teamId" class="col-head">
            <img v-if="col.teamHasLogo" class="team-logo-sm" :src="teamLogoUrl(col.teamId)" :alt="col.teamCode" />
            <span>{{ col.teamCode }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="row.teamId">
          <th class="row-head">
            <span class="rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
            <img v-if="row.teamHasLogo" class="team-logo-sm" :src="teamLogoUrl(row.teamId)" :alt="row.teamCode" />
            <span class="row-name">{{ row.teamCode }}</span>
          </th>
          <td v-for="col in rows" :key="col.teamId" class="cell" :class="cellClass(row, col)">
            <span v-if="row.teamId === col.teamId" class="diagonal"></span>
            <span v-else-if="scoreFor(row.teamId, col.teamId)">{{ scoreFor(row.teamId, col.teamId) }}</span>
            <span v-else class="empty">–</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else>Aucun match de saison régulière joué pour l'instant.</p>
</template>

<script setup>
import { computed } from 'vue'
import { teamLogoUrl } from '../services/api'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  // Chaque cellule : { teamAId, teamBId, score } ou "score" est le score exact
  // de CETTE manche du point de vue de teamA (ex: "2-1"), pas un bilan agrege.
  cells: { type: Array, default: () => [] }
})

const cellMap = computed(() => {
  const map = new Map()
  for (const c of props.cells) {
    map.set(`${c.teamAId}:${c.teamBId}`, c.score)
  }
  return map
})

function scoreFor(teamAId, teamBId) {
  return cellMap.value.get(`${teamAId}:${teamBId}`)
}

function cellClass(row, col) {
  if (row.teamId === col.teamId) return 'diagonal-cell'
  const score = scoreFor(row.teamId, col.teamId)
  if (!score) return ''
  const [a, b] = score.split('-').map(Number)
  if (a > b) return 'cell-win'
  if (a < b) return 'cell-loss'
  return 'cell-draw'
}
</script>

<style scoped>
.h2h-scroll {
  overflow-x: auto;
  width: 100vw;
  position: relative;
  left: 50%;
  right: 50%;
  margin-left: -50vw;
  margin-right: -50vw;
  padding: 0 16px;
}
.h2h-table {
  border-collapse: collapse;
  width: auto;
  margin: 0 auto;
}
.h2h-table th,
.h2h-table td {
  border: 1px solid var(--border);
  text-align: center;
  padding: 7px 9px;
  font-size: 0.84em;
}
.h2h-table {
  border-radius: var(--radius);
}
.corner {
  background: transparent;
  border: none;
}
.col-head {
  color: var(--text-muted);
  font-weight: 700;
  white-space: nowrap;
}
.col-head .team-logo-sm {
  display: block;
  margin: 0 auto 2px;
}
.row-head {
  text-align: left;
  white-space: nowrap;
  padding: 6px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-left: none;
}
.row-name {
  font-weight: 700;
  color: var(--text);
}
.team-logo-sm {
  height: 22px;
  width: 22px;
  padding: 2px;
  border-radius: 6px;
  background: var(--logo-bg);
  object-fit: contain;
}
.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-weight: 700;
  font-size: 0.75em;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}
.cell {
  min-width: 46px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}
.cell-win {
  background: rgba(52, 211, 153, 0.16);
  color: var(--win);
}
.cell-loss {
  background: rgba(251, 113, 133, 0.14);
  color: var(--loss);
}
.cell-draw {
  background: rgba(255, 255, 255, 0.05);
}
.empty {
  color: var(--text-dim);
  font-weight: 400;
}
.diagonal-cell {
  background: repeating-linear-gradient(
    45deg,
    var(--border),
    var(--border) 4px,
    var(--panel) 4px,
    var(--panel) 8px
  );
}
</style>
