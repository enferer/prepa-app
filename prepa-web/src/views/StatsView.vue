<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { analyseApi } from '@/api'
import { useEntrainement } from '@/stores/entrainement'
import { allure, chrono, dateCourte, km, signe } from '@/composables/useFormat'
import type { Provenance, Synthese } from '@/api/types'

/**
 * Est-ce que je progresse ?
 *
 * <p>Cette vue ne raconte pas ce qui a été fait — l'onglet Saison s'en charge — mais ce que
 * l'entraînement produit : les allures réellement tenues face à celles visées, l'équilibre
 * entre facile et intensité, les meilleurs efforts, ce que le cycle a changé.
 */
const entrainement = useEntrainement()

const synthese = ref<Synthese | null>(null)
const fenetre = ref(90)
const chargement = ref(true)

const LIBELLES_FENETRE: Record<number, string> = {
  30: '30 derniers jours',
  90: '90 derniers jours',
  365: 'douze derniers mois',
}

/** Ce que le filtre couvre, écrit tel quel sur chaque carte qui en dépend. */
const surLaPeriode = computed(() => LIBELLES_FENETRE[fenetre.value] ?? `${fenetre.value} jours`)

/** Ce que le filtre ne couvre pas : les records se cherchent dans tout l'historique. */
const TOUT = 'tout ton historique'

/**
 * D'où vient un chrono, dit sans jargon.
 *
 * <p>L'ancienne étiquette opposait « mesuré » à « estimé » selon que la montre avait transmis
 * le détail des tours — ce qui n'a rien à voir avec la confiance qu'on peut accorder au
 * chiffre. Un dix kilomètres couru pour lui-même s'affichait « estimé », un kilomètre dévalé
 * en descente « mesuré ».
 */
const PROVENANCES: Record<Provenance, { libelle: string; explication: string }> = {
  SORTIE: {
    libelle: 'couru',
    explication: 'Cette sortie fait la distance : le chrono est celui de la course elle-même.',
  },
  TOURS: {
    libelle: 'mesuré',
    explication: 'Meilleur passage retrouvé dans une sortie plus longue, tour par tour.',
  },
  ESTIMATION: {
    libelle: 'estimé',
    explication:
      "Déduit de l'allure moyenne d'une sortie plus longue : un ordre de grandeur, pas un chrono. "
      + 'Aucun temps réellement couru sur cette distance n’a encore été enregistré.',
  },
}

const DISTANCES: Record<number, string> = {
  1000: '1 km',
  5000: '5 km',
  10000: '10 km',
  21097: 'Semi',
  42195: 'Marathon',
}

async function charger() {
  if (!entrainement.athleteId) return
  chargement.value = true
  try {
    synthese.value = await analyseApi.synthese(entrainement.athleteId, fenetre.value)
  } finally {
    chargement.value = false
  }
}

function changerFenetre(jours: number) {
  fenetre.value = jours
  charger()
}

/**
 * La dérive cardiaque, traduite.
 *
 * <p>Courir au même rythme avec un cœur qui bat plus vite est le signe le plus précoce
 * d'une fatigue qui s'installe — bien avant que les allures ne se dégradent. En dessous de
 * quatre battements, c'est du bruit.
 */
const tendanceCardiaque = computed(() => {
  const tendance = synthese.value?.tendanceFc
  if (!tendance?.ecart && tendance?.ecart !== 0) return null
  if (!tendance.fcMoyRecente || !tendance.fcMoyPrecedente) return null

  const ecart = tendance.ecart
  const commun = {
    ecart,
    recente: tendance.fcMoyRecente,
    precedente: tendance.fcMoyPrecedente,
  }
  if (ecart >= 4) {
    return {
      ...commun,
      ton: 'alerte' as const,
      message:
        'Ton cœur bat plus vite pour un entraînement comparable. C’est souvent le premier signe d’une fatigue qui s’installe — ton coach en tiendra compte.',
    }
  }
  if (ecart <= -4) {
    return {
      ...commun,
      ton: 'succes' as const,
      message: 'Ton cœur travaille moins pour le même entraînement : ta forme progresse.',
    }
  }
  return {
    ...commun,
    ton: 'neutre' as const,
    message: 'Rien à signaler : ton cœur réagit comme d’habitude à ton entraînement.',
  }
})

