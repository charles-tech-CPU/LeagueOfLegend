<template>
  <div v-if="matches.length" class="bracket">
    <p class="hint">
      <span class="hint-icon">💡</span>
      Survole une équipe pour suivre son parcours. Les traits relient chaque match à celui où
      <strong>le vainqueur</strong> avance ; une place vide indique d'où viendra l'équipe.
    </p>

    <section v-for="board in boards" :key="board.key" class="board">
      <header class="board-head">
        <h3>{{ board.label }}</h3>
        <span class="board-count">{{ boardProgress(board) }}</span>
      </header>

      <div v-if="board.champion" class="champion">
        <span class="trophy">🏆</span>
        <span class="logo-box">
          <img v-if="logos.has(board.champion.id)" :src="teamLogoUrl(board.champion.id)" :alt="board.champion.code" />
        </span>
        <span class="champion-text">
          <span class="champion-label">Champion</span>
          <span class="champion-name">{{ board.champion.name }}</span>
        </span>
        <span class="champion-score">{{ board.champion.score }} contre {{ board.champion.runnerUp }}</span>
      </div>

      <!-- Arbre a elimination : cartes positionnees + traits SVG -->
      <div v-if="board.kind === 'tree'" class="board-scroll">
        <div class="canvas" :style="{ width: `${board.width}px`, height: `${board.height}px` }">
          <template v-for="lane in board.lanes" :key="lane.key">
            <div
              v-if="!lane.floating"
              class="lane-band"
              :class="laneTone(lane.key)"
              :style="{ top: `${lane.top}px`, height: `${lane.height + 12}px`, width: `${lane.width + 24}px` }"
            ></div>
            <div v-if="!lane.hideTitle" class="lane-title" :class="laneTone(lane.key)" :style="{ top: `${lane.top + 8}px`, left: `${lane.floating ? lane.columns[0].x : 0}px` }">
              {{ lane.label }}
            </div>
            <template v-if="!lane.floating">
              <div
                v-for="col in lane.columns"
                :key="col.x"
                class="col-title"
                :style="{ top: `${lane.top + 34}px`, left: `${col.x}px`, width: `${CARD_W}px` }"
              >
                {{ col.labels.join(' · ') }}
              </div>
            </template>
          </template>

          <svg class="edges" :width="board.width" :height="board.height" aria-hidden="true">
            <path
              v-for="e in board.edges"
              :key="e.id"
              :d="e.d"
              :class="{ done: e.done, lit: hovered != null && e.teamId === hovered }"
            />
          </svg>

          <BracketMatchCard
            v-for="n in board.nodes"
            :key="n.match.id"
            class="node"
            :style="{ left: `${n.x}px`, top: `${n.y}px` }"
            :match="n.match"
            :round="n.round"
            :logos="logos"
            :sources="sources"
            :highlight="hovered"
            :width="CARD_W"
            :height="CARD_H"
            @hover="hovered = $event"
          />
        </div>
      </div>

      <!-- Phase suisse : colonnes par round, matchs regroupes par bilan -->
      <div v-else class="board-scroll">
        <div class="swiss">
          <div v-for="col in board.columns" :key="col.label" class="swiss-col">
            <div class="col-title">{{ col.label }}</div>
            <div v-for="b in col.buckets" :key="b.record" class="bucket">
              <div class="bucket-head">
                <span class="record" :class="recordTone(b.record)">{{ b.record === '?' ? 'Bilan à venir' : b.record }}</span>
                <span class="bucket-count">{{ b.matches.length }} match{{ b.matches.length > 1 ? 's' : '' }}</span>
              </div>
              <BracketMatchCard
                v-for="m in b.matches"
                :key="m.id"
                :match="m"
                :logos="logos"
                :sources="sources"
                :highlight="hovered"
                :height="CARD_H"
                @hover="hovered = $event"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
  <p v-else class="muted">Aucun match de playoffs pour l'instant.</p>
</template>

<script setup>
import { computed, ref } from 'vue'
import { teamLogoUrl } from '../services/api'
import { CARD_H, CARD_W, buildBoards, slotSources } from '../bracketLayout'
import BracketMatchCard from './BracketMatchCard.vue'

const props = defineProps({
  matches: { type: Array, default: () => [] },
  teams: { type: Array, default: () => [] }
})

const hovered = ref(null)

const logos = computed(() => new Set(props.teams.filter(t => t.hasLogo).map(t => t.id)))
const boards = computed(() => buildBoards(props.matches))
const sources = computed(() => slotSources(props.matches))

