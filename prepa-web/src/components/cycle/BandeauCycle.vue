<script setup lang="ts">
import { computed } from 'vue'
import { chrono, dateLongue } from '@/composables/useFormat'
import type { Cycle } from '@/api/types'

const props = defineProps<{ cycle: Cycle; semaineCourante?: number | null }>()

/**
 * Deux cycles, deux facons de se situer : une preparation se compte en jours avant la course,
 * un cycle libre en semaines parcourues sur son horizon.
 */
const compteARebours = computed(() => {
  const jours = props.cycle.joursAvantCourse
  if (jours === undefined || jours === null) return null
  if (jours > 0) return `J−${jours}`
  if (jours === 0) return "C'est aujourd'hui"
  return `Course passée depuis ${Math.abs(jours)} jours`
})

const avancement = computed(() => {
  const debut = new Date(props.cycle.dateDebut).getTime()
  const fin = new Date(props.cycle.dateFin).getTime()
  const maintenant = Date.now()
  if (maintenant <= debut) return 0
  if (maintenant >= fin) return 100
  return Math.round(((maintenant - debut) / (fin - debut)) * 100)
})
</script>

<template>
  <section class="rounded-xl bg-[var(--color-accent-fond)] p-4 sm:p-5">
    <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <h1 class="text-lg font-semibold">{{ cycle.nom }}</h1>
      <span
        v-if="compteARebours"
        class="tabulaire rounded-full bg-[var(--color-accent)] px-2.5 py-0.5 text-sm font-medium text-white"
      >
        {{ compteARebours }}
      </span>
      <span v-if="semaineCourante" class="text-sm text-[var(--color-doux)]">
        semaine {{ semaineCourante }} sur {{ cycle.nbSemaines }}
      </span>
    </div>

    <p v-if="cycle.type === 'PREPA'" class="mt-1 text-sm text-[var(--color-doux)]">
      {{ cycle.courseNom }} le {{ dateLongue(cycle.courseDate) }}
      <template v-if="cycle.chronoViseSec"> — objectif {{ chrono(cycle.chronoViseSec) }}</template>
    </p>
    <p v-else class="mt-1 text-sm text-[var(--color-doux)]">{{ cycle.ligneDirectrice }}</p>

    <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-[var(--color-surface)]">
      <div class="h-full rounded-full bg-[var(--color-accent)]" :style="{ width: `${avancement}%` }" />
    </div>
  </section>
</template>
