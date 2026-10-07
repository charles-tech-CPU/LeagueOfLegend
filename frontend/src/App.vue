<template>
  <nav class="topbar">
    <a :href="PORTAL_URL" class="portal-link" title="Retour au portail du foyer">← Portail</a>
    <router-link to="/" class="brand">
      <span class="brand-mark">LR</span>
      <span class="brand-text">LoL <span class="brand-accent">Results</span></span>
    </router-link>
    <router-link to="/" class="nav-link" :class="{ 'router-link-active': $route.path.startsWith('/competitions') }">🏆 Compétitions</router-link>
    <router-link to="/upcoming" class="nav-link">📅 À venir</router-link>
    <router-link to="/teams" class="nav-link" :class="{ 'router-link-active': $route.path.startsWith('/teams') }">🛡️ Équipes</router-link>
    <router-link to="/players" class="nav-link" :class="{ 'router-link-active': $route.path.startsWith('/players') }">🎮 Joueurs</router-link>
    <router-link to="/stats" class="nav-link" :class="{ 'router-link-active': $route.path.startsWith('/stats') }">📈 Stats</router-link>
  </nav>
  <router-view />
</template>

<script setup>
import { PORTAL_URL } from './portal'
</script>
