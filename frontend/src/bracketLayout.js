// Mise en page des brackets de playoffs : transforme la liste plate des matchs
// en "tableaux" (boards) positionnes au pixel pres, avec les traits qui relient
// chaque match a celui ou avance son vainqueur. Pur calcul, sans Vue, pour que
// le composant PlayoffBracket ne fasse que dessiner.

export const CARD_W = 236
export const CARD_H = 96
export const COL_GAP = 64
export const ROW_GAP = 18
export const LANE_HEAD = 56
export const LANE_GAP = 40

const SIDE_LABELS = {
  GROUP: 'Poules',
  SWISS_STAGE: 'Phase suisse',
  SEEDING: 'Seeding',
  PLACEMENT: 'Match de classement',
  PLAY_IN: 'Play-in',
  BRACKET: 'Tableau final',
  UPPER: 'Bracket vainqueurs',
  LOWER: 'Bracket perdants',
  GRAND_FINAL: 'Grande finale',
  REGIONAL_UPPER: 'Bracket vainqueurs',
  REGIONAL_LOWER: 'Bracket perdants',
  KNOCKOUT: 'Knockout Matches',
  AUTRE: 'Playoffs'
}
const SIDE_ORDER = ['GROUP', 'SWISS_STAGE', 'KNOCKOUT', 'PLACEMENT', 'SEEDING', 'PLAY_IN', 'AUTRE', 'BRACKET', 'UPPER', 'LOWER', 'GRAND_FINAL', 'REGIONAL_UPPER', 'REGIONAL_LOWER']

// Cotes reunis dans un meme tableau (les traits les relient entre eux, et la
// grande finale se place entre la finale du haut et celle du bas).
const BOARD_OF_SIDE = {
  AUTRE: 'MAIN',
  BRACKET: 'MAIN',
  UPPER: 'MAIN',
  LOWER: 'MAIN',
  GRAND_FINAL: 'MAIN',
  REGIONAL_UPPER: 'REGIONAL',
  REGIONAL_LOWER: 'REGIONAL'
}
const BOARD_LABELS = { MAIN: 'Phase finale', REGIONAL: 'Regional Finals' }

export function sideLabel(key) {
  const gsl = key.match(/^GSL_(\d+)$/)
  if (gsl) return `Groupe ${gsl[1]}`
  return SIDE_LABELS[key] ?? key
}

function sideRank(key) {
  const i = SIDE_ORDER.indexOf(key.startsWith('GSL_') ? 'GROUP' : key)
  return i === -1 ? SIDE_ORDER.length : i
}

function compareSides(a, b) {
  return sideRank(a) - sideRank(b) || a.localeCompare(b, undefined, { numeric: true })
}

// "Quarterfinal 1", "Quarterfinal 2"... sont des matchs du meme round : le
// numero ne sert qu'a les identifier, pas a les separer en colonnes.
export function roundColumn(label) {
  const lastSpace = label.lastIndexOf(' ')
  const lastWord = label.slice(lastSpace + 1)
  return lastSpace > 0 && /^\d+$/.test(lastWord) ? label.slice(0, lastSpace).trimEnd() : label
}

/**
 * Prefixe commun des rounds d'un meme cote ("Play-In UB R1", "Play-In LB
 * Final"... -> "Play-In ") : deja dit par le titre, on l'enleve des cartes.
 */
function roundPrefixes(matches) {
  const bySide = new Map()
  for (const m of matches) {
    const side = m.bracketSide || 'AUTRE'
    if (!bySide.has(side)) bySide.set(side, new Set())
    bySide.get(side).add(m.roundLabel)
  }
  const prefixes = new Map()
  for (const [side, labels] of bySide) {
    const words = [...labels].map(l => l.split(' '))
    let n = 0
    while (words.every(w => w.length > n + 1 && w[n] === words[0][n])) n++
    prefixes.set(side, labels.size > 1 && n ? words[0].slice(0, n).join(' ') + ' ' : '')
  }
  return prefixes
}

