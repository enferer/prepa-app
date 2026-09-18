<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * Une note de coach, repliée par défaut.
 *
 * <p>Les notes de semaine expliquent un choix d'entraînement : on les lit une fois, le jour
 * où elles sont écrites, puis elles restent là pendant sept jours à occuper le tiers de
 * l'écran. Deux lignes suffisent à savoir s'il y a quelque chose à lire ; le reste se
 * déplie à la demande.
 */
const props = withDefaults(defineProps<{ texte: string; seuil?: number }>(), { seuil: 110 })

const deplie = ref(false)
const longue = computed(() => props.texte.length > props.seuil)
</script>

<template>
  <div class="border-l-2 border-[var(--color-accent)] pl-3 text-sm text-[var(--color-doux)]">
    <p :class="longue && !deplie ? 'line-clamp-2' : ''">{{ texte }}</p>
    <button
      v-if="longue"
      class="mt-0.5 cursor-pointer text-xs font-medium text-[var(--color-accent)]"
      @click="deplie = !deplie"
    >
      {{ deplie ? 'Replier' : 'Lire la suite' }}
    </button>
  </div>
</template>
