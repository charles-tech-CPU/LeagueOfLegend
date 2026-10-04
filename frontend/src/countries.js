import flagIconsCountries from 'flag-icons/country.json'

// Nationalite d'un joueur = code pays ISO 3166-1 alpha-2 (ex: "FR", "KR"), stocke tel quel
// en base. Les drapeaux viennent de flag-icons (SVG embarques : les emojis drapeaux ne
// s'affichent pas sous Windows) et les noms sont traduits en francais par le navigateur.
const frenchNames = new Intl.DisplayNames(['fr'], { type: 'region' })

function frenchName(code, fallback) {
  try {
    return frenchNames.of(code) ?? fallback
  } catch {
    return fallback
  }
}

// Codes ISO a deux lettres uniquement (+ Kosovo, XK), sans les regions comme gb-eng ou eu.
export const COUNTRIES = flagIconsCountries
  .filter(c => c.iso || c.code === 'xk')
  .map(c => ({ code: c.code.toUpperCase(), name: frenchName(c.code.toUpperCase(), c.name) }))
  .sort((a, b) => a.name.localeCompare(b.name, 'fr'))

const BY_CODE = Object.fromEntries(COUNTRIES.map(c => [c.code, c]))

// Pays les plus representes en LoL esport, proposes en tete de liste.
const COMMON_CODES = ['KR', 'CN', 'TW', 'VN', 'JP', 'FR', 'DE', 'ES', 'DK', 'SE', 'PL', 'GB', 'BE', 'NL', 'CZ', 'TR', 'US', 'CA', 'BR']
export const COMMON_COUNTRIES = COMMON_CODES.map(code => BY_CODE[code]).filter(Boolean)

export function countryName(code) {
  return BY_CODE[code?.toUpperCase()]?.name ?? code
}

export function isKnownCountry(code) {
  return Boolean(code && BY_CODE[code.toUpperCase()])
}
