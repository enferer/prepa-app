<script setup lang="ts">
defineProps<{
  libelle: string
  valeur: string
  detail?: string
  /** Teinte d'accompagnement : neutre par defaut, pour ne pas crier sans raison. */
  ton?: 'neutre' | 'succes' | 'alerte' | 'manque'
}>()

const couleurs = {
  neutre: 'text-[var(--color-texte)]',
  succes: 'text-[var(--color-succes)]',
  alerte: 'text-[var(--color-alerte)]',
  manque: 'text-[var(--color-manque)]',
}
</script>

<template>
  <div class="rounded-lg border border-[var(--color-bordure)] bg-[var(--color-surface)] px-3 py-2.5">
    <p class="text-xs text-[var(--color-doux)]">{{ libelle }}</p>
    <p class="tabulaire mt-1 text-xl font-semibold" :class="couleurs[ton ?? 'neutre']">{{ valeur }}</p>
    <p v-if="detail" class="mt-0.5 text-xs text-[var(--color-doux)]">{{ detail }}</p>
  </div>
</template>
