<template>
  <table v-if="rows.length" class="standings">
    <thead>
      <tr>
        <th class="rank-col">#</th>
        <th>Équipe</th>
        <th class="num">Séries</th>
        <th class="rate-col">% victoires</th>
        <th class="num">Games</th>
        <th v-if="hasForm" class="form-col">Forme</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="(row, index) in rows" :key="row.teamId">
        <td class="rank-col">
          <span class="rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
        </td>
        <td>
          <span class="team">
            <span class="logo-box">
              <img v-if="row.teamHasLogo" :src="teamLogoUrl(row.teamId)" :alt="row.teamName" />
              <span v-else>{{ (row.teamCode ?? '').slice(0, 2) }}</span>
            </span>
            <span class="team-name">{{ row.teamName }}</span>
          </span>
        </td>
        <td class="num record">
          <span class="w">{{ row.seriesWon }}</span><span class="sep">-</span><span class="l">{{ row.seriesLost }}</span>
        </td>
        <td class="rate-col">
          <span class="rate">
            <span class="rate-bar"><span class="rate-fill" :style="{ width: `${winRate(row)}%` }"></span></span>
            <span class="rate-value">{{ winRate(row) }}%</span>
          </span>
        </td>
        <td class="num games">
          {{ row.gamesWon }}-{{ row.gamesLost }}
          <span class="diff" :class="diffTone(row)">{{ diffLabel(row) }}</span>
        </td>
        <td v-if="hasForm" class="form-col">
          <span class="form">
            <span
              v-for="f in formOf(row.teamId)"
              :key="f.id"
              class="pip"
              :class="f.won ? 'won' : 'lost'"
              :title="f.title"
            >{{ f.won ? 'V' : 'D' }}</span>
          </span>
        </td>
      </tr>
    </tbody>
  </table>
  <p v-else class="muted">Aucun match de saison régulière joué pour l'instant : le classement apparaîtra dès qu'un résultat sera enregistré.</p>
</template>

<script setup>
import { computed } from 'vue'
import { teamLogoUrl } from '../services/api'
import { kickoff } from '../matchEdit'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  // Matchs ayant servi au classement, pour la colonne "Forme" (facultatif)
  matches: { type: Array, default: () => [] }
})

const FORM_LENGTH = 5

// Derniers resultats de chaque equipe, du plus ancien au plus recent.
const formByTeam = computed(() => {
  const form = new Map()
  const played = props.matches
    .filter(m => m.status === 'COMPLETED' && m.team1Id != null && m.team2Id != null)
    .sort((a, b) => kickoff(a).localeCompare(kickoff(b)))
  for (const m of played) {
    for (const [self, opp, scored, conceded] of [
      [m.team1Id, m.team2Code, m.score1, m.score2],
      [m.team2Id, m.team1Code, m.score2, m.score1]
    ]) {
      if (!form.has(self)) form.set(self, [])
      form.get(self).push({ id: m.id, won: scored > conceded, title: `${scored}-${conceded} contre ${opp} (${m.date})` })
    }
  }
  return form
})

const hasForm = computed(() => formByTeam.value.size > 0)

function formOf(teamId) {
  return (formByTeam.value.get(teamId) ?? []).slice(-FORM_LENGTH)
}

function winRate(row) {
  const total = row.seriesWon + row.seriesLost
  return total ? Math.round((row.seriesWon / total) * 100) : 0
}

function gameDiff(row) {
  return row.gamesWon - row.gamesLost
}

function diffLabel(row) {
  const d = gameDiff(row)
  return d > 0 ? `+${d}` : `${d}`
}

function diffTone(row) {
  const d = gameDiff(row)
  if (d > 0) return 'pos'
  return d < 0 ? 'neg' : ''
}
</script>

<style scoped>
.standings td {
  padding-top: 9px;
  padding-bottom: 9px;
}
.num {
  text-align: center;
  white-space: nowrap;
}
.rank-col {
  width: 48px;
  text-align: center;
}
.rank {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 0.88em;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.04);
}
.rank-1 {
  background: linear-gradient(160deg, #ffe08a, #d19a24);
  color: #1b1405;
}
.rank-2 {
  background: linear-gradient(160deg, #eef1f6, #9aa3b2);
  color: #1b1d26;
}
.rank-3 {
  background: linear-gradient(160deg, #f0b27a, #9a5b2c);
  color: #1b1405;
}
.team {
  display: flex;
  align-items: center;
  gap: 12px;
}
.logo-box {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--logo-bg);
  font-size: 0.7em;
  font-weight: 800;
  color: var(--text-muted);
}
.logo-box img {
  width: 26px;
  height: 26px;
  object-fit: contain;
}
.team-name {
  font-weight: 700;
}
.record {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.05em;
  font-variant-numeric: tabular-nums;
}
.record .w {
  color: var(--win);
}
.record .l {
  color: var(--loss);
}
.record .sep {
  color: var(--text-dim);
  margin: 0 3px;
}
.rate-col {
  width: 180px;
}
.rate {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rate-bar {
  flex: 1;
  height: 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}
.rate-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent-grad);
}
.rate-value {
  width: 38px;
  text-align: right;
  font-size: 0.85em;
  font-weight: 700;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.games {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.diff {
  display: inline-block;
  min-width: 34px;
  margin-left: 6px;
  font-size: 0.78em;
  font-weight: 700;
  color: var(--text-dim);
}
.diff.pos {
  color: var(--win);
}
.diff.neg {
  color: var(--loss);
}
.form-col {
  white-space: nowrap;
}
.form {
  display: inline-flex;
  gap: 4px;
}
.pip {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  font-size: 0.68em;
  font-weight: 800;
}
.pip.won {
  color: #06281c;
  background: var(--win);
}
.pip.lost {
  color: #fff;
  background: #be123c;
}
@media (max-width: 720px) {
  .rate-col {
    display: none;
  }
}
</style>
