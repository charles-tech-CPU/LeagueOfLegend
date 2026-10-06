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
// En dessous, la carte n'affiche plus que le code des equipes (voir BracketMatchCard)
export const MIN_CARD_W = 150
const MIN_COL_GAP = 28

/**
 * Largeur des cartes et ecart entre colonnes pour que `cols` colonnes tiennent
 * dans `avail` px : on reduit les deux dans la meme proportion, sans descendre
 * sous les minimums (au-dela, le tableau defile horizontalement).
 */
export function fitGeometry(cols, avail) {
  const full = cols * CARD_W + (cols - 1) * COL_GAP
  const ratio = avail > 0 ? Math.min(1, avail / full) : 1
  const cardW = Math.max(MIN_CARD_W, Math.floor(CARD_W * ratio))
  // Cartes au minimum : l'ecart prend ce qui reste plutot que de deborder
  const leftover = cols > 1 ? Math.floor((avail - cols * cardW) / (cols - 1)) : COL_GAP
  return { cardW, colGap: Math.max(MIN_COL_GAP, Math.min(Math.floor(COL_GAP * ratio), leftover)) }
}

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
  TIEBREAKER: 'Matchs de départage',
  AUTRE: 'Playoffs'
}
const SIDE_ORDER = ['GROUP', 'SWISS_STAGE', 'TIEBREAKER', 'KNOCKOUT', 'PLACEMENT', 'SEEDING', 'PLAY_IN', 'AUTRE', 'BRACKET', 'UPPER', 'LOWER', 'GRAND_FINAL', 'REGIONAL_UPPER', 'REGIONAL_LOWER']

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

// Mots dont le numero qui suit designe le round lui-meme ("Round 2", "Day 3").
const ROUND_WORDS = /^(round|day|week|stage|phase|semaine|jour|tour)$/i

