<script setup lang="ts">
import { computed } from 'vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import PastilleStatut from '@/components/ui/PastilleStatut.vue'
import { dateLongue, km, joursDepuis } from '@/composables/useFormat'
import type { Seance } from '@/api/types'

const props = defineProps<{ seance: Seance; miseEnAvant?: boolean }>()
const emit = defineEmits<{ statut: [statut: 'REALISEE' | 'NON_REALISEE']; ouvrir: [] }>()

const passee = computed(() => joursDepuis(props.seance.date) < 0)

/**
 * Les séances rapprochées automatiquement n'ont pas besoin d'être confirmées : seules
 * celles que rien n'est venu renseigner appellent une réponse de l'athlète — typiquement
 * une sortie faite sans montre.
 */
const aTrancher = computed(() => passee.value && props.seance.statut === 'A_VENIR')
</script>

<template>
  <article
    class="rounded-xl border bg-[var(--color-surface)] p-4"
    :class="miseEnAvant
      ? 'border-[var(--color-accent)] ring-1 ring-[var(--color-accent-fond)]'
      : 'border-[var(--color-bordure)]'"
  >
    <div class="flex flex-wrap items-center gap-2">
      <EtiquetteType :type="seance.type" />
      <PastilleStatut :statut="seance.statut" />
      <span class="ml-auto text-xs text-[var(--color-doux)]">{{ dateLongue(seance.date) }}</span>
    </div>

    <h3 class="mt-2 font-medium">{{ seance.titre }}</h3>

    <p v-if="seance.description" class="mt-1 text-sm text-[var(--color-doux)]">
      {{ seance.description }}
    </p>

    <dl class="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm">
      <div v-if="seance.distanceCibleKm" class="flex gap-1.5">
        <dt class="text-[var(--color-doux)]">Distance</dt>
        <dd class="tabulaire font-medium">{{ km(seance.distanceCibleKm) }}</dd>
      </div>
      <div v-if="seance.dureeCibleMin" class="flex gap-1.5">
        <dt class="text-[var(--color-doux)]">Durée</dt>
        <dd class="tabulaire font-medium">{{ seance.dureeCibleMin }} min</dd>
      </div>
      <div v-if="seance.alluresTexte" class="flex gap-1.5">
        <dt class="text-[var(--color-doux)]">Allures</dt>
        <dd class="font-medium">{{ seance.alluresTexte }}</dd>
      </div>
      <div v-if="seance.focus" class="flex gap-1.5">
        <dt class="text-[var(--color-doux)]">Focus</dt>
        <dd class="font-medium">{{ seance.focus }}</dd>
      </div>
    </dl>

    <p
      v-if="seance.commentaireCoach"
      class="mt-3 rounded-lg bg-[var(--color-accent-fond)] px-3 py-2 text-sm text-[var(--color-texte)]"
    >
      <span class="font-medium text-[var(--color-accent)]">Ton coach — </span>{{ seance.commentaireCoach }}
    </p>

    <p v-if="seance.commentaireAthlete" class="mt-2 text-sm italic text-[var(--color-doux)]">
      « {{ seance.commentaireAthlete }} »
    </p>

    <!-- Trancher une seance echue est la seule action offerte ici : le contenu du plan, lui,
         reste du ressort du coach. -->
    <div v-if="aTrancher" class="mt-4 flex gap-2">
      <button
        class="rounded-lg bg-[var(--color-succes)] px-3 py-1.5 text-sm font-medium text-white"
        @click="emit('statut', 'REALISEE')"
      >
        Je l'ai faite
      </button>
      <button
        class="rounded-lg border border-[var(--color-bordure)] px-3 py-1.5 text-sm"
        @click="emit('statut', 'NON_REALISEE')"
      >
        Pas faite
      </button>
    </div>

    <button
      v-if="seance.activityId"
      class="mt-3 text-sm text-[var(--color-accent)] hover:underline"
      @click="emit('ouvrir')"
    >
      Voir la séance réalisée →
    </button>
  </article>
</template>
