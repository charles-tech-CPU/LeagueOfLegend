import { createRouter, createWebHistory } from 'vue-router'
import CompetitionsView from '../views/CompetitionsView.vue'
import CompetitionDetailView from '../views/CompetitionDetailView.vue'
import TeamsView from '../views/TeamsView.vue'
import UpcomingMatchesView from '../views/UpcomingMatchesView.vue'

const routes = [
  { path: '/', name: 'competitions', component: CompetitionsView },
  { path: '/competitions/:id', name: 'competition-detail', component: CompetitionDetailView, props: true },
  { path: '/teams', name: 'teams', component: TeamsView },
  { path: '/upcoming', name: 'upcoming', component: UpcomingMatchesView }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