function shortRound(label, prefix) {
  return prefix && label.startsWith(prefix) ? label.slice(prefix.length) : label
}

export function kickoff(m) {
  return m.date + (m.time ?? '')
}

export function winnerSlot(m) {
  if (m.status !== 'COMPLETED' || m.score1 == null || m.score2 == null || m.score1 === m.score2) return null
  return m.score1 > m.score2 ? 1 : 2
}

function byKickoff(a, b) {
  return kickoff(a).localeCompare(kickoff(b)) || a.roundLabel.localeCompare(b.roundLabel, undefined, { numeric: true })
}

/**
 * Liens "le vainqueur de A joue B". Les liens saisis (nextMatchId) priment ;
 * a defaut on les deduit des equipes : si le match precedent d'une equipe
 * dans ce tableau est une victoire, c'est de la qu'elle vient. Beaucoup de
 * brackets importes n'ont pas leurs liens, sans ca ils seraient sans traits.
 */
function winnerLinks(matches) {
  const ids = new Set(matches.map(m => m.id))
  const links = []
  const hasExplicit = new Set()
  for (const m of matches) {
    if (m.nextMatchId != null && ids.has(m.nextMatchId)) {
      links.push({ from: m.id, to: m.nextMatchId, slot: m.nextMatchSlot })
      hasExplicit.add(m.id)
    }
  }
  const fedSlots = new Set(links.map(l => `${l.to}:${l.slot}`))

  const sorted = [...matches].sort(byKickoff)
  const lastMatchOf = new Map()
  for (const m of sorted) {
    for (const slot of [1, 2]) {
      const teamId = slot === 1 ? m.team1Id : m.team2Id
      if (teamId == null) continue
      const prev = lastMatchOf.get(teamId)
      if (prev && !hasExplicit.has(prev.id) && !fedSlots.has(`${m.id}:${slot}`)) {
        const prevTeamSlot = prev.team1Id === teamId ? 1 : 2
        if (winnerSlot(prev) === prevTeamSlot) {
          links.push({ from: prev.id, to: m.id, slot, inferred: true })
          hasExplicit.add(prev.id)
        }
      }
      lastMatchOf.set(teamId, m)
    }
  }
  return links
}

function loserLinks(matches) {
  const ids = new Set(matches.map(m => m.id))
  return matches
    .filter(m => m.loserNextMatchId != null && ids.has(m.loserNextMatchId))
    .map(m => ({ from: m.id, to: m.loserNextMatchId, slot: m.loserNextMatchSlot }))
}

/** Colonnes d'un cote : un round par colonne, et les rounds joues le meme jour cote a cote. */
function laneColumns(laneMatches) {
  const byRound = new Map()
  for (const m of laneMatches) {
    const key = roundColumn(m.roundLabel)
    if (!byRound.has(key)) byRound.set(key, [])
    byRound.get(key).push(m)
  }
  const rounds = [...byRound.entries()].map(([label, ms]) => ({
    labels: [label],
    start: ms.reduce((min, m) => (kickoff(m) < min ? kickoff(m) : min), kickoff(ms[0])),
    matches: ms
  })).sort((a, b) => a.start.localeCompare(b.start))

  const columns = []
  for (const r of rounds) {
    const last = columns.at(-1)
    if (last && last.start.slice(0, 10) === r.start.slice(0, 10)) {
      last.labels.push(...r.labels)
      last.matches.push(...r.matches)
    } else {
      columns.push({ ...r, labels: [...r.labels], matches: [...r.matches] })
    }
  }
  for (const c of columns) {
    c.matches.sort((a, b) => a.roundLabel.localeCompare(b.roundLabel, undefined, { numeric: true }) || byKickoff(a, b))
  }
  return columns
}

function mean(values) {
  return values.reduce((s, v) => s + v, 0) / values.length
}

