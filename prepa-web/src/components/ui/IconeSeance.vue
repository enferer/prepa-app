<script setup lang="ts">
import { computed } from 'vue'
import type { TypeSeance } from '@/api/types'

/**
 * Le pictogramme d'un type de séance.
 *
 * <p>Dans le semainier, sept cases se lisent au regard, pas à la lecture : un coureur, un
 * haltère et une lune se distinguent avant qu'on ait déchiffré « Endurance », « Renfo » et
 * « repos ». Le dessin dit la nature de la séance, la couleur dit son intensité — deux
 * informations qui ne se recouvrent pas.
 *
 * <p>Les côtes et la course ont leur propre signe parce qu'elles ne se préparent pas comme
 * le reste : un relief et un drapeau. Toutes les autres foulées partagent le coureur.
 */
const props = defineProps<{ type: TypeSeance }>()

const TRACES = {
  COURSE_A_PIED:
    '<circle cx="15.6" cy="4.4" r="1.9" fill="currentColor" stroke="none"/>' +
    '<path d="M14.6 7 11.4 10.9"/>' +
    '<path d="M11.4 10.9 14.2 13.9 13.2 18.6"/>' +
    '<path d="M11.4 10.9 8.4 13.4 5.6 16.3"/>' +
    '<path d="M13.9 7.9 16.7 9.9 15 12.4"/>' +
    '<path d="M13.9 7.9 10.2 8.6 7.6 10.6"/>',
  RELIEF: '<path d="M3 19 9.5 8l4 6 2.5-3.5L21 19Z"/>',
  DOSSARD: '<path d="M6 21V4"/><path d="M6 5h11l-2.2 3.6L17 12.5H6z"/>',
  HALTERE: '<path d="M6 8v8M4 10.5v3M18 8v8M20 10.5v3M6 12h12"/>',
  VELO:
    '<circle cx="5.8" cy="16.5" r="3.6"/><circle cx="18.2" cy="16.5" r="3.6"/>' +
    '<path d="M5.8 16.5 10 9.5h5.5l2.7 7M9.6 9.5h4.4M15.6 6.5h2.1l.7 2.5"/>',
  LUNE: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
} as const

const SIGNES: Record<TypeSeance, keyof typeof TRACES> = {
  EF: 'COURSE_A_PIED',
  SL: 'COURSE_A_PIED',
  SEUIL: 'COURSE_A_PIED',
  VMA: 'COURSE_A_PIED',
  AM: 'COURSE_A_PIED',
  COTES: 'RELIEF',
  COURSE: 'DOSSARD',
  RENFO: 'HALTERE',
  CROSS: 'VELO',
  REPOS: 'LUNE',
}

const trace = computed(() => TRACES[SIGNES[props.type]])
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="trace"
  />
</template>
