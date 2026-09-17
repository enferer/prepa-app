<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useLargeur } from '@/composables/useLargeur'
import type { ActivityResume } from '@/api/types'

const props = defineProps<{ activites: ActivityResume[]; mois?: number }>()
const emit = defineEmits<{ jour: [date: string] }>()

/**
 * Une année d'entraînement en une image.
 *
 * <p>Chaque case est un jour, sa teinte dit le kilométrage. Ce qu'on vient y chercher n'est
 * pas un chiffre précis mais une forme : les semaines pleines, les coupures, la régularité —
 * ou son absence. Un tableau de chiffres ne montrerait pas cela.
 */
const MOIS_COURTS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D']

const conteneur = useTemplateRef<HTMLElement>('conteneur')
const largeurDisponible = useLargeur(conteneur)

/** Écart entre deux cases, constant ; la taille des cases s'ajuste autour. */
const ECART = 3

const nbMois = computed(() => props.mois ?? 12)

/** Kilomètres par jour, tous types de course confondus. */
const parJour = computed(() => {
  const table = new Map<string, number>()
  for (const activite of props.activites) {
    if (!['RUN', 'TRAIL', 'TREADMILL'].includes(activite.type)) continue
    table.set(activite.date, (table.get(activite.date) ?? 0) + (activite.distanceM ?? 0) / 1000)
  }
  return table
})

const maximum = computed(() => Math.max(10, ...parJour.value.values()))

/** Les semaines affichées, du lundi le plus ancien jusqu'à aujourd'hui. */
const semaines = computed(() => {
  const fin = new Date()
  const debut = new Date()
  debut.setMonth(debut.getMonth() - nbMois.value)
  // On démarre un lundi pour que chaque colonne soit une semaine complète.
  debut.setDate(debut.getDate() - ((debut.getDay() + 6) % 7))

  const colonnes: { jours: { iso: string; km: number; mois: number }[] }[] = []
  const curseur = new Date(debut)

  while (curseur <= fin) {
    const jours = []
    for (let j = 0; j < 7; j++) {
      const iso = curseur.toISOString().slice(0, 10)
      jours.push({ iso, km: parJour.value.get(iso) ?? 0, mois: curseur.getMonth() })
      curseur.setDate(curseur.getDate() + 1)
    }
    colonnes.push({ jours })
  }
  return colonnes
})

/**
 * Les cases s'agrandissent pour remplir la largeur, dans des bornes qui gardent la grille
 * lisible : trop petites elles disparaissent, trop grandes elles deviennent un damier.
 */
const taille = computed(() => {
  const brut = largeurDisponible.value / Math.max(1, semaines.value.length) - ECART
  return Math.max(6, Math.min(20, Math.floor(brut)))
})

const pas = computed(() => taille.value + ECART)

const largeur = computed(() => semaines.value.length * pas.value)

/** Cinq paliers suffisent : au-delà, l'œil ne distingue plus rien. */
function teinte(km: number): string {
  if (km === 0) return 'var(--color-appui)'
  const part = km / maximum.value
  const opacite = part < 0.25 ? 30 : part < 0.5 ? 50 : part < 0.75 ? 75 : 100
  return `color-mix(in srgb, var(--color-accent) ${opacite}%, transparent)`
}

/** Un libellé de mois au-dessus de la première semaine qui commence dedans. */
const etiquettesMois = computed(() =>
  semaines.value
    .map((semaine, index) => ({ index, mois: semaine.jours[0].mois }))
    .filter((e, i, tableau) => i === 0 || tableau[i - 1].mois !== e.mois),
)
</script>

<template>
  <div ref="conteneur" class="w-full">
    <svg :width="largeur" :height="7 * pas + 18" class="block">
      <text
        v-for="etiquette in etiquettesMois"
        :key="etiquette.index"
        :x="etiquette.index * pas"
        y="9"
        font-size="9"
        fill="var(--color-doux)"
      >
        {{ MOIS_COURTS[etiquette.mois] }}
      </text>

      <g v-for="(semaine, colonne) in semaines" :key="colonne">
        <rect
          v-for="(jour, ligne) in semaine.jours"
          :key="jour.iso"
          :x="colonne * pas"
          :y="18 + ligne * pas"
          :width="taille"
          :height="taille"
          rx="2"
          :fill="teinte(jour.km)"
          class="cursor-pointer"
          @click="emit('jour', jour.iso)"
        >
          <title>{{ jour.iso }} — {{ jour.km ? `${jour.km.toFixed(1)} km` : 'repos' }}</title>
        </rect>
      </g>
    </svg>

    <div class="mt-2 flex items-center gap-1.5 text-xs text-[var(--color-doux)]">
      <span>moins</span>
      <span
        v-for="palier in [0, 0.2, 0.4, 0.6, 0.9]"
        :key="palier"
        class="inline-block h-3 w-3 rounded-sm"
        :style="{ backgroundColor: teinte(palier * maximum) }"
      />
      <span>plus</span>
    </div>
  </div>
</template>
