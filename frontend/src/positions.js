// Postes d'un effectif LoL, dans l'ordre d'affichage classique (du haut de la carte vers le bas),
// avec une couleur d'identification par poste.
export const POSITIONS = [
  { key: 'TOP', label: 'Top lane', color: '#f2994a' },
  { key: 'JGL', label: 'Jungle', color: '#3fb96e' },
  { key: 'MID', label: 'Mid lane', color: '#22d3ee' },
  { key: 'ADC', label: 'Bot lane', color: '#f5c451' },
  { key: 'SUPP', label: 'Support', color: '#a78bfa' }
]

export function positionColor(key) {
  return POSITIONS.find(p => p.key === key)?.color ?? 'var(--accent)'
}

export function positionIndex(key) {
  return POSITIONS.findIndex(p => p.key === key)
}
