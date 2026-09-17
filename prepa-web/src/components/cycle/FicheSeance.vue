<script setup lang="ts">
import { computed } from 'vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import PastilleStatut from '@/components/ui/PastilleStatut.vue'
import { dateLongue, joursDepuis, km } from '@/composables/useFormat'
import type { Seance } from '@/api/types'

const props = defineProps<{ seance: Seance | null }>()
const emit = defineEmits<{
  fermer: []
  statut: [statut: 'REALISEE' | 'NON_REALISEE']
  ouvrirActivite: []
}>()

/**
 * Ce qu'il y a à faire, ou ce qui a été fait.
 *
 * <p>Une séance à venir n'était consultable nulle part : on voyait son titre dans le
 * semainier sans pouvoir lire la consigne qui va avec. Or c'est précisément ce qu'on vient
 * chercher la veille au soir.
 */
const quand = computed(() => {
  if (!props.seance) return ''
  const jours = joursDepuis(props.seance.date)
  if (jours === 0) return "aujourd'hui"
  if (jours === 1) return 'demain'
  if (jours === -1) return 'hier'
  if (jours > 1) return `dans ${jours} jours`
  return `il y a ${Math.abs(jours)} jours`
})

const passee = computed(() => (props.seance ? joursDepuis(props.seance.date) < 0 : false))

const aConfirmer = computed(
  () => passee.value && props.seance?.statut === 'A_VENIR',
)
</script>

<template>
  <!-- Panneau modal : on revient toujours au semainier derrière. -->
  <Teleport to="body">
    <div
      v-if="seance"
      class="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4"
      @click.self="emit('fermer')"
    >
      <article
        class="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-t-2xl bg-[var(--color-surface)] p-5 sm:rounded-2xl"
      >
        <header class="flex flex-wrap items-center gap-2">
          <EtiquetteType :type="seance.type" />
          <PastilleStatut :statut="seance.statut" />
          <button
            class="ml-auto rounded-md px-2 py-1 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)]"
            @click="emit('fermer')"
          >
            ✕
          </button>
        </header>

        <p class="mt-3 text-sm text-[var(--color-doux)]">
          {{ dateLongue(seance.date) }} · {{ quand }}
        </p>
        <h2 class="mt-0.5 text-lg font-semibold">{{ seance.titre }}</h2>

        <p v-if="seance.description" class="mt-3 text-sm whitespace-pre-line">
          {{ seance.description }}
        </p>

        <dl class="mt-4 grid grid-cols-2 gap-3">
          <div v-if="seance.distanceCibleKm" class="rounded-lg bg-[var(--color-appui)] px-3 py-2">
            <dt class="text-xs text-[var(--color-doux)]">Distance</dt>
            <dd class="tabulaire font-semibold">{{ km(seance.distanceCibleKm) }}</dd>
          </div>
          <div v-if="seance.dureeCibleMin" class="rounded-lg bg-[var(--color-appui)] px-3 py-2">
            <dt class="text-xs text-[var(--color-doux)]">Durée</dt>
            <dd class="tabulaire font-semibold">{{ seance.dureeCibleMin }} min</dd>
          </div>
          <div v-if="seance.alluresTexte" class="col-span-2 rounded-lg bg-[var(--color-appui)] px-3 py-2">
            <dt class="text-xs text-[var(--color-doux)]">Allures à tenir</dt>
            <dd class="font-semibold">{{ seance.alluresTexte }}</dd>
          </div>
          <div v-if="seance.focus" class="col-span-2 rounded-lg bg-[var(--color-appui)] px-3 py-2">
            <dt class="text-xs text-[var(--color-doux)]">Focus</dt>
            <dd class="font-medium">{{ seance.focus }}</dd>
          </div>
        </dl>

        <p
          v-if="seance.commentaireCoach"
          class="mt-4 rounded-lg bg-[var(--color-accent-fond)] px-3 py-2 text-sm"
        >
          <span class="font-medium text-[var(--color-accent)]">Ton coach — </span>{{ seance.commentaireCoach }}
        </p>

        <p v-if="seance.commentaireAthlete" class="mt-2 text-sm italic text-[var(--color-doux)]">
          « {{ seance.commentaireAthlete }} »
        </p>

        <div class="mt-5 flex flex-wrap gap-2">
          <button
            v-if="seance.activityId"
            class="rounded-lg bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white"
            @click="emit('ouvrirActivite')"
          >
            Voir ce que tu as fait
          </button>

          <template v-if="aConfirmer">
            <button
              class="rounded-lg bg-[var(--color-succes)] px-4 py-2 text-sm font-medium text-white"
              @click="emit('statut', 'REALISEE')"
            >
              Je l'ai faite
            </button>
            <button
              class="rounded-lg border border-[var(--color-bordure)] px-4 py-2 text-sm"
              @click="emit('statut', 'NON_REALISEE')"
            >
              Pas faite
            </button>
          </template>
        </div>
      </article>
    </div>
  </Teleport>
</template>
