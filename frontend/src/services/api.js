import axios from 'axios'

// URL du backend Spring Boot en dev. A adapter le jour d'un deploiement
// (variable d'environnement Vite, ex: import.meta.env.VITE_API_URL).
const API_BASE_URL = `http://${window.location.hostname}:8080/api`
const api = axios.create({
  baseURL: `http://${window.location.hostname}:8080/api`
})

export function teamLogoUrl(teamId) {
  return `${API_BASE_URL}/teams/${teamId}/logo`
}

export default {
  // Competitions
  getCompetitions: () => api.get('/competitions').then(r => r.data),
  // Competitions d'une saison, avec leur avancement (matchCount / playedCount)
  getCompetitionsBySeason: (season) => api.get('/competitions', { params: { season } }).then(r => r.data),
  getSeasons: () => api.get('/competitions/seasons').then(r => r.data),
  getCompetition: (id) => api.get(`/competitions/${id}`).then(r => r.data),
  createCompetition: (payload) => api.post('/competitions', payload).then(r => r.data),
  updateCompetition: (id, payload) => api.put(`/competitions/${id}`, payload).then(r => r.data),

  // Phases d'une competition (format) : la creation genere les matchs
  getStages: (competitionId) => api.get(`/competitions/${competitionId}/stages`).then(r => r.data),
  createStage: (competitionId, payload) => api.post(`/competitions/${competitionId}/stages`, payload).then(r => r.data),
  nextSwissRound: (stageId, date) => api.post(`/stages/${stageId}/next-round`, null, { params: { date } }).then(r => r.data),
  deleteStage: (stageId) => api.delete(`/stages/${stageId}`),

  // Equipes
  getTeams: () => api.get('/teams').then(r => r.data),
  getTeam: (id) => api.get(`/teams/${id}`).then(r => r.data),
  // Noms successifs de l'equipe (ex: SK Telecom T1 -> T1), du plus ancien au nom actuel
  getTeamNames: (id) => api.get(`/teams/${id}/names`).then(r => r.data),
  createTeam: (payload) => api.post('/teams', payload).then(r => r.data),
  updateTeam: (id, payload) => api.put(`/teams/${id}`, payload).then(r => r.data),
  deleteTeam: (id) => api.delete(`/teams/${id}`),

  // Joueurs (effectif actuel d'une equipe ou d'une saison passee, ou sans equipe)
  getAllPlayers: () => api.get('/players').then(r => r.data),
  getPlayer: (id) => api.get(`/players/${id}`).then(r => r.data),
  // season nul = effectif actuel
  getPlayers: (teamId, season = null) =>
    api.get(`/teams/${teamId}/players`, { params: season ? { season } : {} }).then(r => r.data),
  getRosterSeasons: (teamId) => api.get(`/teams/${teamId}/roster-seasons`).then(r => r.data),
  createPlayer: (teamId, payload) => api.post(`/teams/${teamId}/players`, payload).then(r => r.data),
  // Joueur sans equipe actuelle (retraite...)
  createFreePlayer: (payload) => api.post('/players', payload).then(r => r.data),
  // payload : { pseudo, nationality, position, startDate (facultatif), endDate }
  createFormerPlayer: (teamId, payload) => api.post(`/teams/${teamId}/former-players`, payload).then(r => r.data),
  updatePlayer: (id, payload) => api.put(`/players/${id}`, payload).then(r => r.data),
  deletePlayer: (id) => api.delete(`/players/${id}`),
  // payload : { teamId (nul = quitte son equipe), date }
  transferPlayer: (id, payload) => api.post(`/players/${id}/transfer`, payload).then(r => r.data),

  // Historique des equipes d'un joueur (passages), avec les resultats de chaque passage
  getPlayerStints: (playerId) => api.get(`/players/${playerId}/stints`).then(r => r.data),
  addStint: (playerId, payload) => api.post(`/players/${playerId}/stints`, payload).then(r => r.data),
  updateStint: (id, payload) => api.put(`/stints/${id}`, payload).then(r => r.data),
  deleteStint: (id) => api.delete(`/stints/${id}`),

  // Matchs
  getMatchesByCompetition: (competitionId) =>
    api.get('/matches', { params: { competitionId } }).then(r => r.data),
  // from : ignore les matchs plus anciens (tournois historiques sans score)
  getScheduledMatches: (from) =>
    api.get('/matches', { params: { status: 'SCHEDULED', from } }).then(r => r.data),
  createMatch: (payload) => api.post('/matches', payload).then(r => r.data),
  updateMatch: (id, payload) => api.put(`/matches/${id}`, payload).then(r => r.data),
  deleteMatch: (id) => api.delete(`/matches/${id}`),

  // Classement / tete-a-tete (calcules cote backend, jamais stockes)
  getStandings: (competitionId, groupId) =>
    api.get('/standings', { params: { competitionId, groupId } }).then(r => r.data),
  getHeadToHead: (competitionId, groupId) =>
    api.get('/head-to-head', { params: { competitionId, groupId } }).then(r => r.data)
}
