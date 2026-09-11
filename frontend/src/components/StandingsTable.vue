<template>
  <table v-if="rows.length">
    <thead>
      <tr>
        <th>#</th>
        <th></th>
        <th>Équipe</th>
        <th>Séries (V-D)</th>
        <th>Games (V-D)</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="(row, index) in rows" :key="row.teamId">
        <td>
          <span class="rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
        </td>
        <td>
          <img
            v-if="row.teamHasLogo"
            class="team-logo"
            :src="teamLogoUrl(row.teamId)"
            :alt="row.teamName"
          />
        </td>
        <td class="team-name">{{ row.teamName }}</td>
        <td class="score-cell">{{ row.seriesWon }}-{{ row.seriesLost }}</td>
        <td class="score-cell muted">{{ row.gamesWon }}-{{ row.gamesLost }}</td>
      </tr>
    </tbody>
  </table>
  <p v-else>Aucun match de saison régulière joué pour l'instant : le classement apparaîtra dès qu'un résultat sera enregistré.</p>
</template>

<script setup>
import { teamLogoUrl } from '../services/api'

defineProps({
  rows: { type: Array, default: () => [] }
})
</script>

<style scoped>
.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-weight: 700;
  font-size: 0.85em;
  color: var(--text-muted);
}

.rank-1 {
  background: linear-gradient(180deg, #f0c75e, #a9791f);
  color: #1a1408;
}

.rank-2 {
  background: linear-gradient(180deg, #d7dbe0, #8b929c);
  color: #1a1408;
}

.rank-3 {
  background: linear-gradient(180deg, #c98a52, #7a4c26);
  color: #1a1408;
}

.team-name {
  font-weight: 600;
}

.score-cell {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.score-cell.muted {
  font-weight: 500;
  color: var(--text-muted);
}
</style>
