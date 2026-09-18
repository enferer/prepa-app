<script setup lang="ts">
import { computed } from 'vue'
import IconeSeance from '@/components/ui/IconeSeance.vue'
import { km } from '@/composables/useFormat'
import type { Seance, Semaine, TypeSeance } from '@/api/types'

const props = defineProps<{ semaine: Semaine }>()
const emit = defineEmits<{ ouvrir: [seance: Seance] }>()

const JOURS = ['Lun.', 'Mar.', 'Mer.', 'Jeu.', 'Ven.', 'Sam.', 'Dim.']

const aujourdhui = new Date().toISOString().slice(0, 10)

/**
 * Les sept jours, chacun avec ses séances.
 *
 * <p>Une semaine se lit de haut en bas, un jour par ligne : la date à gauche, ce qu'il y a à
 * faire à droite. Sept colonnes larges de cent pixels obligeaient à couper les titres —
 * « Allure mara… » — là où c'est précisément le titre qui dit la séance.
 *
 * <p>Les jours de repos gardent leur ligne, en pointillé : c'est leur place entre les
 * séances qui dit si la charge est tenable.
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

/**
 * La couleur du pictogramme, sur la même rampe que le déroulé de la fiche.
 *
 * <p>C'est ce qui permet de voir la semaine au lieu de la lire : deux signes orange à un
 * jour d'écart disent « deux séances dures d'affilée » sans qu'on déchiffre les intitulés.
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

const COURTS: Record<TypeSeance, string> = {
  EF: 'EF', SL: 'SL', SEUIL: 'Seuil', VMA: 'VMA', AM: 'AM',
  COTES: 'Côtes', RENFO: 'Renfo', COURSE: 'Course', CROSS: 'Cross', REPOS: 'Repos',
}

/**
 * La ligne sous le titre : ce que la séance demande, en une ligne.
 *
 * <p>Le déroulé dessiné vivait ici ; il est retourné dans la fiche. Une bande de cent pixels
 * ne montrait pas grand-chose, et ce qu'on cherche dans un semainier est d'abord la distance
 * et l'allure à tenir.
 */
function details(seance: Seance): string {
  return [
    COURTS[seance.type],
    seance.distanceCibleKm ? km(seance.distanceCibleKm) : null,
    seance.dureeCibleMin && !seance.distanceCibleKm ? `${seance.dureeCibleMin} min` : null,
    seance.alluresTexte,
  ]
    .filter(Boolean)
    .join(' · ')
}

/**
 * Le constat, réduit à un signe.
 *
 * <p>Une pastille « À venir » sur chaque ligne n'apprend rien — c'est l'état par défaut.
 * Seul ce qui s'écarte du plan mérite un signe.
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
  <ol class="space-y-2">
    <li
      v-for="jour in jours"
      :key="jour.iso"
      class="flex overflow-hidden rounded-xl border"
      :class="[
        jour.aujourdhui
          ? 'border-[var(--color-accent)] bg-[var(--color-accent-fond)]'
          : jour.seances.length
            ? 'border-[var(--color-bordure)] bg-[var(--color-surface)]'
            : 'border-dashed border-[var(--color-bordure)]',
        !jour.seances.length && !jour.aujourdhui && jour.passe ? 'opacity-50' : '',
      ]"
    >
      <!-- Le jour courant porte un trait plein : on retrouve sa ligne sans la chercher. -->
      <span class="w-1 shrink-0" :class="jour.aujourdhui ? 'bg-[var(--color-accent)]' : ''" />

      <div class="flex min-w-0 flex-1 gap-3 p-3">
        <div class="w-11 shrink-0 text-center">
          <p
            class="text-xs font-semibold uppercase"
            :class="jour.aujourdhui ? 'text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
          >
            {{ jour.nom }}
          </p>
          <p
            class="tabulaire text-xl leading-tight font-semibold"
            :class="jour.aujourdhui ? 'text-[var(--color-accent)]' : ''"
          >
            {{ jour.numero }}
          </p>
        </div>

        <p
          v-if="!jour.seances.length"
          class="self-center text-sm text-[var(--color-doux)] italic"
        >
          Repos
        </p>

        <!--
          Une séance tient en deux lignes : son titre, et ce qu'elle demande. Le déroulé
          dessiné, les consignes et le commentaire du coach vivent dans la fiche, à un clic.
        -->
        <ul v-else class="min-w-0 flex-1 divide-y divide-[var(--color-bordure)]">
          <li v-for="seance in jour.seances" :key="seance.id">
            <button
              class="flex w-full cursor-pointer items-start gap-2 py-1.5 text-left"
              @click="emit('ouvrir', seance)"
            >
              <IconeSeance
                :type="seance.type"
                class="mt-0.5 size-4 shrink-0"
                :style="{ color: teinte(seance.type) }"
              />
              <span class="min-w-0 flex-1">
                <span class="block leading-snug font-medium">{{ seance.titre }}</span>
                <span class="block truncate text-sm text-[var(--color-doux)]">
                  {{ details(seance) }}
                </span>
              </span>
              <span
                v-if="marque(seance)"
                class="shrink-0 text-sm"
                :class="marque(seance)!.classe"
                :title="marque(seance)!.titre"
              >
                {{ marque(seance)!.signe }}
              </span>
            </button>
          </li>
        </ul>
      </div>
    </li>
  </ol>
</template>
