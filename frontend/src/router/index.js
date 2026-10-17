import { createRouter, createWebHistory } from 'vue-router'
import CompetitionsView from '../views/CompetitionsView.vue'
import CompetitionDetailView from '../views/CompetitionDetailView.vue'
import TeamsView from '../views/TeamsView.vue'
import TeamDetailView from '../views/TeamDetailView.vue'
import PlayersView from '../views/PlayersView.vue'
import PlayerDetailView from '../views/PlayerDetailView.vue'
import UpcomingMatchesView from '../views/UpcomingMatchesView.vue'
import MatchDetailView from '../views/MatchDetailView.vue'
import StatsView from '../views/StatsView.vue'
import PowerRankingView from '../views/PowerRankingView.vue'

const routes = [
  { path: '/', name: 'competitions', component: CompetitionsView },
  { path: '/competitions/:id', name: 'competition-detail', component: CompetitionDetailView, props: true },
  { path: '/teams', name: 'teams', component: TeamsView },
  { path: '/teams/:id', name: 'team-detail', component: TeamDetailView, props: true },
  { path: '/players', name: 'players', component: PlayersView },
  { path: '/players/:id', name: 'player-detail', component: PlayerDetailView, props: true },
  { path: '/upcoming', name: 'upcoming', component: UpcomingMatchesView },
  { path: '/matches/:id', name: 'match-detail', component: MatchDetailView, props: true },
  { path: '/stats', name: 'stats', component: StatsView },
  { path: '/power-ranking', name: 'power-ranking', component: PowerRankingView }
]

// Delai laisse a une page pour charger ses donnees avant de restaurer la position
const RESTORE_SCROLL_DELAY_MS = 350

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // Retour / avance du navigateur : on retrouve la position qu'on avait quittee
    if (savedPosition) return new Promise(resolve => setTimeout(() => resolve(savedPosition), RESTORE_SCROLL_DELAY_MS))
    // Simple changement d'onglet (?tab=) sur la meme page : on ne bouge pas
    if (to.path === from.path) return false
    return { top: 0 }
  }
})