// "Quarterfinal 1", "Quarterfinal 2"... sont des matchs du meme round : le
// numero ne sert qu'a les identifier, pas a les separer en colonnes. Sauf
// apres "Round", "Day"... ou il numerote le round ("Round 1" -> "Round 2" ;
// "Round 3 1" et "Round 3 2" restent ensemble).
export function roundColumn(label) {
  const words = label.trim().split(/\s+/)
  const last = words.at(-1)
  if (words.length < 2 || !/^\d+$/.test(last) || ROUND_WORDS.test(words.at(-2))) return label
  return words.slice(0, -1).join(' ')
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
    // "Round 1", "Round 2" : sans le prefixe il ne resterait qu'un numero
    while (n > 0 && words.some(w => /^\d/.test(w[n]))) n--
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

function byText(a, b) {
  return a.localeCompare(b)
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
  const links = matches
    .filter(m => m.nextMatchId != null && ids.has(m.nextMatchId))
    .map(m => ({ from: m.id, to: m.nextMatchId, slot: m.nextMatchSlot }))
  const linked = new Set(links.map(l => l.from))
  const fedSlots = new Set(links.map(l => `${l.to}:${l.slot}`))

  const lastMatchOf = new Map()
  for (const m of [...matches].sort(byKickoff)) {
    for (const slot of [1, 2]) {
      const teamId = teamIdAt(m, slot)
      if (teamId == null) continue
      const prev = lastMatchOf.get(teamId)
      lastMatchOf.set(teamId, m)
      if (!prev || linked.has(prev.id) || fedSlots.has(`${m.id}:${slot}`)) continue
      if (teamIdAt(prev, winnerSlot(prev)) === teamId) {
        links.push({ from: prev.id, to: m.id, slot, inferred: true })
        linked.add(prev.id)
      }
    }
  }
  return links
}

/** Equipe a la place 1 ou 2 d'un match (null si place vide ou inconnue). */
function teamIdAt(match, slot) {
  if (slot === 1) return match.team1Id
  if (slot === 2) return match.team2Id
  return null
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
    start: ms.map(kickoff).sort(byText)[0],
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

/**
 * Un match se place toujours a droite des matchs dont il recoit le vainqueur. Les
 * libelles et les dates ne suffisent pas : libelles par journee ("Day 2" = quart puis
 * demi le meme jour), gauntlet ou tous les matchs s'appellent "Demi-finale", dates
 * importees incoherentes. Les colonnes trop chargees sont donc redecoupees.
 */
function orderColumnsByLinks(columns, winnerIdsInto) {
  const colOf = new Map()
  columns.forEach((c, ci) => c.matches.forEach(m => colOf.set(m.id, ci)))
  const all = columns.flatMap(c => c.matches)
  for (let pass = 0, changed = true; changed && pass < all.length; pass++) {
    changed = false
    for (const m of all) {
      for (const f of winnerIdsInto.get(m.id) ?? []) {
        if (colOf.has(f) && colOf.get(f) >= colOf.get(m.id)) {
          colOf.set(m.id, colOf.get(f) + 1)
          changed = true
        }
      }
    }
  }
  const byCol = groupBy(all, m => colOf.get(m.id))
  return [...byCol.keys()].sort((a, b) => a - b).map(ci => {
    const ms = byCol.get(ci)
    return {
      labels: [...new Set(ms.map(m => roundColumn(m.roundLabel)))],
      start: ms.map(kickoff).sort(byText)[0],
      matches: ms
    }
  })
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

function edgePath(a, b, cardW) {
  const x1 = a.x + cardW
  const y1 = a.y + CARD_H / 2
  const x2 = b.x
  const y2 = b.y + CARD_H / 2
  const midX = x1 + Math.max(16, (x2 - x1) / 2)
  const r = Math.min(10, Math.abs(y2 - y1) / 2, (x2 - x1) / 4)
  if (Math.abs(y2 - y1) < 1) return `M${x1},${y1} H${x2}`
  const dir = y2 > y1 ? 1 : -1
  return `M${x1},${y1} H${midX - r} Q${midX},${y1} ${midX},${y1 + dir * r} V${y2 - dir * r} Q${midX},${y2} ${midX + r},${y2} H${x2}`
}

/**
 * Ordre de traitement des cotes : l'ordre habituel (haut, bas, finale...), mais un
 * cote passe toujours apres ceux dont il recoit des vainqueurs, pour se placer a
 * leur droite. En cas de boucle, l'ordre habituel l'emporte.
 */
function orderSides(sides, winners, matches) {
  const sideOf = new Map(matches.map(m => [m.id, m.bracketSide || 'AUTRE']))
  const feeders = new Map(sides.map(s => [s, new Set()]))
  for (const l of winners) {
    const from = sideOf.get(l.from)
    const to = sideOf.get(l.to)
    if (from !== to) feeders.get(to).add(from)
  }
  const ordered = []
  const done = new Set()
  while (ordered.length < sides.length) {
    const ready = sides.find(s => !done.has(s) && [...feeders.get(s)].every(f => done.has(f)))
    const next = ready ?? sides.find(s => !done.has(s))
    ordered.push(next)
    done.add(next)
  }
  return ordered
}

/**
 * Colonne de chaque round d'un cote. Un cote qui en prolonge un autre (ex: grande
 * finale, bracket haut apres des rounds communs) se place a droite des matchs qui
 * l'alimentent : chaque colonne (et les suivantes) se decale si un de ses matchs recoit
 * le vainqueur d'un match deja place plus a droite dans un autre cote.
 */
function placeLaneColumns(columns, winnerIdsInto, colOf, mixedRound) {
  const colIndex = []
  let fedFromOtherSide = false
  columns.forEach((c, ci) => {
    const feederCols = c.matches.flatMap(m => (winnerIdsInto.get(m.id) ?? []).filter(f => colOf.has(f)).map(f => colOf.get(f)))
    if (ci === 0) fedFromOtherSide = feederCols.length > 0
    const previous = ci > 0 ? colIndex[ci - 1] + 1 : 0
    colIndex.push(Math.max(previous, ...feederCols.map(col => col + 1)))
    for (const m of c.matches) {
      colOf.set(m.id, colIndex[ci])
      if (c.labels.length > 1) mixedRound.add(m.id)
    }
  })
  return { colIndex, fedFromOtherSide }
}

/** Finale placee a hauteur de ses demi-finales, sans chevaucher une carte de sa colonne. */
function placeFloatingFinal(matchesOf, x, winnerIdsInto, pos) {
  const step = CARD_H + ROW_GAP
  let minY = 0
  for (const m of matchesOf) {
    const feeders = (winnerIdsInto.get(m.id) ?? []).filter(id => pos.has(id))
    const cy = mean(feeders.map(id => pos.get(id).y + CARD_H / 2))
    let y = Math.max(cy - CARD_H / 2, minY)
    const sameColumn = [...pos.values()].filter(p => p.x === x).sort((a, b) => a.y - b.y)
    for (const p of sameColumn) {
      if (y < p.y + step && y + step > p.y) y = p.y + step
    }
    pos.set(m.id, { x, y })
    minY = y + step
  }
}

function buildTreeBoard(key, label, matches, geometry) {
  const { cardW, colGap } = geometry
  const colX = col => col * (cardW + colGap)
  const prefixes = roundPrefixes(matches)
  const winners = winnerLinks(matches)
  const losers = loserLinks(matches)
  const winnersInto = groupBy(winners, l => l.to)
  const losersInto = groupBy(losers, l => l.to)
  const winnerIdsInto = new Map([...winnersInto].map(([k, ls]) => [k, ls.map(l => l.from)]))
  const loserIdsInto = new Map([...losersInto].map(([k, ls]) => [k, ls.map(l => l.from)]))

  const sideGroups = groupBy(matches, m => m.bracketSide || 'AUTRE')
  const sideKeys = orderSides([...sideGroups.keys()].sort(compareSides), winners, matches)
  const colOf = new Map()
  const pos = new Map()
  const lanes = []
  let laneTop = 0
  let maxCol = 0

  const floating = []
  const mixedRound = new Set()
  for (const side of sideKeys) {
    const laneMatches = sideGroups.get(side)
    const columns = orderColumnsByLinks(laneColumns(laneMatches), winnerIdsInto)
    for (const c of columns) c.labels = c.labels.map(l => shortRound(l, prefixes.get(side)))

    const { colIndex, fedFromOtherSide } = placeLaneColumns(columns, winnerIdsInto, colOf, mixedRound)
    const offset = colIndex[0]
    maxCol = Math.max(maxCol, colIndex.at(-1))

    // Finale alimentee par d'autres cotes (grande finale, ou finale d'une phase
    // intermediaire importee en "BRACKET") : placee entre ses demi-finales.
    const isFinal = (side === 'GRAND_FINAL' || side === 'BRACKET') && columns.length === 1 && fedFromOtherSide
    if (isFinal) {
      floating.push({ side, columns, offset })
      continue
    }

    const centerY = layoutLane(columns, winnerIdsInto, loserIdsInto)
    const bodyTop = laneTop + LANE_HEAD
    let bottom = bodyTop
    for (const [id, cy] of centerY) {
      const y = bodyTop + cy - CARD_H / 2
      pos.set(id, { x: colX(colOf.get(id)), y })
      bottom = Math.max(bottom, y + CARD_H)
    }
    lanes.push({
      key: side,
      label: sideLabel(side),
      top: laneTop,
      height: bottom - laneTop,
      width: colX(colIndex.at(-1) + 1) - colGap,
      columns: columns.map((c, ci) => ({ x: colX(colIndex[ci]), labels: c.labels, date: c.start.slice(0, 10) }))
    })
    laneTop = bottom + LANE_GAP
  }

  for (const f of floating) {
    const matchesOf = f.columns[0].matches
    placeFloatingFinal(matchesOf, colX(f.offset), winnerIdsInto, pos)
    lanes.push({
      key: f.side,
      label: sideLabel(f.side),
      floating: true,
      top: Math.min(...matchesOf.map(m => pos.get(m.id).y)) - LANE_HEAD,
      height: 0,
      columns: [{ x: colX(f.offset), labels: f.columns[0].labels, date: f.columns[0].start.slice(0, 10) }]
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
      d: edgePath(pos.get(l.from), pos.get(l.to), cardW),
      done: slot != null,
      teamId: teamIdAt(from, slot)
    }
  })

  const width = colX(maxCol + 1) - colGap
  const height = Math.max(...nodes.map(n => n.y + CARD_H), 0)
  if (lanes.length === 1) lanes[0].hideTitle = true
  return { kind: 'tree', key, label, nodes, edges, lanes, width, height, cols: maxCol + 1, cardW, champion: championOf(matches) }
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
 * sur les visuels officiels des Worlds. Un bilan est inconnu ("?") tant qu'un
 * match precedent de l'equipe n'a pas de resultat.
 */
const NO_RECORD = { w: 0, l: 0, unknown: false }

/** Bilan d'une equipe avant son match ("2-1"), "?" si inconnu ou equipe pas encore connue. */
function recordBefore(records, teamId) {
  if (teamId == null) return '?'
  const r = records.get(teamId) ?? NO_RECORD
  return r.unknown ? '?' : `${r.w}-${r.l}`
}

/** Met a jour le bilan des deux equipes ; un match sans resultat rend leur bilan inconnu. */
function recordResult(records, m) {
  const slot = winnerSlot(m)
  for (const [teamId, teamSlot] of [[m.team1Id, 1], [m.team2Id, 2]]) {
    if (teamId == null) continue
    const before = records.get(teamId) ?? NO_RECORD
    let after = { ...before, l: before.l + 1 }
    if (!slot) after = { ...before, unknown: true }
    else if (slot === teamSlot) after = { ...before, w: before.w + 1 }
    records.set(teamId, after)
  }
}

function buildSwissBoard(key, matches) {
  const sorted = [...matches].sort(byKickoff)
  const roundOf = swissRounds(sorted)
  const records = new Map()
  const rounds = new Map()
  for (const m of sorted) {
    const round = roundOf.get(m.id)
    if (!rounds.has(round)) rounds.set(round, new Map())
    const buckets = rounds.get(round)
    const bucket = recordBefore(records, m.team1Id)
    if (!buckets.has(bucket)) buckets.set(bucket, [])
    buckets.get(bucket).push(m)
    recordResult(records, m)
  }
  const columns = [...rounds.entries()].map(([label, buckets]) => ({
    label,
    buckets: [...buckets.entries()]
      .map(([rec, ms]) => ({ record: rec, matches: ms }))
      .sort((a, b) => bucketRank(a.record) - bucketRank(b.record))
  }))
  return { kind: 'swiss', key, label: sideLabel(key), columns }
}

/**
 * Round de chaque match. Les libelles saisis font foi, sauf s'ils sont incoherents
 * (une equipe jouant deux fois dans le meme "round", ex: un round importe qui couvre
 * deux journees) : en suisse, le n-ieme match d'une equipe est alors celui du round n.
 */
function swissRounds(sorted) {
  const byLabel = groupBy(sorted, m => roundColumn(m.roundLabel))
  const coherent = [...byLabel.values()].every(ms => {
    const teams = ms.flatMap(m => [m.team1Id, m.team2Id]).filter(id => id != null)
    return new Set(teams).size === teams.length
  })
  const roundOf = new Map()
  if (coherent) {
    for (const m of sorted) roundOf.set(m.id, roundColumn(m.roundLabel))
    return roundOf
  }
  const played = new Map()
  for (const m of sorted) {
    if (m.team1Id == null || m.team2Id == null) {
      roundOf.set(m.id, roundColumn(m.roundLabel))
      continue
    }
    const n = Math.max(played.get(m.team1Id) ?? 0, played.get(m.team2Id) ?? 0) + 1
    played.set(m.team1Id, n)
    played.set(m.team2Id, n)
    roundOf.set(m.id, `Round ${n}`)
  }
  return roundOf
}

function bucketRank(r) {
  if (r === '?') return Infinity
  const [w, l] = r.split('-').map(Number)
  return l - w
}

/**
 * Tous les tableaux a afficher, dans l'ordre de la competition. `avail` est la
 * largeur disponible en px : les arbres sont resserres pour y tenir.
 */
export function buildBoards(matches, avail = 0) {
  const boardOf = m => {
    const side = m.bracketSide || 'AUTRE'
    return BOARD_OF_SIDE[side] ?? side
  }
  // Deux phases d'une meme competition (ex: Play-In puis Playoffs, chacun avec ses
  // "Round 1", "Round 2"...) forment des tableaux distincts.
  const stageIds = new Set(matches.map(m => m.stageId).filter(id => id != null))
  const multiStage = stageIds.size > 1
  const boardGroups = groupBy(matches, m => `${multiStage ? m.stageId ?? '' : ''}#${boardOf(m)}`)
  const firstSide = key => [...new Set(boardGroups.get(key).map(m => m.bracketSide || 'AUTRE'))].sort(compareSides)[0]
  const firstKickoff = key => boardGroups.get(key).map(kickoff).sort(byText)[0]
  const stageOf = key => key.slice(0, key.indexOf('#'))
  const stageStart = key => [...boardGroups.keys()].filter(k => stageOf(k) === stageOf(key)).map(firstKickoff).sort(byText)[0]
  const keys = [...boardGroups.keys()].sort((a, b) =>
    stageStart(a).localeCompare(stageStart(b)) || compareSides(firstSide(a), firstSide(b)))

  const boards = keys.map(groupKey => {
    const ms = boardGroups.get(groupKey)
    const key = groupKey.slice(groupKey.indexOf('#') + 1)
    if (key === 'SWISS_STAGE') return { ...buildSwissBoard(key, ms), key: groupKey }
    const stageName = multiStage ? ms[0].stageName : null
    const base = BOARD_LABELS[key] ?? sideLabel(key)
    let label = base
    if (stageName) label = key === 'MAIN' ? stageName : `${base} (${stageName})`
    // Le nombre de colonnes n'est connu qu'apres une premiere mise en page
    const natural = buildTreeBoard(key, label, ms, { cardW: CARD_W, colGap: COL_GAP })
    const board = natural.width <= avail || !avail ? natural : buildTreeBoard(key, label, ms, fitGeometry(natural.cols, avail))
    return { ...board, key: groupKey }
  })

  // Deux phases importees sous le meme nom (ex: "Playoffs" du Play-In et des
  // Playoffs) : les dates du tableau les distinguent.
  const sameLabel = groupBy(boards, b => b.label)
  for (const b of boards) {
    if (sameLabel.get(b.label).length < 2) continue
    const days = boardGroups.get(b.key).map(m => m.date).sort(byText)
    const day = iso => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
    b.label = days[0] === days.at(-1) ? `${b.label} (${day(days[0])})` : `${b.label} (${day(days[0])} → ${day(days.at(-1))})`
  }
  return boards
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
