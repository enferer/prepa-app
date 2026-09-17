<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useLargeur } from '@/composables/useLargeur'
import type { SemaineVolume } from '@/api/types'

const props = defineProps<{ semaines: SemaineVolume[]; cibles?: Record<string, number> }>()

/**
 * Volume hebdomadaire en barres.
 *
 * <p>Les barres se partagent la largeur disponible au lieu d'avoir une taille fixe : sur
 * grand écran le graphe remplissait mal, sur petit il débordait derrière une barre de
 * défilement. Dessiné en SVG plutôt qu'avec une bibliothèque — une série de barres ne
 * justifie pas cent kilooctets de dépendance, et le rendu suit la palette du thème.
 */
const conteneur = useTemplateRef<HTMLElement>('conteneur')
const largeur = useLargeur(conteneur, 700)

const HAUTEUR = 150
const MARGE_BAS = 24

const maximum = computed(() => {
  const valeurs = props.semaines.map((s) => s.km)
  const cibles = Object.values(props.cibles ?? {})
  return Math.max(1, ...valeurs, ...cibles)
})

/** Un quart de la largeur d'une barre en écart : dense sans être collé. */
const pas = computed(() => largeur.value / Math.max(1, props.semaines.length))
const largeurBarre = computed(() => Math.max(2, pas.value * 0.78))

const barres = computed(() =>
  props.semaines.map((semaine, index) => {
    const cible = props.cibles?.[semaine.lundi]
    return {
      ...semaine,
      x: index * pas.value,
      hauteur: (semaine.km / maximum.value) * (HAUTEUR - MARGE_BAS),
      hauteurCible: cible ? (cible / maximum.value) * (HAUTEUR - MARGE_BAS) : null,
      cible,
    }
  }),
)

/** Une étiquette sur deux, ou moins, selon la place : sinon elles se chevauchent. */
const intervalleEtiquettes = computed(() => Math.max(1, Math.ceil(28 / pas.value)))
</script>

<template>
  <div ref="conteneur" class="w-full">
    <svg :width="largeur" :height="HAUTEUR" class="block">
      <g v-for="(barre, index) in barres" :key="barre.lundi">
        <!-- La cible apparaît en trait, le réalisé en plein : l'écart se lit sans légende. -->
        <line
          v-if="barre.hauteurCible"
          :x1="barre.x"
          :x2="barre.x + largeurBarre"
          :y1="HAUTEUR - MARGE_BAS - barre.hauteurCible"
          :y2="HAUTEUR - MARGE_BAS - barre.hauteurCible"
          stroke="var(--color-doux)"
          stroke-width="1.5"
          stroke-dasharray="3 2"
        />
        <rect
          :x="barre.x"
          :y="HAUTEUR - MARGE_BAS - barre.hauteur"
          :width="largeurBarre"
          :height="Math.max(barre.km ? 1 : 0, barre.hauteur)"
          rx="2"
          fill="var(--color-accent)"
          :opacity="barre.nbCourses ? 0.9 : 0.25"
        >
          <title>
            Semaine du {{ barre.lundi }} — {{ barre.km.toFixed(1) }} km,
            {{ barre.nbCourses }} sortie{{ barre.nbCourses > 1 ? 's' : '' }}{{
              barre.cible ? ` (objectif ${barre.cible} km)` : ''
            }}
          </title>
        </rect>
        <text
          v-if="index % intervalleEtiquettes === 0"
          :x="barre.x + largeurBarre / 2"
          :y="HAUTEUR - 8"
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
