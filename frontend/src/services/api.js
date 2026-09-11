import axios from 'axios'

// URL du backend Spring Boot en dev. A adapter le jour d'un deploiement
// (variable d'environnement Vite, ex: import.meta.env.VITE_API_URL).
const API_BASE_URL = 'http://localhost:8080/api'
const api = axios.create({
  baseURL: API_BASE_URL
})

export function teamLogoUrl(teamId) {
  return `${API_BASE_URL}/teams/${teamId}/logo`
}

export default {
  // Competitions
  getCompetitions: () => api.get('/competitions').then(r => r.data),
  createCompetition: (payload) => api.post('/competitions', payload).then(r => r.data),

  // Equipes
  getTeams: () => api.get('/teams').then(r => r.data),
  createTeam: (payload) => api.post('/teams', payload).then(r => r.data),
  updateTeam: (id, payload) => api.put(`/teams/${id}`, payload).then(r => r.data),
  deleteTeam: (id) => api.delete(`/teams/${id}`),

  // Matchs
  getMatchesByCompetition: (competitionId) =>
    api.get('/matches', { params: { competitionId } }).then(r => r.data),
  getScheduledMatches: () =>
    api.get('/matches', { params: { status: 'SCHEDULED' } }).then(r => r.data),
  createMatch: (payload) => api.post('/matches', payload).then(r => r.data),
  updateMatch: (id, payload) => api.put(`/matches/${id}`, payload).then(r => r.data),
  deleteMatch: (id) => api.delete(`/matches/${id}`),

  // Classement / tete-a-tete (calcules cote backend, jamais stockes)
  getStandings: (competitionId) =>
    api.get('/standings', { params: { competitionId } }).then(r => r.data),
  getHeadToHead: (competitionId) =>
    api.get('/head-to-head', { params: { competitionId } }).then(r => r.data)
}
