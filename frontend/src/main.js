import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import 'flag-icons/css/flag-icons.min.css'
import './style.css'

// Largeur utile de la page (hors barre de defilement verticale), pour les blocs
// pleine largeur (.full-bleed) : 100vw l'inclut et ferait defiler la page.
const root = document.documentElement
new ResizeObserver(() => root.style.setProperty('--page-w', `${root.clientWidth}px`)).observe(root)

createApp(App).use(router).mount('#app')
