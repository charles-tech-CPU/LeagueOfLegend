<template>
  <div v-if="matches.length" class="bracket">
    <div v-for="side in sides" :key="side.key" class="bracket-side">
      <h3>{{ side.label }}</h3>

      <div v-if="side.isTable" class="side-table-scroll">
        <table class="side-table">
          <thead>
            <tr>
              <th>Round</th>
              <th>Date</th>
              <th>Équipe 1</th>
              <th></th>
              <th>Équipe 2</th>
              <th>Statut</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in side.flatMatches" :key="m.id">
              <td class="round-cell">{{ m.roundLabel }}</td>
              <td class="date-cell">{{ m.date }}</td>
              <td class="team-cell" :class="{ winner: isWinner(m, 1) }">
                <img v-if="m.team1HasLogo" class="team-logo" :src="teamLogoUrl(m.team1Id)" :alt="m.team1Code" />
                <span>{{ m.team1Code ?? 'À déterminer' }}</span>
              </td>
              <td class="score-cell">
                <span :class="{ winner: isWinner(m, 1) }">{{ m.score1 ?? '–' }}</span>
                <span class="score-sep">-</span>
                <span :class="{ winner: isWinner(m, 2) }">{{ m.score2 ?? '–' }}</span>
              </td>
              <td class="team-cell" :class="{ winner: isWinner(m, 2) }">
                <img v-if="m.team2HasLogo" class="team-logo" :src="teamLogoUrl(m.team2Id)" :alt="m.team2Code" />
                <span>{{ m.team2Code ?? 'À déterminer' }}</span>
              </td>
              <td>
                <span class="status-badge" :class="m.status === 'COMPLETED' ? 'completed' : 'scheduled'">
                  {{ m.status === 'COMPLETED' ? 'Joué' : 'À venir' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="bracket-rounds">
        <div v-for="col in side.rounds" :key="col.roundLabel" class="bracket-round">
          <div class="round-title">{{ col.roundLabel }}</div>
          <div v-for="m in col.matches" :key="m.id" class="bracket-match">
            <div class="bracket-team" :class="{ winner: isWinner(m, 1) }">
              <img v-if="m.team1HasLogo" class="team-logo" :src="teamLogoUrl(m.team1Id)" :alt="m.team1Code" />
              <span class="team-code">{{ m.team1Code ?? 'À déterminer' }}</span>
              <span class="team-score">{{ m.score1 ?? '' }}</span>
            </div>
            <div class="bracket-team" :class="{ winner: isWinner(m, 2) }">
              <img v-if="m.team2HasLogo" class="team-logo" :src="teamLogoUrl(m.team2Id)" :alt="m.team2Code" />
              <span class="team-code">{{ m.team2Code ?? 'À déterminer' }}</span>
              <span class="team-score">{{ m.score2 ?? '' }}</span>
            </div>
            <div class="bracket-meta">{{ m.bestOf }} · {{ m.status === 'COMPLETED' ? 'Joué' : 'À venir' }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <p v-else>Aucun match de playoffs pour l'instant.</p>
</template>

<script setup>
import { computed } from 'vue'
import { teamLogoUrl } from '../services/api'

const props = defineProps({
  matches: { type: Array, default: () => [] },
  teams: { type: Array, default: () => [] }
})

const SIDE_LABELS = {
  GROUP: 'Poules',
  SWISS_STAGE: 'Phase suisse',
  SEEDING: 'Seeding',
  PLACEMENT: 'Match de classement',
  PLAY_IN: 'Play-in',
  BRACKET: 'Bracket',
  UPPER: 'Bracket vainqueurs',
  LOWER: 'Bracket perdants',
  GRAND_FINAL: 'Grande finale',
  REGIONAL_UPPER: 'Bracket vainqueurs',
  REGIONAL_LOWER: 'Bracket perdants',
  AUTRE: 'Playoffs'
}
const SIDE_ORDER = ['GROUP', 'SWISS_STAGE', 'PLACEMENT', 'SEEDING', 'PLAY_IN', 'BRACKET', 'UPPER', 'LOWER', 'GRAND_FINAL', 'REGIONAL_UPPER', 'REGIONAL_LOWER']

// Cotes sans arbre d'avancement (poules, phase suisse, seeding, classement) :
// l'affichage en colonnes horizontales par round n'a de sens que pour un
// vrai bracket a elimination (UPPER/LOWER/GRAND_FINAL/PLAY_IN/...). Ailleurs,
// un simple tableau qui descend se lit bien mieux (moins large, chronologique).
const TABLE_SIDES = new Set(['GROUP', 'SWISS_STAGE', 'SEEDING', 'PLACEMENT'])

function teamHasLogoLookup(teams) {
  const byId = new Map(teams.map(t => [t.id, t.hasLogo]))
  return id => byId.get(id) ?? false
}

const sides = computed(() => {
  const hasLogo = teamHasLogoLookup(props.teams)

  const enriched = props.matches.map(m => ({
    ...m,
    team1HasLogo: m.team1Id != null && hasLogo(m.team1Id),
    team2HasLogo: m.team2Id != null && hasLogo(m.team2Id)
  }))

  const sideGroups = new Map()
  for (const m of enriched) {
    const sideKey = m.bracketSide || 'AUTRE'
    if (!sideGroups.has(sideKey)) sideGroups.set(sideKey, [])
    sideGroups.get(sideKey).push(m)
  }

  const sideKeys = [...sideGroups.keys()].sort((a, b) => {
    const ia = SIDE_ORDER.indexOf(a)
    const ib = SIDE_ORDER.indexOf(b)
    if (ia === -1 && ib === -1) return a.localeCompare(b)
    if (ia === -1) return 1
    if (ib === -1) return -1
    return ia - ib
  })

  return sideKeys.map(key => {
    const sideMatches = sideGroups.get(key)
    const isTable = TABLE_SIDES.has(key)

    if (isTable) {
      const flatMatches = [...sideMatches].sort((a, b) => (a.date + (a.time ?? '')).localeCompare(b.date + (b.time ?? '')))
      return { key, label: SIDE_LABELS[key] ?? key, isTable, flatMatches }
    }

    const roundGroups = new Map()
    for (const m of sideMatches) {
      if (!roundGroups.has(m.roundLabel)) roundGroups.set(m.roundLabel, [])
      roundGroups.get(m.roundLabel).push(m)
    }
    const rounds = [...roundGroups.entries()]
      .map(([roundLabel, ms]) => ({
        roundLabel,
        minDate: ms.reduce((min, m) => (m.date < min ? m.date : min), ms[0].date),
        matches: ms.sort((a, b) => (a.date + (a.time ?? '')).localeCompare(b.date + (b.time ?? '')))
      }))
      .sort((a, b) => a.minDate.localeCompare(b.minDate))

    return { key, label: SIDE_LABELS[key] ?? key, isTable, rounds }
  })
})

function isWinner(match, slot) {
  if (match.status !== 'COMPLETED') return false
  if (slot === 1) return match.score1 > match.score2
  return match.score2 > match.score1
}
</script>

<style scoped>
.bracket-side {
  margin-bottom: 28px;
}
.bracket-side h3 {
  margin-bottom: 10px;
  font-size: 1em;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--gold);
}
.bracket-rounds {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}
.bracket-round {
  min-width: 190px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.round-title {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.75em;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.bracket-match {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 10px;
  background: var(--panel);
  box-shadow: var(--shadow);
}
.bracket-team {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}
.bracket-team.winner .team-code {
  font-weight: 700;
  color: var(--gold-bright);
}
.bracket-team.winner .team-score {
  color: var(--gold-bright);
}
.team-logo {
  height: 18px;
  width: auto;
  max-width: 48px;
  object-fit: contain;
  padding: 2px;
  border-radius: 4px;
  background: rgba(240, 230, 210, 0.9);
}
.team-code {
  flex: 1;
  color: var(--text);
}
.team-score {
  min-width: 1.5em;
  text-align: right;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}
.bracket-meta {
  color: var(--text-dim);
  font-size: 0.72em;
  margin-top: 6px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.side-table-scroll {
  overflow-x: auto;
}
.side-table {
  border-collapse: collapse;
  width: 100%;
  max-width: 640px;
}
.side-table th,
.side-table td {
  padding: 8px 10px;
  font-size: 0.88em;
  border-bottom: 1px solid var(--border);
  text-align: left;
}
.side-table th {
  color: var(--text-muted);
  font-weight: 700;
  font-size: 0.72em;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}
.round-cell {
  color: var(--text-muted);
  font-weight: 700;
  white-space: nowrap;
}
.date-cell {
  color: var(--text-dim);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.side-table .team-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.side-table .team-cell.winner {
  font-weight: 700;
  color: var(--gold-bright);
}
.side-table .score-cell {
  text-align: center;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.side-table .score-cell .winner {
  color: var(--gold-bright);
}
.score-sep {
  color: var(--text-dim);
  margin: 0 4px;
}
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.75em;
  font-weight: 700;
  white-space: nowrap;
}
.status-badge.completed {
  color: #7fe0a0;
  background: rgba(63, 185, 110, 0.16);
}
.status-badge.scheduled {
  color: var(--text-muted);
  background: rgba(200, 170, 110, 0.12);
}
</style>
