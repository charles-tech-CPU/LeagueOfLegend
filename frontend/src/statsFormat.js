// Mise en forme des statistiques (fiche joueur, onglets Stats d'une competition ou d'une saison).

/** Pourcentage de victoires (entier), null si aucune manche au vainqueur connu. */
export function winRate(s) {
  const played = (s.wins ?? 0) + (s.losses ?? 0)
  return played ? Math.round((100 * s.wins) / played) : null
}

/** "72 %" a partir d'une part entre 0 et 1, "—" si inconnue. */
export function percent(share) {
  return share === null || share === undefined ? '—' : `${Math.round(share * 100)} %`
}

/** Ratio K/D/A a deux decimales. */
export function kdaRatio(ratio) {
  return ratio === null || ratio === undefined ? '—' : ratio.toFixed(2)
}

/** K/D/A moyen par manche, "4.2 / 1.8 / 6.0" ; games : manches ou le K/D/A est saisi. */
export function averageKda(s, games = s.kdaGames ?? s.games) {
  if (!games) return '—'
  const avg = v => (v / games).toFixed(1)
  return `${avg(s.kills)} / ${avg(s.deaths)} / ${avg(s.assists)}`
}

/** Couleur du ratio K/D/A : excellent, bon, moyen, faible. */
export function ratioClass(ratio) {
  if (ratio === null || ratio === undefined) return ''
  if (ratio >= 5) return 'ratio-great'
  if (ratio >= 3) return 'ratio-good'
  if (ratio >= 2) return 'ratio-ok'
  return 'ratio-low'
}
