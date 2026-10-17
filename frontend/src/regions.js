// Regions des equipes et des competitions. En base, une meme region est ecrite de plusieurs
// facons selon la source (import Excel, Leaguepedia, saisie) : "LEC", "Europe", "EMEA"...
// On les ramene ici a une seule region, avec un libelle et une couleur (celle de sa ligue,
// voir leagueColors.js), pour les afficher et les filtrer de la meme facon partout.
import { leagueColor } from './leagueColors'

const REGIONS = [
  { key: 'KR', label: 'Corée', league: 'LCK', aliases: ['LCK', 'Korea', 'KR'] },
  { key: 'CN', label: 'Chine', league: 'LPL', aliases: ['LPL', 'China', 'CN'] },
  { key: 'EU', label: 'Europe', league: 'LEC', aliases: ['LEC', 'Europe', 'EMEA', 'EU'] },
  { key: 'NA', label: 'Amérique du Nord', league: 'LCS', aliases: ['LCS', 'North America', 'NA'] },
  { key: 'BR', label: 'Brésil', league: 'CBLOL', aliases: ['CBLOL', 'Brazil', 'BR'] },
  { key: 'AP', label: 'Asie-Pacifique', league: 'LCP', aliases: ['LCP', 'Asia Pacific', 'APAC', 'LMS', 'PCS'] },
  { key: 'SEA', label: 'Asie du Sud-Est', league: 'GPL', aliases: ['SEA', 'GPL'] },
  { key: 'VN', label: 'Vietnam', league: 'VCS', aliases: ['Vietnam', 'VCS', 'VN'] },
  { key: 'JP', label: 'Japon', league: 'LJL', aliases: ['Japan', 'LJL', 'JP'] },
  { key: 'TR', label: 'Turquie', league: 'TCL', aliases: ['Turkey', 'TCL', 'TR'] },
  { key: 'CIS', label: 'CEI', league: 'LCL', aliases: ['CIS', 'LCL'] },
  { key: 'OCE', label: 'Océanie', league: 'OPL', aliases: ['Oceania', 'OPL', 'LCO', 'OCE'] },
  { key: 'LAT', label: 'Amérique latine', league: 'LLA', aliases: ['Latin America', 'LAN', 'LAS', 'LAT', 'LLA'] },
  { key: 'AM', label: 'Amériques', league: 'LTA', aliases: ['Americas', 'LTA'] },
  { key: 'WC', label: 'Wildcard', league: 'IWCI', aliases: ['Wildcard'] }
]

const BY_ALIAS = new Map(
  REGIONS.flatMap(r => r.aliases.map(alias => [alias.toLowerCase(), r]))
)

/**
 * Region d'une valeur saisie : { key, label, color }, ou null si vide. Une valeur inconnue
 * est gardee telle quelle, en gris.
 */
export function regionOf(raw) {
  const value = (raw ?? '').trim()
  if (!value) return null
  const region = BY_ALIAS.get(value.toLowerCase())
  if (!region) return { key: value, label: value, color: 'var(--text-dim)' }
  return { key: region.key, label: region.label, color: leagueColor(region.league) }
}

/** Libelle de la region ("Europe" pour "LEC", "EMEA"...), ou la valeur de repli. */
export function regionLabel(raw, fallback = '') {
  return regionOf(raw)?.label ?? fallback
}
