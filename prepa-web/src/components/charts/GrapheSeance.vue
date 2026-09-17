<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { useLargeur } from '@/composables/useLargeur'
import { allure } from '@/composables/useFormat'
import type { Tour } from '@/api/types'

const props = defineProps<{ tours: Tour[] }>()

/**
 * Le déroulé d'une séance, tour par tour.
 *
 * <p>Trois grandeurs sur un même axe horizontal, parce qu'elles ne se lisent qu'ensemble :
 * l'allure dit ce qui a été couru, la fréquence cardiaque ce qu'il en a coûté, le dénivelé
 * pourquoi. Une allure qui s'effondre dans une montée n'est pas une baisse de régime ; la
 * même allure sur le plat avec une FC qui grimpe, si.
 *
 * <p>L'allure est tracée à l'envers — plus c'est rapide, plus c'est haut — parce que c'est
 * ainsi qu'on lit une performance.
 */
const conteneur = useTemplateRef<HTMLElement>('conteneur')
const largeur = useLargeur(conteneur, 700)

const HAUTEUR = 190
const MARGE = { haut: 12, bas: 26, gauche: 44, droite: 40 }

const afficheFc = ref(true)
const afficheDenivele = ref(true)

const aDesFc = computed(() => props.tours.some((t) => t.fcMoy))
const aDuDenivele = computed(() => props.tours.some((t) => (t.denivelePosM ?? 0) > 0))

/** Position horizontale cumulée : un tour long occupe plus de place qu'un tour court. */
const points = computed(() => {
  const total = props.tours.reduce((somme, t) => somme + (t.distanceM ?? 0), 0)
  if (!total) return []

  let cumul = 0
  return props.tours.map((tour) => {
    const debut = cumul
    cumul += tour.distanceM ?? 0
    return {
      tour,
      partDebut: debut / total,
      partFin: cumul / total,
      partMilieu: (debut + cumul) / 2 / total,
    }
  })
})

const largeurTrace = computed(() => Math.max(120, largeur.value - MARGE.gauche - MARGE.droite))
const hauteurTrace = HAUTEUR - MARGE.haut - MARGE.bas

function x(part: number): number {
  return MARGE.gauche + part * largeurTrace.value
}

/** Bornes d'allure, élargies de 5 % pour que la courbe ne colle pas aux bords. */
const bornesAllure = computed(() => {
  const valeurs = props.tours.map((t) => t.allureSecKm).filter((a): a is number => !!a)
  if (!valeurs.length) return { min: 0, max: 1 }
  const min = Math.min(...valeurs)
  const max = Math.max(...valeurs)
  const marge = Math.max(10, (max - min) * 0.05)
  return { min: min - marge, max: max + marge }
})

function yAllure(secKm: number): number {
  const { min, max } = bornesAllure.value
  // Inversé : une allure basse (rapide) monte dans le graphe.
  return MARGE.haut + ((secKm - min) / (max - min)) * hauteurTrace
}

const bornesFc = computed(() => {
  const valeurs = props.tours.map((t) => t.fcMoy).filter((f): f is number => !!f)
  if (!valeurs.length) return { min: 0, max: 1 }
  return { min: Math.min(...valeurs) - 5, max: Math.max(...valeurs) + 5 }
})

function yFc(fc: number): number {
  const { min, max } = bornesFc.value
  return MARGE.haut + hauteurTrace - ((fc - min) / (max - min)) * hauteurTrace
}

/** Profil d'altitude reconstitué en cumulant le dénivelé net de chaque tour. */
const profil = computed(() => {
  let altitude = 0
  const suite = [{ part: 0, altitude: 0 }]
  for (const point of points.value) {
    altitude += point.tour.deniveleNetM
    suite.push({ part: point.partFin, altitude })
  }
  return suite
})

const bornesAltitude = computed(() => {
  const valeurs = profil.value.map((p) => p.altitude)
  const min = Math.min(...valeurs)
  const max = Math.max(...valeurs)
  return { min, max: max === min ? min + 1 : max }
})

function yAltitude(altitude: number): number {
  const { min, max } = bornesAltitude.value
  // Le relief occupe le tiers bas du graphe : c'est un décor, pas le sujet.
  const hauteurRelief = hauteurTrace * 0.33
  return MARGE.haut + hauteurTrace - ((altitude - min) / (max - min)) * hauteurRelief
}

const cheminDenivele = computed(() => {
  if (!profil.value.length) return ''
  const bas = MARGE.haut + hauteurTrace
  const debut = `M ${x(0)} ${bas}`
  const trace = profil.value.map((p) => `L ${x(p.part)} ${yAltitude(p.altitude)}`).join(' ')
  return `${debut} ${trace} L ${x(1)} ${bas} Z`
})

/** Le profil seul, sans la fermeture vers le bas : c'est lui qu'on trace en trait. */
const cheminProfil = computed(() =>
  profil.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(p.part)} ${yAltitude(p.altitude)}`).join(' '),
)

const cheminFc = computed(() =>
  points.value
    .filter((p) => p.tour.fcMoy)
    .map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(p.partMilieu)} ${yFc(p.tour.fcMoy!)}`)
    .join(' '),
)

/** Repères d'allure : trois valeurs suffisent à situer la courbe. */
const graduationsAllure = computed(() => {
  const { min, max } = bornesAllure.value
  return [min + (max - min) * 0.15, (min + max) / 2, max - (max - min) * 0.15].map((v) => ({
    valeur: Math.round(v),
    y: yAllure(v),
  }))
})

