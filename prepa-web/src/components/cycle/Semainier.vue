<script setup lang="ts">
import { computed } from 'vue'
import GrapheStructure from '@/components/cycle/GrapheStructure.vue'
import { km } from '@/composables/useFormat'
import type { Seance, Semaine, TypeSeance } from '@/api/types'

const props = defineProps<{ semaine: Semaine; volumeRealise: number }>()
const emit = defineEmits<{ ouvrir: [seance: Seance] }>()

const JOURS = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']

const aujourdhui = new Date().toISOString().slice(0, 10)

/**
 * Les sept jours, chacun avec ses séances.
 *
 * <p>Une semaine d'entraînement se lit comme un calendrier, pas comme une liste : c'est la
 * place des jours de repos entre les séances qui dit si la charge est tenable. On affiche
 * donc les sept jours, même vides.
 */
const jours = computed(() =>
  Array.from({ length: 7 }, (_, decalage) => {
    const jour = new Date(props.semaine.dateDebut)
    jour.setDate(jour.getDate() + decalage)
    const iso = jour.toISOString().slice(0, 10)
    return {
      iso,
      nom: JOURS[decalage],
      numero: jour.getDate(),
      aujourdhui: iso === aujourdhui,
      passe: iso < aujourdhui,
      seances: props.semaine.seances
        .filter((s) => s.date === iso)
        .sort((a, b) => a.ordre - b.ordre),
    }
  }),
)

const partDeLaCible = computed(() =>
  props.semaine.volumeCibleKm ? (props.volumeRealise / props.semaine.volumeCibleKm) * 100 : null,
)

/**
 * La couleur du liseré d'une séance, sur la même rampe que le déroulé.
 *
 * <p>C'est ce qui permet de voir la semaine au lieu de la lire : deux traits orange collés
 * disent « deux séances dures d'affilée » sans qu'on ait à déchiffrer les intitulés.
 */
const TEINTES: Partial<Record<TypeSeance, string>> = {
  SEUIL: 'var(--color-effort-fort)',
  VMA: 'var(--color-effort-fort)',
  COTES: 'var(--color-effort-fort)',
  AM: 'var(--color-effort-fort)',
  COURSE: 'var(--color-effort-fort)',
  EF: 'var(--color-effort-endurance)',
  SL: 'var(--color-effort-endurance)',
}

function teinte(type: TypeSeance): string {
  return TEINTES[type] ?? 'var(--color-effort-facile)'
}

/** Les libellés courts : dans une colonne large de cent pixels, « Allure marathon » ne tient pas. */
const COURTS: Record<TypeSeance, string> = {
  EF: 'Endurance', SL: 'Sortie longue', SEUIL: 'Seuil', VMA: 'VMA', AM: 'Allure marathon',
  COTES: 'Côtes', RENFO: 'Renfo', COURSE: 'Course', CROSS: 'Cross', REPOS: 'Repos',
}

/**
 * Le constat, réduit à un signe.
 *
 * <p>Une pastille « À venir » sur chacune des sept cases n'apprenait rien — c'est l'état par
 * défaut. Seul ce qui s'écarte du plan mérite un signe.
 */
function marque(seance: Seance): { signe: string; classe: string; titre: string } | null {
  switch (seance.statut) {
    case 'REALISEE':
      return { signe: '✓', classe: 'text-[var(--color-succes)]', titre: 'Faite' }
    case 'ANALYSEE':
      return { signe: '✓', classe: 'text-[var(--color-accent)]', titre: 'Faite et analysée' }
    case 'NON_REALISEE':
      return { signe: '✕', classe: 'text-[var(--color-manque)]', titre: 'Non faite' }
    case 'DEPLACEE':
      return { signe: '→', classe: 'text-[var(--color-alerte)]', titre: 'Décalée' }
    case 'ANNULEE':
      return { signe: '—', classe: 'text-[var(--color-doux)]', titre: 'Annulée' }
    default:
      return null
  }
}
</script>

<template>
  <div>
    <!-- Barre de progression du volume : où en est la semaine, d'un coup d'œil. -->
    <div class="mb-4">
      <div class="flex items-baseline justify-between text-sm">
        <span class="tabulaire">
          <span class="font-semibold">{{ km(volumeRealise) }}</span>
          <span class="text-[var(--color-doux)]"> sur {{ km(semaine.volumeCibleKm) }} visés</span>
        </span>
        <span v-if="partDeLaCible !== null" class="tabulaire text-sm text-[var(--color-doux)]">
          {{ Math.round(partDeLaCible) }} %
        </span>
      </div>
      <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-[var(--color-appui)]">
        <div
          class="h-full rounded-full transition-[width]"
          :class="partDeLaCible !== null && partDeLaCible >= 90
            ? 'bg-[var(--color-succes)]'
            : 'bg-[var(--color-accent)]'"
          :style="{ width: `${Math.min(100, partDeLaCible ?? 0)}%` }"
        />
      </div>
    </div>

    <!-- Sept colonnes sur écran large, sept lignes sur téléphone : dans les deux cas,
         un jour = un bloc, et les jours de repos restent visibles. -->
    <ol class="grid gap-2 sm:grid-cols-7">
      <li
        v-for="jour in jours"
        :key="jour.iso"
        class="rounded-lg border p-2 transition-colors"
        :class="[
          jour.aujourdhui
            ? 'border-[var(--color-accent)] bg-[var(--color-accent-fond)]'
            : 'border-[var(--color-bordure)]',
          jour.passe && !jour.aujourdhui ? 'opacity-70' : '',
        ]"
      >
        <div class="mb-1.5 flex items-baseline gap-1.5">
          <span
            class="text-xs font-medium uppercase"
            :class="jour.aujourdhui ? 'text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
          >
            {{ jour.nom }}
          </span>
          <span class="tabulaire text-xs text-[var(--color-doux)]">{{ jour.numero }}</span>
        </div>

        <p v-if="!jour.seances.length" class="py-1 text-xs text-[var(--color-doux)] opacity-60">
          repos
        </p>

        <!--
          Une séance tient en trois signes : un liseré coloré qui dit son intensité, son nom,
          et le dessin de son déroulé. Le reste — la consigne, les allures — vit dans la fiche,
          qui s'ouvre d'un clic.
        -->
        <button
          v-for="seance in jour.seances"
          :key="seance.id"
          class="mb-1 flex w-full cursor-pointer gap-2 rounded-md bg-[var(--color-surface)] p-1.5 text-left last:mb-0 hover:bg-[var(--color-appui)]"
          @click="emit('ouvrir', seance)"
        >
          <span
            class="w-1 shrink-0 rounded-full"
            :style="{ backgroundColor: teinte(seance.type) }"
            aria-hidden="true"
          />
          <span class="min-w-0 flex-1">
            <span class="flex items-baseline gap-1">
              <span class="min-w-0 flex-1 truncate text-xs font-medium">
                {{ COURTS[seance.type] }}
              </span>
              <span
                v-if="marque(seance)"
                class="shrink-0 text-xs"
                :class="marque(seance)!.classe"
                :title="marque(seance)!.titre"
              >
                {{ marque(seance)!.signe }}
              </span>
            </span>

            <GrapheStructure
              v-if="seance.structure.length"
              :blocs="seance.structure"
              variante="bandeau"
              class="mt-1.5"
            />

            <span
              v-if="seance.distanceCibleKm"
              class="tabulaire mt-1 block text-xs text-[var(--color-doux)]"
            >
              {{ km(seance.distanceCibleKm) }}
            </span>
          </span>
        </button>
      </li>
    </ol>
  </div>
</template>
