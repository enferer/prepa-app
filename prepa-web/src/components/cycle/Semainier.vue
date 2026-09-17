<script setup lang="ts">
import { computed } from 'vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import PastilleStatut from '@/components/ui/PastilleStatut.vue'
import { km } from '@/composables/useFormat'
import type { Seance, Semaine } from '@/api/types'

const props = defineProps<{ semaine: Semaine; volumeRealise: number }>()
const emit = defineEmits<{ ouvrir: [seance: Seance] }>()

const JOURS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']

const aujourdhui = new Date().toISOString().slice(0, 10)

/**
 * Les sept jours, chacun avec ses séances.
 *
 * <p>Une semaine d'entraînement se lit comme un calendrier, pas comme une liste : c'est la
 * place des jours de repos entre les séances qui dit si la charge est tenable. On affiche
 * donc les sept jours, même vides.
 */
const jours = computed(() =>
  Array.from({ length: 7 }, (_, decalage) => {
    const jour = new Date(props.semaine.dateDebut)
    jour.setDate(jour.getDate() + decalage)
    const iso = jour.toISOString().slice(0, 10)
    return {
      iso,
      nom: JOURS[decalage],
      numero: jour.getDate(),
      aujourdhui: iso === aujourdhui,
      passe: iso < aujourdhui,
      seances: props.semaine.seances
        .filter((s) => s.date === iso)
        .sort((a, b) => a.ordre - b.ordre),
    }
  }),
)

const partDeLaCible = computed(() =>
  props.semaine.volumeCibleKm ? (props.volumeRealise / props.semaine.volumeCibleKm) * 100 : null,
)
</script>

<template>
  <div>
    <!-- Barre de progression du volume : où en est la semaine, d'un coup d'œil. -->
    <div class="mb-4">
      <div class="flex items-baseline justify-between text-sm">
        <span class="tabulaire">
          <span class="font-semibold">{{ km(volumeRealise) }}</span>
          <span class="text-[var(--color-doux)]"> sur {{ km(semaine.volumeCibleKm) }} visés</span>
        </span>
        <span v-if="partDeLaCible !== null" class="tabulaire text-sm text-[var(--color-doux)]">
          {{ Math.round(partDeLaCible) }} %
        </span>
      </div>
      <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-[var(--color-appui)]">
        <div
          class="h-full rounded-full transition-[width]"
          :class="partDeLaCible !== null && partDeLaCible >= 90
            ? 'bg-[var(--color-succes)]'
            : 'bg-[var(--color-accent)]'"
          :style="{ width: `${Math.min(100, partDeLaCible ?? 0)}%` }"
        />
      </div>
    </div>

    <!-- Sept colonnes sur écran large, sept lignes sur téléphone : dans les deux cas,
         un jour = un bloc, et les jours de repos restent visibles. -->
    <ol class="grid gap-2 sm:grid-cols-7">
      <li
        v-for="jour in jours"
        :key="jour.iso"
        class="rounded-lg border p-2 transition-colors"
        :class="[
          jour.aujourdhui
            ? 'border-[var(--color-accent)] bg-[var(--color-accent-fond)]'
            : 'border-[var(--color-bordure)]',
          jour.passe && !jour.aujourdhui ? 'opacity-70' : '',
        ]"
      >
        <div class="mb-1.5 flex items-baseline gap-1.5">
          <span
            class="text-xs font-medium uppercase"
            :class="jour.aujourdhui ? 'text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
          >
            {{ jour.nom }}
          </span>
          <span class="tabulaire text-xs text-[var(--color-doux)]">{{ jour.numero }}</span>
          <span v-if="jour.aujourdhui" class="ml-auto text-xs text-[var(--color-accent)]">aujourd'hui</span>
        </div>

        <p v-if="!jour.seances.length" class="py-1 text-xs text-[var(--color-doux)]">repos</p>

        <button
          v-for="seance in jour.seances"
          :key="seance.id"
          class="mb-1 block w-full rounded-md bg-[var(--color-surface)] p-1.5 text-left last:mb-0 hover:bg-[var(--color-appui)]"
          @click="emit('ouvrir', seance)"
        >
          <div class="flex flex-wrap items-center gap-1">
            <EtiquetteType :type="seance.type" compact />
            <PastilleStatut :statut="seance.statut" />
          </div>
          <p class="mt-1 text-xs leading-snug">{{ seance.titre }}</p>
          <p v-if="seance.distanceCibleKm" class="tabulaire mt-0.5 text-xs text-[var(--color-doux)]">
            {{ km(seance.distanceCibleKm) }}
          </p>
        </button>
      </li>
    </ol>
  </div>
</template>
