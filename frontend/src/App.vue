<template>
  <nav class="topbar">
    <router-link to="/" class="brand">
      <span class="brand-mark">LR</span>
      <span class="brand-text">LoL <span class="brand-accent">Results</span></span>
    </router-link>
    <router-link to="/" class="nav-link" :class="{ 'router-link-active': $route.path.startsWith('/competitions') }">🏆 Compétitions</router-link>
    <router-link to="/upcoming" class="nav-link">📅 À venir</router-link>
    <router-link to="/teams" class="nav-link">🛡️ Équipes</router-link>
  </nav>
  <router-view />
</template>
