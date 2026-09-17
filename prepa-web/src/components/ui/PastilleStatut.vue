<script setup lang="ts">
import { computed } from 'vue'
import type { StatutSeance } from '@/api/types'

const props = defineProps<{ statut: StatutSeance }>()

const apparence = computed(() => {
  switch (props.statut) {
    // Le constat est neutre : la séance a eu lieu, elle n'a pas encore été regardée.
    case 'REALISEE':
      return {
        texte: 'Faite',
        classes: 'bg-[var(--color-succes-fond)] text-[var(--color-succes)]',
        infobulle: 'Ton coach ne l’a pas encore passée en revue',
      }
    case 'ANALYSEE':
      return {
        texte: 'Analysée',
        classes: 'bg-[var(--color-accent-fond)] text-[var(--color-accent)]',
        infobulle: 'Passée en revue par ton coach',
      }
    case 'NON_REALISEE':
      return {
        texte: 'Non faite',
        classes: 'bg-[var(--color-manque-fond)] text-[var(--color-manque)]',
        infobulle: 'Aucune activité ne correspond à cette séance',
      }
    case 'DEPLACEE':
      return {
        texte: 'Décalée',
        classes: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
        infobulle: 'Reportée à un autre jour de la semaine',
      }
    case 'ANNULEE':
      return {
        texte: 'Annulée',
        classes: 'bg-[var(--color-appui)] text-[var(--color-doux)]',
        infobulle: 'Retirée du plan',
      }
    default:
      return {
        texte: 'À venir',
        classes: 'bg-[var(--color-appui)] text-[var(--color-doux)]',
        infobulle: undefined,
      }
  }
})
</script>

<template>
  <span
    class="rounded-full px-2 py-0.5 text-xs font-medium"
    :class="apparence.classes"
    :title="apparence.infobulle"
  >
    {{ apparence.texte }}
  </span>
</template>
