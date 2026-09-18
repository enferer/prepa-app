<script setup lang="ts">
import { computed } from 'vue'
import { chrono, dateLongue } from '@/composables/useFormat'
import type { Cycle } from '@/api/types'

/**
 * Où en est la préparation — le décor de la séance du jour.
 *
 * <p>Il tient sa place en haut de l'écran : voir le chemin parcouru fait partie de ce qu'on
 * vient chercher, et une barre d'un pixel ne le donne pas. Le compte à rebours, la semaine
 * et la part du cycle déjà courue se lisent donc en grand, d'un seul regard.
 */
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
      <span v-if="semaineCourante" class="tabulaire text-sm text-[var(--color-doux)]">
        semaine {{ semaineCourante }} sur {{ cycle.nbSemaines }}
      </span>
    </div>

    <p v-if="cycle.type === 'PREPA'" class="mt-1 text-sm text-[var(--color-doux)]">
      <!-- Le nom de la course, seulement s'il apprend autre chose que celui du cycle. -->
      <template v-if="cycle.courseNom && cycle.courseNom !== cycle.nom">
        {{ cycle.courseNom }} ·
      </template>
      {{ dateLongue(cycle.courseDate) }}
      <template v-if="cycle.chronoViseSec"> · {{ chrono(cycle.chronoViseSec) }}</template>
    </p>
    <p v-else class="mt-1 text-sm text-[var(--color-doux)]">{{ cycle.ligneDirectrice }}</p>

    <!-- Le chemin parcouru, avec sa part chiffrée : c'est ce qu'on vient regarder. -->
    <div class="mt-3 flex items-center gap-3">
      <div class="h-2 flex-1 overflow-hidden rounded-full bg-[var(--color-surface)]">
        <div
          class="h-full rounded-full bg-[var(--color-accent)] transition-[width]"
          :style="{ width: `${avancement}%` }"
        />
      </div>
      <span class="tabulaire shrink-0 text-sm font-medium text-[var(--color-accent)]">
        {{ avancement }} %
      </span>
    </div>
  </section>
</template>
