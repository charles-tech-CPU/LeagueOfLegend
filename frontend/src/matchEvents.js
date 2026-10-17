// Signale qu'un resultat de match a change (score, equipes, details), pour que les vues
// calculees a partir des matchs (power ranking...) se mettent a jour sans recharger la page :
// dans cet onglet, et dans les autres onglets du meme navigateur via BroadcastChannel.

const CHANNEL_NAME = 'lol-results-matches'
const listeners = new Set()
let channel = null
try {
  channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(CHANNEL_NAME)
  channel?.addEventListener('message', () => listeners.forEach(fn => fn()))
} catch {
  channel = null
}

export function notifyMatchChanged() {
  listeners.forEach(fn => fn())
  channel?.postMessage('changed')
}

/** Abonne `fn` aux changements de resultats ; renvoie la fonction de desabonnement. */
export function onMatchChanged(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}
