<script setup lang="ts">
import IconeSeance from '@/components/ui/IconeSeance.vue'
import { km } from '@/composables/useFormat'
import type { Seance, TypeSeance } from '@/api/types'

/**
 * Une séance passée que rien n'est venu renseigner, posée sur une seule ligne.
 *
 * <p>Ce bloc pose une question fermée — l'as-tu faite ? — et une question fermée n'a pas
 * besoin d'une carte entière : le jour, la séance, deux boutons. Le reste se lit dans la
 * fiche comme n'importe quelle autre séance.
 */
defineProps<{ seance: Seance }>()
const emit = defineEmits<{ statut: [statut: 'REALISEE' | 'NON_REALISEE']; ouvrir: [] }>()

const JOURS = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.']

const COURTS: Record<TypeSeance, string> = {
  EF: 'Endurance', SL: 'Sortie longue', SEUIL: 'Seuil', VMA: 'VMA', AM: 'Allure marathon',
  COTES: 'Côtes', RENFO: 'Renfo', COURSE: 'Course', CROSS: 'Cross', REPOS: 'Repos',
}

function jour(iso: string): string {
  const d = new Date(iso)
  return `${JOURS[d.getDay()]} ${d.getDate()}`
}
</script>

<template>
  <div class="flex items-center gap-2 py-2">
    <button
      class="flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-left"
      @click="emit('ouvrir')"
    >
      <span class="tabulaire w-14 shrink-0 text-xs text-[var(--color-doux)]">
        {{ jour(seance.date) }}
      </span>
      <IconeSeance :type="seance.type" class="size-3.5 shrink-0 text-[var(--color-doux)]" />
      <span class="truncate text-sm">{{ COURTS[seance.type] }}</span>
      <span v-if="seance.distanceCibleKm" class="tabulaire shrink-0 text-sm text-[var(--color-doux)]">
        {{ km(seance.distanceCibleKm, 0) }}
      </span>
    </button>

    <button
      class="shrink-0 cursor-pointer rounded-md border border-[var(--color-succes)] px-2.5 py-1 text-sm text-[var(--color-succes)]"
      title="Faite"
      @click="emit('statut', 'REALISEE')"
    >
      ✓
    </button>
    <button
      class="shrink-0 cursor-pointer rounded-md border border-[var(--color-bordure)] px-2.5 py-1 text-sm text-[var(--color-doux)]"
      title="Pas faite"
      @click="emit('statut', 'NON_REALISEE')"
    >
      ✕
    </button>
  </div>
</template>
