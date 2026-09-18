<script setup lang="ts">
import { computed } from 'vue'
import { dureeCourte } from '@/composables/useFormat'
import type { BlocPrevu, RoleBloc } from '@/api/types'

/**
 * Le déroulé d'une séance prévue, dessiné.
 *
 * <p>« 2 km éch. + 3×1 km à AM récup 2 min + 2 km RAC » est juste, mais il faut le lire deux
 * fois pour voir la séance. Sous forme de bande, elle se voit d'un coup : où est l'effort,
 * combien de fois il revient, ce qui l'encadre.
 *
 * <p>La largeur suit le temps, pas la distance — deux kilomètres d'échauffement pèsent plus
 * lourd dans une séance que deux kilomètres d'allure marathon, et c'est bien ce qu'on ressent.
 *
 * <p>Trois couleurs seulement, ordonnées du calme au dur. Six rôles pour trois teintes : des
 * lignes droites sont de l'intensité, une récupération et un échauffement sont du calme. Un
 * athlète ne retient pas six codes, il retient « bleu clair, bleu, orange ».
 */
const props = withDefaults(
  defineProps<{
    blocs: BlocPrevu[]
    /** `bandeau` pour le semainier : une bande fine, sans texte. */
    variante?: 'complet' | 'bandeau'
  }>(),
  { variante: 'complet' },
)

const TEINTES: Record<RoleBloc, string> = {
  ECHAUFFEMENT: 'var(--color-effort-facile)',
  RECUPERATION: 'var(--color-effort-facile)',
  RETOUR_AU_CALME: 'var(--color-effort-facile)',
  ENDURANCE: 'var(--color-effort-endurance)',
  LIGNES: 'var(--color-effort-fort)',
  EFFORT: 'var(--color-effort-fort)',
}

const NOMS: Record<RoleBloc, string> = {
  ECHAUFFEMENT: 'Échauffement',
  ENDURANCE: 'Endurance',
  EFFORT: 'Effort',
  LIGNES: 'Lignes droites',
  RECUPERATION: 'Récupération',
  RETOUR_AU_CALME: 'Retour au calme',
}

const total = computed(() =>
  props.blocs.reduce((somme, bloc) => somme + bloc.dureeEstimeeSec, 0),
)

/**
 * Largeur que la bande occupe, en gros.
 *
 * <p>Rien ici ne mesure le conteneur : on n'a besoin que d'un ordre de grandeur pour décider
 * si un bloc a la place de montrer ses répétitions. Se tromper d'un facteur deux ne change
 * rien à la décision ; ne pas trancher du tout donne six traits d'un pixel collés les uns aux
 * autres, qui ne disent plus rien.
 */
const LARGEUR_SUPPOSEE = { complet: 520, bandeau: 110 }

/** En dessous, un morceau et son écart ne se distinguent plus l'un de l'autre. */
const LARGEUR_MIN_MORCEAU = 4

/**
 * Chaque bloc, découpé en ses répétitions.
 *
 * <p>Une série de trois kilomètres se dessine en trois morceaux séparés par leurs
 * récupérations, pas en un bloc plein : c'est la respiration de la séance qui fait sa
 * difficulté, et l'effacer reviendrait à dessiner un tempo continu.
 */
const dessin = computed(() =>
  props.blocs.map((bloc) => {
    const part = total.value ? (bloc.dureeEstimeeSec / total.value) * 100 : 0
    const recup = bloc.recupSec ?? 0
    const parRepetition = (bloc.dureeEstimeeSec - recup * (bloc.repetitions - 1)) / bloc.repetitions

    // Un bloc trop étroit pour ses répétitions est dessiné plein. Six accélérations de vingt
    // secondes dans un footing d'une heure tiennent sur cinq pixels : les découper y ferait
    // une hachure illisible, là où un trait franc dit au moins qu'il s'est passé quelque chose.
    const largeur = (part / 100) * LARGEUR_SUPPOSEE[props.variante]
    const nbMorceaux = Math.max(1, 2 * bloc.repetitions - 1)
    const detaille = bloc.repetitions > 1 && largeur / nbMorceaux >= LARGEUR_MIN_MORCEAU

    const morceaux: { duree: number; recup: boolean }[] = []
    if (detaille) {
      for (let i = 0; i < bloc.repetitions; i++) {
        if (i > 0 && recup > 0) morceaux.push({ duree: recup, recup: true })
        morceaux.push({ duree: parRepetition, recup: false })
      }
    } else {
      morceaux.push({ duree: bloc.dureeEstimeeSec, recup: false })
    }

    return {
      bloc,
      teinte: TEINTES[bloc.role],
      nom: NOMS[bloc.role],
      part,
      morceaux,
      infobulle: `${NOMS[bloc.role]} — ${bloc.libelle} · ${dureeCourte(bloc.dureeEstimeeSec)}`,
    }
  }),
)

</script>

<template>
  <div v-if="blocs.length">
    <!--
      La bande. Un filet d'un pixel sépare les morceaux : sans lui, deux blocs de même teinte
      se recollent et la séance paraît continue là où elle ne l'est pas.
    -->
    <div
      class="flex w-full overflow-hidden rounded-md"
      :class="variante === 'bandeau' ? 'h-1.5 gap-px' : 'h-9 gap-0.5'"
    >
      <div
        v-for="(part, index) in dessin"
        :key="index"
        class="flex gap-px"
        :style="{
          flexGrow: part.part,
          flexBasis: 0,
          minWidth: variante === 'bandeau' ? '3px' : '6px',
        }"
        :title="part.infobulle"
      >
        <div
          v-for="(morceau, rang) in part.morceaux"
          :key="rang"
          class="h-full"
          :style="{
            flexGrow: morceau.duree,
            flexBasis: 0,
            backgroundColor: morceau.recup ? 'var(--color-effort-facile)' : part.teinte,
            opacity: morceau.recup ? 0.55 : 1,
          }"
        />
      </div>
    </div>

    <!--
      Les étiquettes, sous la bande. La couleur ne porte jamais seule le sens : un athlète
      daltonien lit la même séance, et le palier calme est trop discret pour se suffire.
    -->
    <ol v-if="variante === 'complet'" class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
      <li v-for="(part, index) in dessin" :key="index" class="flex items-center gap-1.5">
        <span
          class="inline-block h-2 w-2 shrink-0 rounded-sm"
          :style="{ backgroundColor: part.teinte }"
        />
        <span class="tabulaire font-medium">{{ part.bloc.libelle }}</span>
        <span class="text-[var(--color-doux)]">{{ part.nom.toLowerCase() }}</span>
        <span v-if="part.bloc.recupSec" class="tabulaire text-[var(--color-doux)]">
          · récup {{ dureeCourte(part.bloc.recupSec) }}
        </span>
      </li>
    </ol>

    <p
      v-if="variante === 'complet' && blocs.length > 1"
      class="mt-2 text-xs text-[var(--color-doux)]"
    >
      La largeur suit le temps passé, pas la distance.
    </p>
  </div>
</template>
