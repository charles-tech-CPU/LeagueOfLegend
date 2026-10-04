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
  'EM LCQ': '#22b8cf',
  // Historique (import Leaguepedia) : les ancetres reprennent la couleur de leur ligue actuelle.
  Champions: '#f5c518', // Coree, avant la LCK
  OGN: '#f5c518',
  'NA LCS': '#e8536a',
  'EU LCS': '#4a9eff',
  LMS: '#f2994a',   // Taiwan, avant la PCS / LCP
  PCS: '#f2994a',
  GPL: '#2dd4bf',   // Asie du Sud-Est
  VCS: '#ef4444',   // Vietnam
  TCL: '#f43f5e',   // Turquie
  LJL: '#fb7185',   // Japon
  OPL: '#38bdf8',   // Oceanie
  LCO: '#38bdf8',
  LCL: '#a3a3a3',   // CEI
  LLA: '#84cc16',   // Amerique latine
  CLS: '#84cc16',
  LLN: '#84cc16',
  CLA: '#84cc16',
  MSI: '#e2e8f0',
  'ALL-STAR': '#f472b6',
  'RIFT RIVALS': '#fb923c',
  MSC: '#e2e8f0',
  IEM: '#60a5fa',   // Tournois hors Riot des debuts
  IPL: '#c084fc',
  MLG: '#4ade80',
  DreamHack: '#facc15',
  BotA: '#94a3b8',
  CLN: '#84cc16',
  LTA: '#e8536a',   // Ameriques (2025)
  'LTA N': '#e8536a',
  'LTA S': '#ec4899',
  LST: '#2dd4bf',
  SLTV: '#a3a3a3',
  FST: '#e2e8f0',   // First Stand
  RF: '#f5c451',    // Regional Finals (qualifications pour les Worlds)
  IWCT: '#94a3b8',
  IWCI: '#94a3b8',
  IWCQ: '#94a3b8'
}

export function leagueColor(code) {
  return LEAGUE_COLORS[code] ?? 'var(--gold)'
}
