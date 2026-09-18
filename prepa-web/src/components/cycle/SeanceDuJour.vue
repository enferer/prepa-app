<script setup lang="ts">
import { computed } from 'vue'
import GrapheStructure from '@/components/cycle/GrapheStructure.vue'
import IconeSeance from '@/components/ui/IconeSeance.vue'
import NoteRepliable from '@/components/ui/NoteRepliable.vue'
import { allure, dureeCourte, km } from '@/composables/useFormat'
import type { Seance, TypeSeance } from '@/api/types'

/**
 * Ce qu'il y a à faire aujourd'hui — la première chose qu'on vient chercher.
 *
 * <p>Elle se lit sans être lue : la nature de la séance, son déroulé dessiné, et trois
 * nombres portant leur unité. Aucune étiquette ne les précède — « 14 km » n'a pas besoin
 * qu'on écrive « distance » au-dessus, et la place ainsi gagnée revient aux chiffres.
 *
 * <p>Tout ce qui explique plutôt qu'il ne dirige — consigne écrite, allures détaillées,
 * focus — vit dans la fiche, à un clic sur la carte.
 */
const props = defineProps<{ seance: Seance; lectureSeule?: boolean }>()
const emit = defineEmits<{
  ouvrir: []
  ouvrirActivite: []
  statut: [statut: 'REALISEE' | 'NON_REALISEE']
}>()

/**
 * Regarder l'entrainement d'un autre ne donne pas le droit de le renseigner : les gestes de
 * confirmation disparaissent plutot que d'echouer en 403 une fois cliques.
 */
const aTrancher = computed(
  () => !props.lectureSeule && props.seance.statut === 'A_VENIR' && props.seance.type !== 'REPOS',
)

const faite = computed(
  () => props.seance.statut === 'REALISEE' || props.seance.statut === 'ANALYSEE',
)

const NOMS: Record<TypeSeance, string> = {
  EF: 'Endurance', SL: 'Sortie longue', SEUIL: 'Seuil', VMA: 'VMA', AM: 'Allure marathon',
  COTES: 'Côtes', RENFO: 'Renforcement', COURSE: 'Course', CROSS: 'Cross', REPOS: 'Repos',
}

/** La teinte de l'intensité, la même que dans le déroulé et le semainier. */
const TEINTES: Partial<Record<TypeSeance, string>> = {
  SEUIL: 'var(--color-effort-fort)',
  VMA: 'var(--color-effort-fort)',
  COTES: 'var(--color-effort-fort)',
  AM: 'var(--color-effort-fort)',
  COURSE: 'var(--color-effort-fort)',
  EF: 'var(--color-effort-endurance)',
  SL: 'var(--color-effort-endurance)',
}

const teinte = computed(() => TEINTES[props.seance.type] ?? 'var(--color-effort-facile)')

/** Le temps que la séance devrait durer, d'après son propre déroulé. */
const dureePrevue = computed(() => {
  if (props.seance.dureeCibleMin) return `${props.seance.dureeCibleMin} min`
  const total = props.seance.structure.reduce((somme, bloc) => somme + bloc.dureeEstimeeSec, 0)
  return total ? dureeCourte(total) : null
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

/** Les trois nombres, sans étiquette : l'unité dit déjà ce qu'ils sont. */
const chiffres = computed(() =>
  [
    props.seance.distanceCibleKm ? km(props.seance.distanceCibleKm) : null,
    dureePrevue.value,
    allureCle.value ? `${allureCle.value}/km` : null,
  ].filter((v): v is string => !!v),
)
</script>

<template>
  <section
    class="overflow-hidden rounded-2xl border border-[var(--color-bordure)] bg-[var(--color-surface)] shadow-sm"
  >
    <!-- Un filet de la couleur de l'intensité : la nature de la séance se voit avant de se lire. -->
    <div class="h-1" :style="{ backgroundColor: teinte }" />

    <button class="block w-full cursor-pointer p-5 text-left" @click="emit('ouvrir')">
      <div class="flex items-center gap-2">
        <span class="grid size-6 place-items-center rounded-md" :style="{ color: teinte }">
          <IconeSeance :type="seance.type" class="size-4" />
        </span>
        <span class="text-sm font-semibold tracking-wide uppercase" :style="{ color: teinte }">
          {{ NOMS[seance.type] }}
        </span>
        <span
          v-if="faite"
          class="ml-auto rounded-full bg-[var(--color-succes-fond)] px-2 py-0.5 text-xs font-medium text-[var(--color-succes)]"
        >
          ✓
        </span>
      </div>

      <h2 class="mt-1 text-2xl leading-tight font-semibold">{{ seance.titre }}</h2>

      <!-- Le déroulé, dessiné. C'est lui qui dit ce qu'on va faire, avant tout chiffre. -->
      <GrapheStructure v-if="seance.structure.length" :blocs="seance.structure" class="mt-4" />

      <p v-else-if="seance.description" class="mt-3 text-sm text-[var(--color-doux)]">
        {{ seance.description }}
      </p>

      <p v-if="chiffres.length" class="tabulaire mt-4 flex flex-wrap items-baseline gap-x-5 gap-y-1">
        <span v-for="chiffre in chiffres" :key="chiffre" class="text-2xl font-semibold">
          {{ chiffre }}
        </span>
      </p>
    </button>

    <!--
      La consigne du coach est écrite pour ce jour-là : elle reste sur la carte. Elle vit hors
      du bouton, parce qu'elle porte le sien — « lire la suite » — et qu'un bouton ne s'imbrique
      pas dans un autre.
    -->
    <div v-if="seance.commentaireCoach" class="-mt-1 px-5 pb-5">
      <NoteRepliable :texte="seance.commentaireCoach" />
    </div>

    <!--
      Quand la sortie a été courue, c'est elle qu'on vient voir, pas la consigne qui l'a
      précédée. Le lien y mène donc directement.
    -->
    <div v-if="seance.activityId" class="border-t border-[var(--color-bordure)] px-5 py-3">
      <button
        class="cursor-pointer text-sm font-medium text-[var(--color-accent)]"
        @click="emit('ouvrirActivite')"
      >
        Ma sortie →
      </button>
    </div>

    <!-- Trancher reste hors du bouton : cliquer « fait » ne doit pas ouvrir la fiche. -->
    <div v-if="aTrancher" class="flex gap-2 border-t border-[var(--color-bordure)] px-5 py-3">
      <button
        class="flex-1 cursor-pointer rounded-lg bg-[var(--color-succes)] py-2 text-sm font-medium text-white"
        @click="emit('statut', 'REALISEE')"
      >
        Fait
      </button>
      <button
        class="flex-1 cursor-pointer rounded-lg border border-[var(--color-bordure)] py-2 text-sm text-[var(--color-doux)]"
        @click="emit('statut', 'NON_REALISEE')"
      >
        Pas fait
      </button>
    </div>
  </section>
</template>
