import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'

/**
 * Routage de l'application.
 *
 * <p>L'historique est en fragment d'URL : l'application peut ainsi etre servie depuis
 * n'importe quel chemin sans configuration de reecriture cote serveur.
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'tableau-de-bord',
      component: () => import('@/views/TableauDeBord.vue'),
      meta: { titre: 'Tableau de bord', connecte: true },
    },
    {
      path: '/saison',
      name: 'saison',
      component: () => import('@/views/SaisonView.vue'),
      meta: { titre: 'Saison', connecte: true },
    },
    {
      path: '/seances',
      name: 'seances',
      component: () => import('@/views/SeancesView.vue'),
      meta: { titre: 'Séances', connecte: true },
    },
    {
      path: '/seances/:id',
      name: 'seance',
      component: () => import('@/views/SeancesView.vue'),
      meta: { titre: 'Séances', connecte: true },
    },
    {
      path: '/stats',
      name: 'stats',
      component: () => import('@/views/StatsView.vue'),
      meta: { titre: 'Statistiques', connecte: true },
    },
    {
      path: '/journal',
      name: 'journal',
      component: () => import('@/views/JournalView.vue'),
      meta: { titre: 'Journal', connecte: true },
    },
    {
      path: '/profil',
      name: 'profil',
      component: () => import('@/views/ProfilView.vue'),
      meta: { titre: 'Profil', connecte: true },
    },
    {
      path: '/connexion',
      name: 'connexion',
      component: () => import('@/views/ConnexionView.vue'),
      meta: { titre: 'Connexion' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (vers) => {
  const auth = useAuth()
  if (!vers.meta.connecte) return true
  if (auth.athlete) return true
  return (await auth.restaurer()) ? true : { name: 'connexion' }
})

router.afterEach((vers) => {
  document.title = vers.meta.titre ? `${vers.meta.titre} — Suivi d'entraînement` : "Suivi d'entraînement"
})

export default router
