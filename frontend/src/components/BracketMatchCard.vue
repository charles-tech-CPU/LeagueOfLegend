<template>
  <article
    class="match-card"
    :class="{ done: winner != null, today: isToday, dimmed: highlight != null && !involves(highlight), focus: highlight != null && involves(highlight) }"
    :style="width ? { width: `${width}px`, height: `${height}px` } : null"
  >
    <header class="card-head">
      <span class="meta">
        <span v-if="round" class="round">{{ round }}</span>
        <span v-if="isToday" class="live">● Aujourd'hui</span>
        <span v-else>{{ shortDay(match.date) }}</span>
        <span v-if="time">· {{ time }}</span>
      </span>
      <span class="bo">{{ match.bestOf }}</span>
    </header>

    <div
      v-for="slot in [1, 2]"
      :key="slot"
      class="team-row"
      :class="{ winner: winner === slot, loser: winner != null && winner !== slot, tbd: teamId(slot) == null }"
      @mouseenter="teamId(slot) != null && emit('hover', teamId(slot))"
      @mouseleave="emit('hover', null)"
    >
      <span class="logo-box">
        <img v-if="hasLogo(teamId(slot))" :src="teamLogoUrl(teamId(slot))" :alt="teamCode(slot)" />
        <span v-else class="logo-fallback">{{ teamId(slot) == null ? '?' : teamCode(slot).slice(0, 2) }}</span>
      </span>
      <span class="names">
        <template v-if="teamId(slot) != null">
          <span class="code">{{ teamCode(slot) }}</span>
          <span class="full">{{ teamName(slot) }}</span>
        </template>
        <span v-else class="source">{{ sources.get(`${match.id}:${slot}`) ?? 'À déterminer' }}</span>
      </span>
      <span class="score">{{ score(slot) ?? '' }}</span>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { teamLogoUrl } from '../services/api'
import { winnerSlot } from '../bracketLayout'
import { localIsoDate, shortDay } from '../format'
import { formatTime } from '../matchEdit'

const props = defineProps({
  match: { type: Object, required: true },
  // Utile quand la colonne melange plusieurs rounds (sinon son titre suffit)
  round: { type: String, default: null },
  logos: { type: Set, default: () => new Set() },
  sources: { type: Map, default: () => new Map() },
  highlight: { type: Number, default: null },
  width: { type: Number, default: null },
  height: { type: Number, default: null }
})

const emit = defineEmits(['hover'])

const winner = computed(() => winnerSlot(props.match))
const time = computed(() => formatTime(props.match.time))
const isToday = computed(() => props.match.status !== 'COMPLETED' && props.match.date === localIsoDate(new Date()))

function teamId(slot) {
  return slot === 1 ? props.match.team1Id : props.match.team2Id
}
function teamCode(slot) {
  return (slot === 1 ? props.match.team1Code : props.match.team2Code) ?? ''
}
function teamName(slot) {
  return (slot === 1 ? props.match.team1Name : props.match.team2Name) ?? ''
}
function score(slot) {
  return slot === 1 ? props.match.score1 : props.match.score2
}
function hasLogo(id) {
  return id != null && props.logos.has(id)
}
function involves(id) {
  return props.match.team1Id === id || props.match.team2Id === id
}
</script>

<style scoped>
.match-card {
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
  transition: opacity 0.18s, border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}
.match-card:hover {
  border-color: var(--border-strong);
  transform: translateY(-1px);
}
.match-card.today {
  border-color: rgba(34, 211, 238, 0.55);
  box-shadow: var(--glow);
}
.match-card.dimmed {
  opacity: 0.28;
}
.match-card.focus {
  border-color: var(--accent-2);
  box-shadow: 0 0 0 1px var(--accent-2), 0 10px 30px -10px rgba(167, 139, 250, 0.7);
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 26px;
  padding: 0 10px;
  font-size: 0.7em;
  background: var(--panel-alt);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.round {
  font-weight: 700;
  color: var(--text-muted);
  margin-right: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  color: var(--text-dim);
}
.live {
  color: var(--accent);
  font-weight: 700;
}
.bo {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 6px;
  font-weight: 700;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.06);
}
.team-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 0 0 10px;
  position: relative;
  min-height: 0;
  cursor: default;
}
.team-row + .team-row {
  border-top: 1px solid var(--border);
}
.team-row.winner::before {
  content: "";
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--gold);
}
.team-row.loser .names,
.team-row.loser .logo-box {
  opacity: 0.5;
}
.logo-box {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--logo-bg);
  overflow: hidden;
}
.logo-box img {
  width: 22px;
  height: 22px;
  object-fit: contain;
}
.logo-fallback {
  font-size: 0.66em;
  font-weight: 800;
  color: var(--text-muted);
}
.tbd .logo-box {
  background: transparent;
  border: 1px dashed var(--border-strong);
}
.tbd .logo-fallback {
  color: var(--text-dim);
}
.names {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 7px;
  overflow: hidden;
  white-space: nowrap;
}
.code {
  font-weight: 800;
  font-size: 0.92em;
  color: var(--text);
}
.full {
  font-size: 0.74em;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
}
.winner .code {
  color: var(--gold-bright);
}
.source {
  font-size: 0.78em;
  font-style: italic;
  color: var(--text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
}
.score {
  align-self: stretch;
  display: grid;
  place-items: center;
  width: 38px;
  flex-shrink: 0;
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.05em;
  font-variant-numeric: tabular-nums;
  color: var(--text-dim);
  background: rgba(255, 255, 255, 0.025);
  border-left: 1px solid var(--border);
}
.winner .score {
  color: #1b1405;
  background: linear-gradient(180deg, var(--gold-bright), var(--gold));
  border-left-color: transparent;
}
</style>