const survole = ref<number | null>(null)
</script>

<template>
  <div ref="conteneur" class="w-full">
    <div class="mb-2 flex flex-wrap items-center gap-3 text-xs">
      <span class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-sm bg-[var(--color-accent)]" />
        allure
      </span>
      <label v-if="aDesFc" class="flex cursor-pointer items-center gap-1.5">
        <input v-model="afficheFc" type="checkbox" class="accent-[var(--color-manque)]" />
        <span class="inline-block h-2.5 w-2.5 rounded-sm bg-[var(--color-manque)]" />
        fréquence cardiaque
      </label>
      <label v-if="aDuDenivele" class="flex cursor-pointer items-center gap-1.5">
        <input v-model="afficheDenivele" type="checkbox" class="accent-[var(--color-doux)]" />
        <span class="inline-block h-2.5 w-2.5 rounded-sm bg-[var(--color-appui)]" />
        dénivelé
      </label>
    </div>

    <svg :width="largeur" :height="HAUTEUR" class="block">
      <g v-for="graduation in graduationsAllure" :key="graduation.valeur">
        <line
          :x1="MARGE.gauche"
          :x2="MARGE.gauche + largeurTrace"
          :y1="graduation.y"
          :y2="graduation.y"
          stroke="var(--color-bordure)"
          stroke-dasharray="2 3"
        />
        <text
          :x="MARGE.gauche - 6"
          :y="graduation.y + 3"
          text-anchor="end"
          font-size="9"
          fill="var(--color-doux)"
        >
          {{ allure(graduation.valeur) }}
        </text>
      </g>

      <!-- Une barre par tour : sa hauteur est l'allure tenue. -->
      <g v-for="(point, index) in points" :key="point.tour.index">
        <rect
          v-if="point.tour.allureSecKm"
          :x="x(point.partDebut) + 0.5"
          :y="yAllure(point.tour.allureSecKm)"
          :width="Math.max(1, x(point.partFin) - x(point.partDebut) - 1)"
          :height="Math.max(1, MARGE.haut + hauteurTrace - yAllure(point.tour.allureSecKm))"
          :fill="point.tour.intensite === 'INTERVAL' || point.tour.intensite === 'ACTIVE'
            ? 'var(--color-accent)'
            : 'color-mix(in srgb, var(--color-accent) 45%, transparent)'"
          :opacity="survole === null || survole === index ? 1 : 0.55"
          @mouseenter="survole = index"
          @mouseleave="survole = null"
        >
          <title>
            Tour {{ point.tour.index }} — {{ allure(point.tour.allureSecKm) }}/km{{
              point.tour.fcMoy ? `, FC ${point.tour.fcMoy}` : ''
            }}{{ point.tour.deniveleNetM ? `, ${point.tour.deniveleNetM > 0 ? '+' : ''}${point.tour.deniveleNetM} m` : '' }}
          </title>
        </rect>
      </g>

      <!--
        Le relief par-dessus les barres, en trait plutôt qu'en aire pleine : dessiné
        derrière, il disparaissait sous les barres exactement sur les séances où il
        explique tout — celles qui montent.
      -->
      <g v-if="afficheDenivele && aDuDenivele">
        <path :d="cheminDenivele" fill="var(--color-texte)" opacity="0.07" />
        <path
          :d="cheminProfil"
          fill="none"
          stroke="var(--color-doux)"
          stroke-width="1.4"
          stroke-linejoin="round"
        />
      </g>

      <path
        v-if="afficheFc && aDesFc"
        :d="cheminFc"
        fill="none"
        stroke="var(--color-manque)"
        stroke-width="1.8"
        stroke-linejoin="round"
      />

      <g v-if="afficheFc && aDesFc">
        <text
          :x="MARGE.gauche + largeurTrace + 6"
          :y="yFc(bornesFc.max) + 8"
          font-size="9"
          fill="var(--color-manque)"
        >
          {{ Math.round(bornesFc.max) }}
        </text>
        <text
          :x="MARGE.gauche + largeurTrace + 6"
          :y="yFc(bornesFc.min)"
          font-size="9"
          fill="var(--color-manque)"
        >
          {{ Math.round(bornesFc.min) }}
        </text>
      </g>

      <line
        :x1="MARGE.gauche"
        :x2="MARGE.gauche + largeurTrace"
        :y1="MARGE.haut + hauteurTrace"
        :y2="MARGE.haut + hauteurTrace"
        stroke="var(--color-bordure)"
      />

      <text
        v-if="afficheDenivele && aDuDenivele"
        :x="MARGE.gauche + 4"
        :y="MARGE.haut + hauteurTrace - 4"
        font-size="9"
        fill="var(--color-doux)"
      >
        {{ Math.round(bornesAltitude.max - bornesAltitude.min) }} m de relief
      </text>

      <text :x="MARGE.gauche" :y="HAUTEUR - 8" font-size="9" fill="var(--color-doux)">départ</text>
      <text
        :x="MARGE.gauche + largeurTrace"
        :y="HAUTEUR - 8"
        text-anchor="end"
        font-size="9"
        fill="var(--color-doux)"
      >
        arrivée
      </text>
    </svg>
  </div>
</template>
