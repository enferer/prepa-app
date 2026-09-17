<script setup lang="ts">
import { computed } from 'vue'
import type { StatutSeance } from '@/api/types'

const props = defineProps<{ statut: StatutSeance }>()

const apparence = computed(() => {
  switch (props.statut) {
    case 'VALIDEE':
      return { texte: 'Faite', classes: 'bg-[var(--color-succes-fond)] text-[var(--color-succes)]' }
    case 'MANQUEE':
      return { texte: 'Manquée', classes: 'bg-[var(--color-manque-fond)] text-[var(--color-manque)]' }
    case 'DEPLACEE':
      return { texte: 'Décalée', classes: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]' }
    case 'ANNULEE':
      return { texte: 'Annulée', classes: 'bg-[var(--color-appui)] text-[var(--color-doux)]' }
    default:
      return { texte: 'À venir', classes: 'bg-[var(--color-appui)] text-[var(--color-doux)]' }
  }
})
</script>

<template>
  <span class="rounded-full px-2 py-0.5 text-xs font-medium" :class="apparence.classes">
    {{ apparence.texte }}
  </span>
</template>