/**
 * Positions verticales dans un cote : chaque match se centre entre les matchs
 * dont il recoit le vainqueur (a defaut le perdant), puis on decale vers le bas
 * ce qui se chevauche. Sans aucun lien, on suppose un arbre classique.
 */
function layoutLane(columns, winnersInto, losersInto) {
  const centerY = new Map()
  const step = CARD_H + ROW_GAP

  columns.forEach((col, ci) => {
    const prev = ci > 0 ? columns[ci - 1].matches : []
    const wanted = col.matches.map((m, i) => {
      const feeders = (winnersInto.get(m.id) ?? []).filter(id => centerY.has(id))
      if (feeders.length) return { m, y: mean(feeders.map(id => centerY.get(id))), rank: 0 }
      const loserFeeders = (losersInto.get(m.id) ?? []).filter(id => centerY.has(id))
      if (loserFeeders.length) return { m, y: mean(loserFeeders.map(id => centerY.get(id))), rank: 1 }
      if (prev.length && prev.length >= col.matches.length) {
        const per = prev.length / col.matches.length
        const block = prev.slice(Math.floor(i * per), Math.max(Math.floor((i + 1) * per), Math.floor(i * per) + 1))
        return { m, y: mean(block.map(p => centerY.get(p.id))), rank: 0 }
      }
      return { m, y: CARD_H / 2 + i * step, rank: 0 }
    })
    // A hauteur egale, le match du vainqueur passe au-dessus de celui du perdant.
    wanted.sort((a, b) => a.y - b.y || a.rank - b.rank)
    let minY = CARD_H / 2
    for (const w of wanted) {
      const y = Math.max(w.y, minY)
      centerY.set(w.m.id, y)
      minY = y + step
    }
  })
  return centerY
}

function groupBy(list, keyOf) {
  const map = new Map()
  for (const item of list) {
    const k = keyOf(item)
    if (!map.has(k)) map.set(k, [])
    map.get(k).push(item)
  }
  return map
}

function edgePath(a, b) {
  const x1 = a.x + CARD_W
  const y1 = a.y + CARD_H / 2
  const x2 = b.x
  const y2 = b.y + CARD_H / 2
  const midX = x1 + Math.max(16, (x2 - x1) / 2)
  const r = Math.min(10, Math.abs(y2 - y1) / 2, (x2 - x1) / 4)
  if (Math.abs(y2 - y1) < 1) return `M${x1},${y1} H${x2}`
  const dir = y2 > y1 ? 1 : -1
  return `M${x1},${y1} H${midX - r} Q${midX},${y1} ${midX},${y1 + dir * r} V${y2 - dir * r} Q${midX},${y2} ${midX + r},${y2} H${x2}`
}

