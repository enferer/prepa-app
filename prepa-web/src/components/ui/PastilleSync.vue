<script setup lang="ts">
import { computed } from 'vue'
import type { StatutSync } from '@/api/types'

/**
 * Issue d'une synchronisation Garmin.
 *
 * <p>Les libellés disent ce qu'il y a à faire plutôt que ce qui a échoué : « Compte à
 * reconnecter » se comprend sans connaître la mécanique OAuth, « Erreur d'authentification »
 * non. Trois statuts appellent un geste humain et prennent donc une couleur d'alerte ; une
 * erreur ordinaire se réessaie toute seule au passage suivant et reste discrète.
 */
const props = defineProps<{ statut?: StatutSync | null }>()

const apparence = computed(() => {
  switch (props.statut) {
    case 'OK':
      return {
        texte: 'À jour',
        classes: 'bg-[var(--color-succes-fond)] text-[var(--color-succes)]',
        infobulle: 'Dernier passage réussi',
      }
    case 'EN_COURS':
      return {
        texte: 'En cours',
        classes: 'bg-[var(--color-accent-fond)] text-[var(--color-accent)]',
        infobulle: 'Une synchronisation est en train de tourner',
      }
    case 'AUTH_ERROR':
      return {
        texte: 'À reconnecter',
        classes: 'bg-[var(--color-manque-fond)] text-[var(--color-manque)]',
        infobulle: 'Garmin a refusé les identifiants : il faut les ressaisir',
      }
    case 'IDENTITE_KO':
      return {
        texte: 'Mauvais compte',
        classes: 'bg-[var(--color-manque-fond)] text-[var(--color-manque)]',
        infobulle:
          'Le compte connecté n’est pas celui de cet athlète : la synchronisation a été arrêtée',
      }
    case 'MFA_REQUISE':
      return {
        texte: 'Code attendu',
        classes: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
        infobulle: 'Garmin réclame un code de vérification à six chiffres',
      }
    case 'ERREUR':
      return {
        texte: 'En échec',
        classes: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
        infobulle: 'Échec passager : le passage suivant réessaiera',
      }
    default:
      return {
        texte: 'Jamais lancée',
        classes: 'bg-[var(--color-appui)] text-[var(--color-doux)]',
        infobulle: undefined,
      }
  }
})
</script>

<template>
  <span
    class="rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap"
    :class="apparence.classes"
    :title="apparence.infobulle"
  >
    {{ apparence.texte }}
  </span>
</template>
