<script setup lang="ts">
import { computed } from 'vue'
import { dureeCourte } from '@/composables/useFormat'
import type { ZoneFc } from '@/api/types'

const props = defineProps<{ zones: ZoneFc[] }>()

/** Du bleu au rouge : l'intensite se lit a la couleur avant de se lire au chiffre. */
const TEINTES = ['#7aa7d8', '#69b58c', '#d8c26a', '#dd9457', '#cc5f63']

const total = computed(() => props.zones.reduce((somme, z) => somme + z.secondes, 0))

/**
 * Les zones de moins de trente secondes sont ecartees : ce sont des passages de transition,
 * les afficher produirait des bandes illisibles sans rien apprendre.
 */
const retenues = computed(() =>
  props.zones
    .filter((z) => z.secondes >= 30)
    .map((z) => ({
      ...z,
      part: total.value ? (z.secondes / total.value) * 100 : 0,
      teinte: TEINTES[Math.min(z.zone - 1, TEINTES.length - 1)],
    })),
)
</script>

<template>
  <div v-if="retenues.length">
    <div class="flex h-7 overflow-hidden rounded-lg">
      <div
        v-for="zone in retenues"
        :key="zone.zone"
        class="flex items-center justify-center text-xs font-medium text-white"
        :style="{ width: `${zone.part}%`, backgroundColor: zone.teinte }"
        :title="`Zone ${zone.zone} — ${dureeCourte(zone.secondes)}`"
      >
        <!-- Sous cinq pour cent du temps, le libelle ne tient pas : on laisse la couleur parler. -->
        <span v-if="zone.part >= 5">Z{{ zone.zone }}</span>
      </div>
    </div>
    <div class="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--color-doux)]">
      <span v-for="zone in retenues" :key="zone.zone" class="tabulaire">
        Z{{ zone.zone }}<template v-if="zone.borneBasse"> ({{ zone.borneBasse }}+)</template>
        · {{ dureeCourte(zone.secondes) }}
      </span>
    </div>
  </div>
</template>
