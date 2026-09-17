<script setup lang="ts">
import { computed } from 'vue'
import type { SemaineVolume } from '@/api/types'

const props = defineProps<{ semaines: SemaineVolume[]; cibles?: Record<string, number> }>()

/**
 * Volume hebdomadaire en barres.
 *
 * <p>Dessine en SVG plutot qu'avec une bibliotheque de graphiques : une serie de barres ne
 * justifie pas cent kilooctets de dependance, et le rendu suit la palette du theme sans
 * configuration.
 */
const HAUTEUR = 120

const maximum = computed(() => {
  const valeurs = props.semaines.map((s) => s.km)
  const cibles = Object.values(props.cibles ?? {})
  return Math.max(1, ...valeurs, ...cibles)
})

const barres = computed(() =>
  props.semaines.map((semaine, index) => ({
    ...semaine,
    x: index,
    hauteur: (semaine.km / maximum.value) * HAUTEUR,
    cible: props.cibles?.[semaine.lundi],
    hauteurCible: props.cibles?.[semaine.lundi]
      ? (props.cibles[semaine.lundi] / maximum.value) * HAUTEUR
      : null,
  })),
)

const largeurBarre = computed(() => Math.max(4, Math.min(28, 700 / Math.max(1, barres.value.length))))
</script>

<template>
  <div class="overflow-x-auto">
    <svg
      :width="Math.max(barres.length * (largeurBarre + 4), 200)"
      :height="HAUTEUR + 28"
      class="block"
    >
      <g v-for="barre in barres" :key="barre.lundi">
        <!-- La cible apparait en trait, le realise en plein : l'ecart se lit sans legende. -->
        <line
          v-if="barre.hauteurCible"
          :x1="barre.x * (largeurBarre + 4)"
          :x2="barre.x * (largeurBarre + 4) + largeurBarre"
          :y1="HAUTEUR - barre.hauteurCible"
          :y2="HAUTEUR - barre.hauteurCible"
          stroke="var(--color-doux)"
          stroke-width="1.5"
          stroke-dasharray="3 2"
        />
        <rect
          :x="barre.x * (largeurBarre + 4)"
          :y="HAUTEUR - barre.hauteur"
          :width="largeurBarre"
          :height="barre.hauteur"
          rx="2"
          fill="var(--color-accent)"
          :opacity="barre.nbCourses ? 0.9 : 0.25"
        >
          <title>{{ barre.lundi }} — {{ barre.km.toFixed(1) }} km, {{ barre.nbCourses }} sorties</title>
        </rect>
        <text
          v-if="barres.length <= 16 || barre.x % 2 === 0"
          :x="barre.x * (largeurBarre + 4) + largeurBarre / 2"
          :y="HAUTEUR + 14"
          text-anchor="middle"
          font-size="9"
          fill="var(--color-doux)"
        >
          S{{ barre.numeroIso }}
        </text>
      </g>
    </svg>
  </div>
</template>
