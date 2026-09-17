<script setup lang="ts">
import { computed } from 'vue'
import type { TypeSeance } from '@/api/types'

const props = defineProps<{ type: TypeSeance; compact?: boolean }>()

/** Libelles lisibles : l'athlete lit « Sortie longue », pas « SL ». */
const LIBELLES: Record<TypeSeance, string> = {
  EF: 'Endurance',
  SL: 'Sortie longue',
  SEUIL: 'Seuil',
  VMA: 'VMA',
  AM: 'Allure marathon',
  COTES: 'Côtes',
  RENFO: 'Renforcement',
  COURSE: 'Course',
  CROSS: 'Cross-training',
  REPOS: 'Repos',
}

const COURTS: Record<TypeSeance, string> = {
  EF: 'EF', SL: 'SL', SEUIL: 'Seuil', VMA: 'VMA', AM: 'AM',
  COTES: 'Côtes', RENFO: 'Renfo', COURSE: 'Course', CROSS: 'Cross', REPOS: 'Repos',
}

/** Les seances de qualite se distinguent des seances faciles au premier regard. */
const TONS: Partial<Record<TypeSeance, string>> = {
  SEUIL: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
  VMA: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
  COTES: 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]',
  AM: 'bg-[var(--color-accent-fond)] text-[var(--color-accent)]',
  SL: 'bg-[var(--color-accent-fond)] text-[var(--color-accent)]',
  COURSE: 'bg-[var(--color-accent-fond)] text-[var(--color-accent)]',
}

const libelle = computed(() => (props.compact ? COURTS : LIBELLES)[props.type])
const classes = computed(() => TONS[props.type] ?? 'bg-[var(--color-appui)] text-[var(--color-doux)]')
</script>

<template>
  <span class="rounded px-1.5 py-0.5 text-xs font-medium" :class="classes">{{ libelle }}</span>
</template>
