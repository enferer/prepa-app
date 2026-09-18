<script setup lang="ts">
import { computed } from 'vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import GrapheStructure from '@/components/cycle/GrapheStructure.vue'
import { allure, dureeCourte, km } from '@/composables/useFormat'
import type { Seance } from '@/api/types'

/**
 * Ce qu'il y a à faire aujourd'hui — la première chose qu'on vient chercher.
 *
 * <p>C'est la seule carte de l'écran qui répond à la question posée en ouvrant l'application.
 * Elle passe donc devant les compteurs d'avancement : savoir qu'on a tenu 87 % de ses séances
 * n'aide personne à sortir courir ce soir.
 *
 * <p>La carte entière est cliquable. Auparavant elle ne l'était pas, et l'athlète passait par
 * le semainier pour ouvrir la fiche d'une séance qu'il avait pourtant sous les yeux.
 */
const props = defineProps<{ seance: Seance }>()
const emit = defineEmits<{ ouvrir: []; statut: [statut: 'REALISEE' | 'NON_REALISEE'] }>()

const faite = computed(
  () => props.seance.statut === 'REALISEE' || props.seance.statut === 'ANALYSEE',
)

/** Le temps que la séance devrait durer, d'après son propre déroulé. */
const dureePrevue = computed(() => {
  if (props.seance.dureeCibleMin) return `${props.seance.dureeCibleMin} min`
  const total = props.seance.structure.reduce((somme, bloc) => somme + bloc.dureeEstimeeSec, 0)
  return total ? `~${dureeCourte(total)}` : null
})

/** L'allure qui compte : la plus rapide du déroulé, celle qui fait la séance. */
const allureCle = computed(() => {
  const efforts = props.seance.structure
    .filter((bloc) => bloc.role === 'EFFORT' && bloc.allureSecKm)
    .map((bloc) => bloc.allureSecKm!)
  if (efforts.length) return allure(Math.min(...efforts))
  const toutes = props.seance.structure.map((b) => b.allureSecKm).filter((a): a is number => !!a)
  return toutes.length ? allure(Math.max(...toutes)) : null
})
</script>

<template>
  <section
    class="w-full rounded-xl border-2 border-[var(--color-accent)] bg-[var(--color-surface)] text-left shadow-sm"
  >
    <button class="block w-full cursor-pointer p-4 text-left sm:p-5" @click="emit('ouvrir')">
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-xs font-semibold tracking-wide uppercase text-[var(--color-accent)]">
          {{ seance.type === 'REPOS' ? 'Aujourd’hui — repos' : 'Ta séance du jour' }}
        </span>
        <EtiquetteType :type="seance.type" />
        <span
          v-if="faite"
          class="ml-auto rounded-full bg-[var(--color-succes-fond)] px-2 py-0.5 text-xs font-medium text-[var(--color-succes)]"
        >
          ✓ faite
        </span>
      </div>

      <h2 class="mt-2 text-xl leading-tight font-semibold">{{ seance.titre }}</h2>

      <!-- Le déroulé, dessiné. C'est lui qui dit ce qu'on va faire, avant tout chiffre. -->
      <GrapheStructure v-if="seance.structure.length" :blocs="seance.structure" class="mt-4" />

      <p v-else-if="seance.description" class="mt-2 text-sm text-[var(--color-doux)]">
        {{ seance.description }}
      </p>

      <dl class="mt-4 flex flex-wrap gap-x-6 gap-y-2">
        <div v-if="seance.distanceCibleKm">
          <dt class="text-xs text-[var(--color-doux)]">Distance</dt>
          <dd class="tabulaire text-lg font-semibold">{{ km(seance.distanceCibleKm) }}</dd>
        </div>
        <div v-if="dureePrevue">
          <dt class="text-xs text-[var(--color-doux)]">Durée</dt>
          <dd class="tabulaire text-lg font-semibold">{{ dureePrevue }}</dd>
        </div>
        <div v-if="allureCle">
          <dt class="text-xs text-[var(--color-doux)]">Allure clé</dt>
          <dd class="tabulaire text-lg font-semibold">{{ allureCle }}/km</dd>
        </div>
        <div v-if="seance.focus" class="min-w-0">
          <dt class="text-xs text-[var(--color-doux)]">Focus</dt>
          <dd class="truncate font-medium">{{ seance.focus }}</dd>
        </div>
      </dl>

      <p
        v-if="seance.commentaireCoach"
        class="mt-4 rounded-lg bg-[var(--color-accent-fond)] px-3 py-2 text-sm"
      >
        <span class="font-medium text-[var(--color-accent)]">Ton coach — </span
        >{{ seance.commentaireCoach }}
      </p>

      <p class="mt-4 text-sm font-medium text-[var(--color-accent)]">
        {{ seance.activityId ? 'Voir ce que tu as couru' : 'Voir le détail' }} →
      </p>
    </button>

    <!-- Trancher reste hors du bouton : cliquer « je l'ai faite » ne doit pas ouvrir la fiche. -->
    <div
      v-if="seance.statut === 'A_VENIR' && seance.type !== 'REPOS'"
      class="flex gap-2 border-t border-[var(--color-bordure)] px-4 py-3 sm:px-5"
    >
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
  </section>
</template>