function buildTreeBoard(key, label, matches) {
  const prefixes = roundPrefixes(matches)
  const winners = winnerLinks(matches)
  const losers = loserLinks(matches)
  const winnersInto = groupBy(winners, l => l.to)
  const losersInto = groupBy(losers, l => l.to)
  const winnerIdsInto = new Map([...winnersInto].map(([k, ls]) => [k, ls.map(l => l.from)]))
  const loserIdsInto = new Map([...losersInto].map(([k, ls]) => [k, ls.map(l => l.from)]))

  const sideGroups = groupBy(matches, m => m.bracketSide || 'AUTRE')
  const sideKeys = [...sideGroups.keys()].sort(compareSides)
  const colOf = new Map()
  const pos = new Map()
  const lanes = []
  let laneTop = 0
  let maxCol = 0

  const floating = []
  const mixedRound = new Set()
  for (const side of sideKeys) {
    const laneMatches = sideGroups.get(side)
    const columns = laneColumns(laneMatches)
    for (const c of columns) c.labels = c.labels.map(l => shortRound(l, prefixes.get(side)))

    // Un cote qui prolonge un autre (ex: grande finale, bracket haut apres des
    // rounds communs) commence a droite des matchs qui l'alimentent.
    const firstIds = columns[0].matches.map(m => m.id)
    const feederCols = firstIds.flatMap(id => (winnerIdsInto.get(id) ?? []).filter(f => colOf.has(f)).map(f => colOf.get(f)))
    const offset = feederCols.length ? Math.max(...feederCols) + 1 : 0
    columns.forEach((c, ci) => c.matches.forEach(m => {
      colOf.set(m.id, offset + ci)
      if (c.labels.length > 1) mixedRound.add(m.id)
    }))
    maxCol = Math.max(maxCol, offset + columns.length - 1)

    const isFinal = side === 'GRAND_FINAL' && columns.length === 1 && feederCols.length > 0
    if (isFinal) {
      floating.push({ side, columns, offset })
      continue
    }

    const centerY = layoutLane(columns, winnerIdsInto, loserIdsInto)
    const bodyTop = laneTop + LANE_HEAD
    let bottom = bodyTop
    for (const [id, cy] of centerY) {
      const y = bodyTop + cy - CARD_H / 2
      pos.set(id, { x: colOf.get(id) * (CARD_W + COL_GAP), y })
      bottom = Math.max(bottom, y + CARD_H)
    }
    lanes.push({
      key: side,
      label: sideLabel(side),
      top: laneTop,
      height: bottom - laneTop,
      width: (offset + columns.length) * (CARD_W + COL_GAP) - COL_GAP,
      columns: columns.map((c, ci) => ({ x: (offset + ci) * (CARD_W + COL_GAP), labels: c.labels, date: c.start.slice(0, 10) }))
    })
    laneTop = bottom + LANE_GAP
  }

  for (const f of floating) {
    const matchesOf = f.columns[0].matches
    let minY = 0
    for (const m of matchesOf) {
      const feeders = (winnerIdsInto.get(m.id) ?? []).filter(id => pos.has(id))
      const cy = mean(feeders.map(id => pos.get(id).y + CARD_H / 2))
      const y = Math.max(cy - CARD_H / 2, minY)
      pos.set(m.id, { x: f.offset * (CARD_W + COL_GAP), y })
      minY = y + CARD_H + ROW_GAP
    }
    lanes.push({
      key: f.side,
      label: sideLabel(f.side),
      floating: true,
      top: Math.min(...matchesOf.map(m => pos.get(m.id).y)) - LANE_HEAD,
      height: 0,
      columns: [{ x: f.offset * (CARD_W + COL_GAP), labels: f.columns[0].labels, date: f.columns[0].start.slice(0, 10) }]
    })
  }

  const byId = new Map(matches.map(m => [m.id, m]))
  const nodes = matches.map(m => ({
    match: m,
    round: mixedRound.has(m.id) ? shortRound(m.roundLabel, prefixes.get(m.bracketSide || 'AUTRE')) : null,
    ...pos.get(m.id)
  }))
  const edges = winners.filter(l => pos.get(l.to).x > pos.get(l.from).x).map(l => {
    const from = byId.get(l.from)
    const slot = winnerSlot(from)
    return {
      id: `${l.from}-${l.to}`,
      d: edgePath(pos.get(l.from), pos.get(l.to)),
      done: slot != null,
      teamId: slot === 1 ? from.team1Id : slot === 2 ? from.team2Id : null
    }
  })

  const width = (maxCol + 1) * CARD_W + maxCol * COL_GAP
  const height = Math.max(...nodes.map(n => n.y + CARD_H), 0)
  if (lanes.length === 1) lanes[0].hideTitle = true
  return { kind: 'tree', key, label, nodes, edges, lanes, width, height, champion: championOf(matches) }
}

function championOf(matches) {
  const final = matches.find(m => m.bracketSide === 'GRAND_FINAL')
  const slot = final && winnerSlot(final)
  if (!slot) return null
  return slot === 1
    ? { id: final.team1Id, code: final.team1Code, name: final.team1Name, runnerUp: final.team2Name, score: `${final.score1}-${final.score2}` }
    : { id: final.team2Id, code: final.team2Code, name: final.team2Name, runnerUp: final.team1Name, score: `${final.score2}-${final.score1}` }
}

