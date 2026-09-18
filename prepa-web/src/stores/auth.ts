import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api'
import { jetons } from '@/api/http'
import type { Athlete } from '@/api/types'

export const useAuth = defineStore('auth', () => {
  const athlete = ref<Athlete | null>(null)
  const chargement = ref(false)

  async function connecter(identifiant: string, motDePasse: string) {
    const reponse = await authApi.connexion(identifiant, motDePasse)
    jetons.enregistrer(reponse.accessToken, reponse.refreshToken)
    athlete.value = await authApi.moi()
  }

  /**
   * Retablit la session au demarrage a partir du jeton conserve.
   * Un echec n'est pas une erreur : il signifie simplement qu'il faut se reconnecter.
   */
  async function restaurer(): Promise<boolean> {
    if (!jetons.acces()) return false
    chargement.value = true
    try {
      athlete.value = await authApi.moi()
      return true
    } catch {
      jetons.effacer()
      return false
    } finally {
      chargement.value = false
    }
  }

  async function deconnecter() {
    const refresh = jetons.rafraichissement()
    if (refresh) {
      await authApi.deconnexion(refresh).catch(() => undefined)
    }
    jetons.effacer()
    athlete.value = null
  }

  return { athlete, chargement, connecter, restaurer, deconnecter }
})
