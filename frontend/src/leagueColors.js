// Couleur d'identification par ligue, pour s'y repérer d'un coup d'œil dans le calendrier.
export const LEAGUE_COLORS = {
  LPL: '#3fb96e',   // Chine — vert
  LCK: '#f5c518',   // Corée — jaune
  LEC: '#4a9eff',   // Europe — bleu
  LCS: '#e8536a',   // Amérique du Nord — rouge
  LCP: '#f2994a',   // Taiwan (ex-PCL) — orange
  CBLOL: '#ec4899'  // Brésil — rose
}

export function leagueColor(code) {
  return LEAGUE_COLORS[code] ?? 'var(--gold)'
}