/**
 * Phase suisse : une colonne par round, et dans chaque colonne les matchs
 * regroupes par bilan des equipes avant le match (2-0, 1-1, 0-2...), comme
 * sur les visuels officiels des Worlds.
 */
function buildSwissBoard(key, matches) {
  const record = new Map()
  const rec = id => record.get(id) ?? { w: 0, l: 0 }
  const rounds = new Map()
  for (const m of [...matches].sort(byKickoff)) {
    const round = roundColumn(m.roundLabel)
    if (!rounds.has(round)) rounds.set(round, new Map())
    const r = m.team1Id != null ? rec(m.team1Id) : null
    const bucket = r ? `${r.w}-${r.l}` : '?'
    const buckets = rounds.get(round)
    if (!buckets.has(bucket)) buckets.set(bucket, [])
    buckets.get(bucket).push(m)

    const slot = winnerSlot(m)
    if (slot && m.team1Id != null && m.team2Id != null) {
      const [w, l] = slot === 1 ? [m.team1Id, m.team2Id] : [m.team2Id, m.team1Id]
      record.set(w, { ...rec(w), w: rec(w).w + 1 })
      record.set(l, { ...rec(l), l: rec(l).l + 1 })
    }
  }
  const columns = [...rounds.entries()].map(([label, buckets]) => ({
    label,
    buckets: [...buckets.entries()]
      .map(([rec, ms]) => ({ record: rec, matches: ms }))
      .sort((a, b) => bucketRank(a.record) - bucketRank(b.record))
  }))
  return { kind: 'swiss', key, label: sideLabel(key), columns }
}

function bucketRank(r) {
  if (r === '?') return Infinity
  const [w, l] = r.split('-').map(Number)
  return l - w
}

/** Tous les tableaux a afficher, dans l'ordre de la competition. */
export function buildBoards(matches) {
  const boardGroups = groupBy(matches, m => {
    const side = m.bracketSide || 'AUTRE'
    return BOARD_OF_SIDE[side] ?? side
  })
  const firstSide = key => [...new Set(boardGroups.get(key).map(m => m.bracketSide || 'AUTRE'))].sort(compareSides)[0]
  const keys = [...boardGroups.keys()].sort((a, b) => compareSides(firstSide(a), firstSide(b)))

  return keys.map(key => {
    const ms = boardGroups.get(key)
    if (key === 'SWISS_STAGE') return buildSwissBoard(key, ms)
    return buildTreeBoard(key, BOARD_LABELS[key] ?? sideLabel(key), ms)
  })
}

/**
 * Libelle d'une place encore vide : "Vainq. Semifinal 1" plutot que
 * "A determiner", pour comprendre d'ou viendra l'equipe.
 */
export function slotSources(matches) {
  const prefixes = roundPrefixes(matches)
  // Deux matchs au meme libelle ("UB R1") : on les numerote dans l'ordre
  const sameLabel = groupBy([...matches].sort(byKickoff), m => `${m.bracketSide}|${m.roundLabel}`)
  const name = m => {
    const twins = sameLabel.get(`${m.bracketSide}|${m.roundLabel}`)
    const short = shortRound(m.roundLabel, prefixes.get(m.bracketSide || 'AUTRE'))
    return twins.length > 1 ? `${short} #${twins.indexOf(m) + 1}` : short
  }
  const sources = new Map()
  for (const m of matches) {
    if (m.nextMatchId != null) sources.set(`${m.nextMatchId}:${m.nextMatchSlot}`, `Vainqueur ${name(m)}`)
    if (m.loserNextMatchId != null) sources.set(`${m.loserNextMatchId}:${m.loserNextMatchSlot}`, `Perdant ${name(m)}`)
  }
  return sources
}
