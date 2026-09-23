// Mise en forme des dates pour l'affichage (en francais, heure locale).

// Date locale (et non UTC) au format ISO "AAAA-MM-JJ", comme m.date renvoye par l'API
export function localIsoDate(d) {
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

function parseIso(iso) {
  return new Date(`${iso}T00:00:00`)
}

/** "sam. 20 sept." */
export function shortDay(iso) {
  if (!iso) return ''
  return parseIso(iso).toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' })
}

/** "Aujourd'hui", "Demain", "Hier" ou "samedi 26 septembre". */
export function longDay(iso, todayIso = localIsoDate(new Date())) {
  const diff = Math.round((parseIso(iso) - parseIso(todayIso)) / 86_400_000)
  if (diff === 0) return "Aujourd'hui"
  if (diff === 1) return 'Demain'
  if (diff === -1) return 'Hier'
  const label = parseIso(iso).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
  return label.charAt(0).toUpperCase() + label.slice(1)
}
