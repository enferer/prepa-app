import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

/**
 * Largeur disponible d'un élément, suivie en continu.
 *
 * <p>Un graphique dessiné à taille fixe laisse un blanc sur grand écran et déborde sur
 * petit. Plutôt que de deviner, on mesure le conteneur et on redessine quand il change —
 * au redimensionnement de la fenêtre comme à l'ouverture d'un panneau.
 */
export function useLargeur(conteneur: Ref<HTMLElement | null>, defaut = 600): Ref<number> {
  const largeur = ref(defaut)
  let observateur: ResizeObserver | null = null

  function observer(element: HTMLElement | null) {
    observateur?.disconnect()
    if (!element) return
    observateur = new ResizeObserver((entrees) => {
      const mesure = entrees[0]?.contentRect.width
      if (mesure && mesure > 0) largeur.value = Math.floor(mesure)
    })
    observateur.observe(element)
    largeur.value = Math.floor(element.clientWidth) || defaut
  }

  /** Relecture directe, en complément de l'observateur. */
  function mesurer() {
    const element = conteneur.value
    if (element) {
      const mesure = element.clientWidth
      if (mesure > 0) largeur.value = Math.floor(mesure)
    }
  }

  onMounted(() => {
    observer(conteneur.value)
    // Le redimensionnement de la fenêtre en renfort : la rotation d'un téléphone change la
    // largeur sans toujours produire de notification exploitable sur l'élément lui-même.
    window.addEventListener('resize', mesurer)
  })

  watch(conteneur, observer)

  onBeforeUnmount(() => {
    observateur?.disconnect()
    window.removeEventListener('resize', mesurer)
  })

  return largeur
}
