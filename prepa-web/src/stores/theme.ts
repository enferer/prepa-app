import { defineStore } from 'pinia'
import { ref, watchEffect } from 'vue'

const CLE = 'prepa.theme'

/** Theme clair ou sombre, retenu d'une visite a l'autre et aligne sur le systeme par defaut. */
export const useTheme = defineStore('theme', () => {
  const prefereSombre = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  const sombre = ref((localStorage.getItem(CLE) ?? (prefereSombre ? 'sombre' : 'clair')) === 'sombre')

  watchEffect(() => {
    document.documentElement.classList.toggle('sombre', sombre.value)
    localStorage.setItem(CLE, sombre.value ? 'sombre' : 'clair')
  })

  return { sombre, basculer: () => (sombre.value = !sombre.value) }
})
