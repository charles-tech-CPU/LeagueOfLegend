import { createRouter, createWebHistory } from 'vue-router'
import CompetitionsView from '../views/CompetitionsView.vue'
import CompetitionDetailView from '../views/CompetitionDetailView.vue'
import TeamsView from '../views/TeamsView.vue'
import TeamDetailView from '../views/TeamDetailView.vue'
import PlayersView from '../views/PlayersView.vue'
import PlayerDetailView from '../views/PlayerDetailView.vue'
import UpcomingMatchesView from '../views/UpcomingMatchesView.vue'
import MatchDetailView from '../views/MatchDetailView.vue'

const routes = [
  { path: '/', name: 'competitions', component: CompetitionsView },
  { path: '/competitions/:id', name: 'competition-detail', component: CompetitionDetailView, props: true },
  { path: '/teams', name: 'teams', component: TeamsView },
  { path: '/teams/:id', name: 'team-detail', component: TeamDetailView, props: true },
  { path: '/players', name: 'players', component: PlayersView },
  { path: '/players/:id', name: 'player-detail', component: PlayerDetailView, props: true },
  { path: '/upcoming', name: 'upcoming', component: UpcomingMatchesView },
  { path: '/matches/:id', name: 'match-detail', component: MatchDetailView, props: true }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