function boardMatches(board) {
  if (board.kind === 'tree') return board.nodes.map(n => n.match)
  return board.columns.flatMap(c => c.buckets.flatMap(b => b.matches))
}

function boardProgress(board) {
  const ms = boardMatches(board)
  const done = ms.filter(m => m.status === 'COMPLETED').length
  return `${done}/${ms.length} joués`
}

function laneTone(side) {
  if (side === 'GRAND_FINAL') return 'tone-gold'
  if (side.includes('LOWER')) return 'tone-rose'
  if (side.includes('UPPER') || side === 'BRACKET') return 'tone-cyan'
  return 'tone-violet'
}

function recordTone(record) {
  if (record === '?') return ''
  const [w, l] = record.split('-').map(Number)
  if (w > l) return 'up'
  if (w < l) return 'down'
  return ''
}
</script>

<style scoped>
.hint {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 0.86em;
  color: var(--text-muted);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: rgba(129, 140, 248, 0.08);
  border: 1px solid rgba(129, 140, 248, 0.2);
  margin: 0 0 22px;
}
.hint strong {
  color: var(--text);
}
.board {
  margin-bottom: 36px;
}
.board-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.board-head h3 {
  margin: 0;
  font-size: 1.15em;
  font-weight: 800;
}
.board-count {
  font-size: 0.78em;
  color: var(--text-dim);
  font-weight: 600;
}
.board-scroll {
  overflow-x: auto;
  padding: 4px 14px 14px;
  margin: 0 -14px;
}

/* ----- Champion ----- */
.champion {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 10px 18px 10px 12px;
  margin-bottom: 16px;
  border-radius: 999px;
  background: linear-gradient(100deg, rgba(245, 196, 81, 0.2), rgba(245, 196, 81, 0.04));
  border: 1px solid rgba(245, 196, 81, 0.45);
  box-shadow: 0 10px 30px -14px rgba(245, 196, 81, 0.7);
}
.trophy {
  font-size: 1.5em;
}
.champion .logo-box {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--logo-bg);
}
.champion .logo-box img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.champion-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.champion-label {
  font-size: 0.68em;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: 700;
  color: var(--gold);
}
.champion-name {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 1.1em;
  color: var(--gold-bright);
}
.champion-score {
  font-size: 0.8em;
  color: var(--text-muted);
  margin-left: 6px;
}

/* ----- Arbre ----- */
.canvas {
  position: relative;
}
.lane-band {
  position: absolute;
  left: -12px;
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--tone) 4%, transparent);
  border: 1px solid color-mix(in srgb, var(--tone) 14%, transparent);
}
.lane-title {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: "Outfit", sans-serif;
  font-weight: 700;
  font-size: 0.9em;
  color: var(--tone);
  white-space: nowrap;
}
.lane-title::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tone);
  box-shadow: 0 0 10px var(--tone);
}
.tone-cyan { --tone: #22d3ee; }
.tone-rose { --tone: #fb7185; }
.tone-gold { --tone: #f5c451; }
.tone-violet { --tone: #a78bfa; }

.col-title {
  position: absolute;
  font-size: 0.68em;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edges {
  position: absolute;
  inset: 0;
  overflow: visible;
  pointer-events: none;
}
.edges path {
  fill: none;
  stroke: var(--border-strong);
  stroke-width: 2;
  stroke-dasharray: 5 5;
  transition: stroke 0.18s, stroke-width 0.18s;
}
.edges path.done {
  stroke: #4b5585;
  stroke-dasharray: none;
}
.edges path.lit {
  stroke: var(--accent-2);
  stroke-width: 3;
  filter: drop-shadow(0 0 6px rgba(167, 139, 250, 0.8));
}
.node {
  position: absolute;
}

/* ----- Phase suisse ----- */
.swiss {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}
.swiss-col {
  flex: 0 0 236px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.swiss .col-title {
  position: static;
  padding-bottom: 2px;
}
.bucket {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border);
}
.bucket-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.record {
  font-family: "Outfit", sans-serif;
  font-weight: 800;
  font-size: 0.85em;
  padding: 2px 10px;
  border-radius: 999px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.06);
}
.record.up {
  color: var(--win);
  background: rgba(52, 211, 153, 0.12);
}
.record.down {
  color: var(--loss);
  background: rgba(251, 113, 133, 0.12);
}
.bucket-count {
  font-size: 0.72em;
  color: var(--text-dim);
}
</style>
