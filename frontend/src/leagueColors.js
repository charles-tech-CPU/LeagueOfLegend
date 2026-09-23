// Couleur d'identification par ligue, pour s'y repérer d'un coup d'œil dans le calendrier.
export const LEAGUE_COLORS = {
  LPL: '#3fb96e',   // Chine — vert
  LCK: '#f5c518',   // Corée — jaune
  LEC: '#4a9eff',   // Europe — bleu
  LCS: '#e8536a',   // Amérique du Nord — rouge
  LCP: '#f2994a',   // Taiwan (ex-PCL) — orange
  CBLOL: '#ec4899', // Brésil — rose
  WSCI: '#a78bfa',  // World Star Challengers Invitational — violet
  WORLDS: '#f5c451', // Worlds — or
  DEMACIA: '#94a3b8', // Demacia Cup — argent
  EM: '#22b8cf',    // EMEA Masters — cyan
  'EM LCQ': '#22b8cf'
}

export function leagueColor(code) {
  return LEAGUE_COLORS[code] ?? 'var(--gold)'
}