/** Un écart de quelques secondes au kilomètre n'est pas un écart : c'est la météo, le terrain. */
function tonDeLEcart(ecart?: number): 'succes' | 'alerte' | 'neutre' {
  if (ecart === undefined || ecart === null) return 'neutre'
  if (Math.abs(ecart) <= 8) return 'neutre'
  return ecart < 0 ? 'succes' : 'alerte'
}

onMounted(charger)

// Changer de profil consulte doit refaire l'analyse, pas garder celle du precedent.
watch(() => entrainement.athleteId, charger)
</script>

<template>
  <div v-if="chargement" class="text-sm text-[var(--color-doux)]">Chargement…</div>

  <EtatVide v-else-if="!synthese?.fenetre" titre="Pas encore de données à analyser" />

  <div v-else class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-lg font-semibold">Est-ce que tu progresses ?</h1>
        <p class="text-sm text-[var(--color-doux)]">
          Ce que ton entraînement produit, et non ce que tu as fait — c'est l'onglet Saison
          qui le raconte. Chaque carte indique la période sur laquelle elle est calculée :
          certaines suivent le filtre, d'autres regardent tout ton historique.
        </p>
      </div>
      <div class="inline-flex rounded-lg border border-[var(--color-bordure)] p-0.5">
        <button
          v-for="choix in [30, 90, 365]"
          :key="choix"
          class="rounded-md px-3 py-1 text-sm"
          :class="fenetre === choix
            ? 'bg-[var(--color-accent-fond)] font-medium text-[var(--color-accent)]'
            : 'text-[var(--color-doux)]'"
          @click="changerFenetre(choix)"
        >
          {{ choix === 365 ? '1 an' : `${choix} j` }}
        </button>
      </div>
    </div>

    <!--
      Le cœur de la vue : ce que l'athlète court vraiment, face à ce qui lui est demandé.
      Aucune autre mesure ne dit aussi directement si l'entraînement fait ce qu'il prétend.
    -->
    <CarteBase
      v-if="synthese.alluresParType.length"
      titre="Tes allures, face à tes cibles"
      :portee="surLaPeriode"
      sous-titre="Sur une séance à intervalles, l'allure retenue est celle des blocs d'effort — pas la moyenne de la sortie, qui mélange échauffement et récupérations. Le relief est neutralisé de la même façon."
    >
      <table class="w-full text-sm">
        <tbody class="divide-y divide-[var(--color-bordure)]">
          <tr v-for="ligne in synthese.alluresParType" :key="ligne.type">
            <td class="py-2 font-medium">{{ ligne.libelle }}</td>
            <td class="tabulaire py-2 text-[var(--color-doux)]">
              {{ ligne.nbSeances }} séance{{ ligne.nbSeances > 1 ? 's' : '' }}
              <span
                v-if="ligne.nbEcartees"
                :title="`${ligne.nbEcartees} sortie(s) trop vallonnée(s) pour que l'allure se compare à une cible, et sans allure corrigée disponible.`"
                class="text-xs"
              >
                (+{{ ligne.nbEcartees }} écartée{{ ligne.nbEcartees > 1 ? 's' : '' }})
              </span>
            </td>
            <td class="tabulaire py-2 font-semibold">{{ allure(ligne.allureReelleSecKm) }}/km</td>
            <td class="tabulaire py-2 text-[var(--color-doux)]">
              <template v-if="ligne.allureCibleSecKm">
                visé {{ allure(ligne.allureCibleSecKm) }}
              </template>
            </td>
            <td class="tabulaire py-2 text-right">
              <span
                v-if="ligne.ecartSecKm !== undefined && ligne.ecartSecKm !== null"
                class="font-medium"
                :class="{
                  'text-[var(--color-succes)]': tonDeLEcart(ligne.ecartSecKm) === 'succes',
                  'text-[var(--color-alerte)]': tonDeLEcart(ligne.ecartSecKm) === 'alerte',
                  'text-[var(--color-doux)]': tonDeLEcart(ligne.ecartSecKm) === 'neutre',
                }"
              >
                {{ signe(ligne.ecartSecKm) }} s/km
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="mt-3 text-xs text-[var(--color-doux)]">
        Un écart de quelques secondes ne veut rien dire : le vent, le terrain et la chaleur en
        valent bien dix.
        <template v-if="synthese.alluresParType.some((l) => l.surAllureCorrigee)">
          Les sorties vallonnées sont comptées à leur allure corrigée de la pente ;
        </template>
        <template v-if="synthese.alluresParType.some((l) => l.nbEcartees)">
          celles dont le relief n'a pas pu être neutralisé sont écartées plutôt que de fausser
          la moyenne.
        </template>
      </p>
    </CarteBase>

    <!--
      Le principe le plus simple de la méthodologie, et le plus souvent trahi : on court son
      facile trop vite et son rapide trop lentement. Le rendre visible permet de le vérifier.
    -->
    <CarteBase
      v-if="synthese.repartition"
      titre="Facile ou dur ?"
      :portee="surLaPeriode"
      sous-titre="Environ quatre cinquièmes du volume devraient être courus en endurance."
    >
      <div class="flex h-8 overflow-hidden rounded-lg">
        <div
          class="flex items-center justify-center text-xs font-medium text-white"
          :style="{ width: `${synthese.repartition.partFacilePct}%`, backgroundColor: 'var(--color-succes)' }"
        >
          {{ synthese.repartition.partFacilePct }} % facile
        </div>
        <div
          class="flex items-center justify-center text-xs font-medium text-white"
          :style="{ width: `${synthese.repartition.partIntensitePct}%`, backgroundColor: 'var(--color-alerte)' }"
        >
          {{ synthese.repartition.partIntensitePct }} %
        </div>
      </div>
      <p class="tabulaire mt-2 text-xs text-[var(--color-doux)]">
        {{ km(synthese.repartition.kmFacile, 0) }} en endurance ·
        {{ km(synthese.repartition.kmIntensite, 0) }} en intensité
      </p>
      <p class="mt-2 text-sm">{{ synthese.repartition.lecture }}</p>
    </CarteBase>

    <CarteBase
      v-if="synthese.records.length"
      titre="Tes meilleurs efforts"
      :portee="TOUT"
      sous-titre="Le meilleur de chaque distance depuis que tu enregistres tes sorties — le filtre de période ne s'y applique pas."
    >
      <table class="w-full text-sm">
        <tbody class="tabulaire divide-y divide-[var(--color-bordure)]">
          <tr v-for="record in synthese.records" :key="record.distanceM">
            <td class="py-2 font-medium">
              {{ DISTANCES[record.distanceM] ?? `${record.distanceM} m` }}
            </td>
            <td class="py-2 font-semibold">{{ chrono(record.tempsSec) }}</td>
            <td class="py-2">{{ allure(record.allureSecKm) }}/km</td>
            <td class="py-2 text-[var(--color-doux)]">{{ dateCourte(record.date) }}</td>
            <td class="py-2 text-right text-xs text-[var(--color-doux)]">
              <span :title="PROVENANCES[record.provenance].explication">
                {{ PROVENANCES[record.provenance].libelle }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </CarteBase>

    <CarteBase
      v-if="tendanceCardiaque"
      titre="Comment tu encaisses"
      portee="4 semaines contre les 4 précédentes"
      sous-titre="Ta fréquence cardiaque moyenne du mois, comparée au mois précédent."
    >
      <div class="flex flex-wrap items-center gap-4">
        <div
          class="rounded-lg px-3 py-2"
          :class="tendanceCardiaque.ton === 'alerte'
            ? 'bg-[var(--color-alerte-fond)]'
            : tendanceCardiaque.ton === 'succes'
              ? 'bg-[var(--color-succes-fond)]'
              : 'bg-[var(--color-appui)]'"
        >
          <span
            class="tabulaire text-2xl font-semibold"
            :class="tendanceCardiaque.ton === 'alerte'
              ? 'text-[var(--color-alerte)]'
              : tendanceCardiaque.ton === 'succes'
                ? 'text-[var(--color-succes)]'
                : 'text-[var(--color-texte)]'"
          >
            {{ signe(tendanceCardiaque.ecart) }}
          </span>
          <span class="ml-1 text-sm text-[var(--color-doux)]">bpm</span>
        </div>

        <div class="min-w-0 flex-1">
          <p class="text-sm">{{ tendanceCardiaque.message }}</p>
          <p class="tabulaire mt-1 text-xs text-[var(--color-doux)]">
            {{ tendanceCardiaque.recente }} bpm en moyenne ces 4 dernières semaines,
            {{ tendanceCardiaque.precedente }} bpm les 4 d'avant.
          </p>
        </div>
      </div>
    </CarteBase>

    <CarteBase
      v-if="synthese.avantPendantCycle"
      titre="Ce que ce cycle a changé"
      portee="avant et depuis le début du cycle"
      :sous-titre="`${synthese.avantPendantCycle.avant.libelle} → ${synthese.avantPendantCycle.pendant.libelle}`"
    >
      <dl class="grid gap-3 sm:grid-cols-3">
        <div
          v-for="delta in synthese.avantPendantCycle.deltas"
          :key="delta.mesure"
          class="rounded-lg border border-[var(--color-bordure)] px-3 py-2"
        >
          <dt class="text-xs text-[var(--color-doux)]">{{ delta.mesure }}</dt>
          <dd class="tabulaire mt-1">
            <span
              class="font-semibold"
              :class="delta.amelioration ? 'text-[var(--color-succes)]' : 'text-[var(--color-doux)]'"
            >
              {{ signe(delta.variationPct) }} %
            </span>
            <span class="ml-2 text-xs text-[var(--color-doux)]">
              {{ delta.avant.toFixed(1) }} → {{ delta.pendant.toFixed(1) }}
            </span>
          </dd>
        </div>
      </dl>
    </CarteBase>

    <CarteBase titre="Ton entraînement en chiffres" :portee="surLaPeriode">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <TuileChiffre
          libelle="Par semaine"
          :valeur="km(synthese.volume.kmParSemaineMoyen)"
          :detail="`de ${km(synthese.volume.kmParSemaineMin, 0)} à ${km(synthese.volume.kmParSemaineMax, 0)}`"
        />
        <TuileChiffre libelle="Séances / semaine" :valeur="synthese.volume.seancesParSemaine.toFixed(1)" />
        <TuileChiffre
          libelle="Allure en endurance"
          :valeur="synthese.allureEf.allureMoySecKm ? `${allure(synthese.allureEf.allureMoySecKm)}` : '—'"
          :detail="`${synthese.allureEf.nbSeances} sorties sous ${synthese.allureEf.seuilFcRetenu} bpm`"
        />
        <TuileChiffre
          libelle="Plus longue de toujours"
          :valeur="synthese.plusLongueDeToujours ? km(synthese.plusLongueDeToujours.distanceKm) : '—'"
          :detail="synthese.plusLongueDeToujours ? dateCourte(synthese.plusLongueDeToujours.date) : undefined"
        />
      </div>
    </CarteBase>
  </div>
</template>
