<script setup lang="ts">
import { computed, ref } from 'vue'
import { allure, duree, signe } from '@/composables/useFormat'
import type { Bloc, Tour } from '@/api/types'

const props = defineProps<{ tours: Tour[]; blocs: Bloc[]; structuree: boolean }>()

/**
 * Deux lectures d'une meme seance. En blocs, les tours consecutifs de meme intensite sont
 * regroupes : les trois fois deux kilometres d'un seuil apparaissent en trois lignes, ce qui
 * redonne la seance telle qu'elle a ete concue. En tours, le decoupage brut de la montre.
 *
 * <p>La bascule n'a de sens que sur une seance structuree : sur une sortie en tours
 * automatiques, tout se fondrait en un bloc unique.
 */
const mode = ref<'blocs' | 'tours'>(props.structuree ? 'blocs' : 'tours')

const INTENSITES: Record<string, string> = {
  WARMUP: 'échauffement',
  ACTIVE: 'actif',
  INTERVAL: 'effort',
  REST: 'récup',
  RECOVERY: 'récup',
  COOLDOWN: 'retour au calme',
  UNKNOWN: 'tour',
}

const TONS: Record<string, string> = {
  INTERVAL: 'text-[var(--color-alerte)]',
  ACTIVE: 'text-[var(--color-texte)]',
  REST: 'text-[var(--color-doux)]',
  RECOVERY: 'text-[var(--color-doux)]',
  WARMUP: 'text-[var(--color-doux)]',
  COOLDOWN: 'text-[var(--color-doux)]',
}

/** Echelle commune aux barres d'allure : la plus rapide du lot fait la reference. */
const allureLaPlusRapide = computed(() => {
  const valeurs = props.tours.map((t) => t.allureSecKm).filter((a): a is number => !!a)
  return valeurs.length ? Math.min(...valeurs) : 0
})

const allureLaPlusLente = computed(() => {
  const valeurs = props.tours.map((t) => t.allureSecKm).filter((a): a is number => !!a)
  return valeurs.length ? Math.max(...valeurs) : 0
})

function largeur(allureSecKm?: number): number {
  if (!allureSecKm || allureLaPlusLente.value === allureLaPlusRapide.value) return 100
  const etendue = allureLaPlusLente.value - allureLaPlusRapide.value
  // Plus c'est rapide, plus la barre est longue.
  return 25 + ((allureLaPlusLente.value - allureSecKm) / etendue) * 75
}
</script>

<template>
  <div>
    <div v-if="structuree" class="mb-3 inline-flex rounded-lg border border-[var(--color-bordure)] p-0.5">
      <button
        v-for="choix in (['blocs', 'tours'] as const)"
        :key="choix"
        class="rounded-md px-3 py-1 text-sm capitalize"
        :class="mode === choix ? 'bg-[var(--color-accent-fond)] font-medium text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
        @click="mode = choix"
      >
        {{ choix }}
      </button>
    </div>

    <table v-if="mode === 'blocs'" class="w-full text-sm">
      <thead class="text-left text-xs text-[var(--color-doux)]">
        <tr class="border-b border-[var(--color-bordure)]">
          <th class="py-1.5 font-medium">Bloc</th>
          <th class="py-1.5 font-medium">Distance</th>
          <th class="py-1.5 font-medium">Temps</th>
          <th class="py-1.5 font-medium">Allure</th>
          <th class="py-1.5 font-medium">FC</th>
          <th class="hidden py-1.5 font-medium sm:table-cell">Détail</th>
        </tr>
      </thead>
      <tbody class="tabulaire divide-y divide-[var(--color-bordure)]">
        <tr v-for="bloc in blocs" :key="bloc.premierTour">
          <td class="py-1.5" :class="TONS[bloc.intensite]">
            {{ INTENSITES[bloc.intensite] ?? bloc.intensite }}
            <span v-if="bloc.nbTours > 1" class="text-[var(--color-doux)]">×{{ bloc.nbTours }}</span>
          </td>
          <td class="py-1.5">{{ (bloc.distanceM / 1000).toFixed(2) }} km</td>
          <td class="py-1.5">{{ duree(bloc.dureeSec) }}</td>
          <td class="py-1.5 font-medium">{{ allure(bloc.allureSecKm) }}</td>
          <td class="py-1.5">
            {{ bloc.fcMoy ?? '—' }}
            <span
              v-if="bloc.fcDebut && bloc.fcFin && Math.abs(bloc.fcFin - bloc.fcDebut) >= 5"
              class="text-xs text-[var(--color-doux)]"
            >
              ({{ signe(bloc.fcFin - bloc.fcDebut) }})
            </span>
          </td>
          <td class="hidden py-1.5 text-xs text-[var(--color-doux)] sm:table-cell">
            {{ bloc.alluresParTour.map((a) => allure(a ?? undefined)).join(' · ') }}
          </td>
        </tr>
      </tbody>
    </table>

    <table v-else class="w-full text-sm">
      <thead class="text-left text-xs text-[var(--color-doux)]">
        <tr class="border-b border-[var(--color-bordure)]">
          <th class="py-1.5 font-medium">#</th>
          <th class="py-1.5 font-medium">Distance</th>
          <th class="py-1.5 font-medium">Temps</th>
          <th class="py-1.5 font-medium">Allure</th>
          <th class="py-1.5 font-medium">FC</th>
          <th class="hidden py-1.5 font-medium sm:table-cell">D+/−</th>
        </tr>
      </thead>
      <tbody class="tabulaire divide-y divide-[var(--color-bordure)]">
        <tr v-for="tour in tours" :key="tour.index">
          <td class="py-1.5 text-[var(--color-doux)]">{{ tour.index }}</td>
          <td class="py-1.5">{{ ((tour.distanceM ?? 0) / 1000).toFixed(2) }} km</td>
          <td class="py-1.5">{{ duree(tour.dureeSec) }}</td>
          <td class="py-1.5">
            <div class="flex items-center gap-2">
              <span class="w-12 font-medium">{{ allure(tour.allureSecKm) }}</span>
              <span
                class="h-1.5 rounded-full bg-[var(--color-accent)]"
                :style="{ width: `${largeur(tour.allureSecKm) * 0.5}px` }"
              />
            </div>
          </td>
          <td class="py-1.5">{{ tour.fcMoy ?? '—' }}</td>
          <td class="hidden py-1.5 text-[var(--color-doux)] sm:table-cell">
            {{ signe(tour.deniveleNetM) }} m
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
