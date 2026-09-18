<script setup lang="ts">
import { computed, ref } from 'vue'
import { allure, distance } from '@/composables/useFormat'
import type { ActivityResume, TypeActivite } from '@/api/types'

/**
 * Choisir quelle sortie regarder.
 *
 * <p>Une liste déroulante de deux cents lignes est illisible : elle ne montre qu'un élément à
 * la fois, ne se cherche pas, et oblige à connaître la date de ce qu'on veut voir. Or on
 * cherche rarement une date — on cherche « ma dernière sortie longue », « le fractionné de la
 * semaine dernière ».
 *
 * <p>D'où une liste vraie : filtrable par nature de sortie, cherchable au titre comme à la
 * date, groupée par mois, et qui montre assez de chaque sortie (distance, allure) pour la
 * reconnaître sans l'ouvrir.
 */
const props = withDefaults(
  defineProps<{
    activites: ActivityResume[]
    selection?: string
    /**
     * Hauteur maximale de la liste défilante.
     *
     * <p>Elle est posée ici plutôt que par le conteneur : une hauteur héritée d'un parent qui
     * n'en a pas lui-même ne contraint rien, et le défilement interne ne s'enclenche jamais —
     * la liste déroule alors la page entière sous le détail de la séance.
     */
    hauteur?: string
  }>(),
  { hauteur: '70vh' },
)
const emit = defineEmits<{ choisir: [id: string] }>()

const MOIS = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
]

/**
 * Les familles de sortie, telles qu'un coureur les distingue.
 *
 * <p>Huit types techniques se réduisent à quatre choix utiles : ce qui se court dehors, ce qui
 * se court en montagne, ce qui se court sur place, et le reste.
 */
const FAMILLES: { cle: string; nom: string; types: TypeActivite[] }[] = [
  { cle: 'tout', nom: 'Tout', types: [] },
  { cle: 'route', nom: 'Route', types: ['RUN'] },
  { cle: 'trail', nom: 'Trail', types: ['TRAIL', 'HIKE'] },
  { cle: 'tapis', nom: 'Tapis', types: ['TREADMILL'] },
  { cle: 'autre', nom: 'Autre', types: ['BIKE', 'SWIM', 'STRENGTH', 'OTHER'] },
]

const famille = ref('tout')
const recherche = ref('')

/** Les familles qui ont au moins une sortie : proposer un filtre vide serait une fausse piste. */
const famillesUtiles = computed(() =>
  FAMILLES.filter(
    (f) => f.cle === 'tout' || props.activites.some((a) => f.types.includes(a.type)),
  ),
)

const filtrees = computed(() => {
  const choisie = FAMILLES.find((f) => f.cle === famille.value)
  const terme = recherche.value.trim().toLowerCase()
  return props.activites.filter((a) => {
    if (choisie?.types.length && !choisie.types.includes(a.type)) return false
    if (!terme) return true
    return `${a.titre ?? ''} ${a.date}`.toLowerCase().includes(terme)
  })
})

/** Groupées par mois : c'est le repère naturel quand on remonte le temps. */
const parMois = computed(() => {
  const groupes: { cle: string; nom: string; activites: ActivityResume[] }[] = []
  for (const activite of filtrees.value) {
    const jour = new Date(activite.date)
    const cle = `${jour.getFullYear()}-${jour.getMonth()}`
    const dernier = groupes[groupes.length - 1]
    if (dernier?.cle === cle) {
      dernier.activites.push(activite)
    } else {
      groupes.push({
        cle,
        nom: `${MOIS[jour.getMonth()]} ${jour.getFullYear()}`,
        activites: [activite],
      })
    }
  }
  return groupes
})

function jourDe(iso: string): string {
  return String(new Date(iso).getDate()).padStart(2, '0')
}
</script>

<template>
  <div>
    <div class="space-y-2 pb-3">
      <input
        v-model="recherche"
        type="search"
        placeholder="Chercher un titre, une date…"
        class="w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-surface)] px-3 py-2 text-sm"
      />
      <div class="flex flex-wrap gap-1">
        <button
          v-for="choix in famillesUtiles"
          :key="choix.cle"
          class="cursor-pointer rounded-full px-2.5 py-1 text-xs"
          :class="famille === choix.cle
            ? 'bg-[var(--color-accent-fond)] font-medium text-[var(--color-accent)]'
            : 'bg-[var(--color-appui)] text-[var(--color-doux)]'"
          @click="famille = choix.cle"
        >
          {{ choix.nom }}
        </button>
      </div>
    </div>

    <p v-if="!filtrees.length" class="py-4 text-sm text-[var(--color-doux)]">
      Aucune sortie ne correspond.
    </p>

    <ol v-else class="overflow-y-auto overscroll-contain pr-1" :style="{ maxHeight: hauteur }">
      <li v-for="mois in parMois" :key="mois.cle">
        <h3
          class="sticky top-0 bg-[var(--color-fond)] py-1 text-xs font-medium tracking-wide uppercase text-[var(--color-doux)]"
        >
          {{ mois.nom }}
        </h3>
        <ul>
          <li v-for="activite in mois.activites" :key="activite.id">
            <button
              class="mb-0.5 flex w-full cursor-pointer items-baseline gap-2 rounded-lg px-2 py-1.5 text-left"
              :class="activite.id === selection
                ? 'bg-[var(--color-accent-fond)]'
                : 'hover:bg-[var(--color-appui)]'"
              @click="emit('choisir', activite.id)"
            >
              <span
                class="tabulaire w-6 shrink-0 text-sm font-semibold"
                :class="activite.id === selection ? 'text-[var(--color-accent)]' : ''"
              >
                {{ jourDe(activite.date) }}
              </span>
              <span class="min-w-0 flex-1">
                <span class="tabulaire block text-sm font-medium">
                  {{ distance(activite.distanceM) }}
                  <span v-if="activite.allureMoySecKm" class="font-normal text-[var(--color-doux)]">
                    · {{ allure(activite.allureMoySecKm) }}/km
                  </span>
                </span>
                <span class="block truncate text-xs text-[var(--color-doux)]">
                  {{ activite.titre || '—' }}
                </span>
              </span>
            </button>
          </li>
        </ul>
      </li>
    </ol>
  </div>
</template>
