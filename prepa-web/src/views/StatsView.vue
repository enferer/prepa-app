<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GrapheVolume from '@/components/charts/GrapheVolume.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { analyseApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { useEntrainement } from '@/stores/entrainement'
import { allure, chrono, dateCourte, km, signe } from '@/composables/useFormat'
import type { Synthese } from '@/api/types'

const auth = useAuth()
const entrainement = useEntrainement()

const synthese = ref<Synthese | null>(null)
const fenetre = ref(90)
const chargement = ref(true)

const DISTANCES: Record<number, string> = {
  1000: '1 km',
  5000: '5 km',
  10000: '10 km',
  21097: 'Semi',
  42195: 'Marathon',
}

/**
 * Le graphe suit la fenetre choisie : afficher deux ans d'historique sous un filtre a
 * quatre-vingt-dix jours donnerait deux lectures contradictoires du meme ecran.
 */
const volumeAffiche = computed(() => {
  const semainesVisibles = Math.ceil(fenetre.value / 7)
  return (synthese.value?.volumeHebdo ?? []).slice(-semainesVisibles)
})

/** Cibles hebdomadaires du cycle, pour les superposer au volume realise. */
const cibles = computed(() => {
  const table: Record<string, number> = {}
  for (const semaine of entrainement.semaines) {
    table[semaine.dateDebut] = semaine.volumeCibleKm
  }
  return table
})

async function charger() {
  if (!auth.athlete) return
  chargement.value = true
  try {
    synthese.value = await analyseApi.synthese(auth.athlete.id, fenetre.value)
  } finally {
    chargement.value = false
  }
}

function changerFenetre(jours: number) {
  fenetre.value = jours
  charger()
}

onMounted(charger)
</script>

<template>
  <div v-if="chargement" class="text-sm text-[var(--color-doux)]">Chargement…</div>

  <EtatVide v-else-if="!synthese?.fenetre" titre="Pas encore de données à analyser" />

  <div v-else class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold">Statistiques</h1>
      <div class="inline-flex rounded-lg border border-[var(--color-bordure)] p-0.5">
        <button
          v-for="choix in [30, 90, 365]"
          :key="choix"
          class="rounded-md px-3 py-1 text-sm"
          :class="fenetre === choix ? 'bg-[var(--color-accent-fond)] font-medium text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
          @click="changerFenetre(choix)"
        >
          {{ choix === 365 ? '1 an' : `${choix} j` }}
        </button>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <TuileChiffre
        libelle="Volume"
        :valeur="km(synthese.volume.kmTotal, 0)"
        :detail="`${synthese.volume.nbCourses} sorties`"
      />
      <TuileChiffre
        libelle="Par semaine"
        :valeur="km(synthese.volume.kmParSemaineMoyen)"
        :detail="`de ${km(synthese.volume.kmParSemaineMin, 0)} à ${km(synthese.volume.kmParSemaineMax, 0)}`"
      />
      <TuileChiffre
        libelle="Séances / semaine"
        :valeur="synthese.volume.seancesParSemaine.toFixed(1)"
      />
      <TuileChiffre
        libelle="Allure en endurance"
        :valeur="synthese.allureEf.allureMoySecKm ? `${allure(synthese.allureEf.allureMoySecKm)}/km` : '—'"
        :detail="`${synthese.allureEf.nbSeances} sorties sous ${synthese.allureEf.seuilFcRetenu} bpm`"
      />
    </div>

    <CarteBase titre="Volume hebdomadaire" sous-titre="Le trait pointillé marque la cible du plan.">
      <GrapheVolume :semaines="volumeAffiche" :cibles="cibles" />
    </CarteBase>

    <CarteBase
      v-if="synthese.tendanceFc?.ecart !== undefined && synthese.tendanceFc?.ecart !== null"
      titre="Fréquence cardiaque"
    >
      <p class="text-sm">{{ synthese.tendanceFc.lecture }}</p>
      <p class="tabulaire mt-1 text-sm text-[var(--color-doux)]">
        {{ synthese.tendanceFc.fcMoyRecente }} bpm sur les 4 dernières semaines,
        contre {{ synthese.tendanceFc.fcMoyPrecedente }} sur les 4 précédentes
        ({{ signe(synthese.tendanceFc.ecart) }}).
      </p>
    </CarteBase>

    <CarteBase
      v-if="synthese.records.length"
      titre="Meilleurs efforts"
      sous-titre="Mesurés sur les tours quand le détail existe, estimés sinon."
    >
      <table class="w-full text-sm">
        <tbody class="tabulaire divide-y divide-[var(--color-bordure)]">
          <tr v-for="record in synthese.records" :key="record.distanceM">
            <td class="py-1.5 font-medium">{{ DISTANCES[record.distanceM] ?? `${record.distanceM} m` }}</td>
            <td class="py-1.5">{{ chrono(record.tempsSec) }}</td>
            <td class="py-1.5">{{ allure(record.allureSecKm) }}/km</td>
            <td class="py-1.5 text-[var(--color-doux)]">{{ dateCourte(record.date) }}</td>
            <td class="py-1.5 text-xs text-[var(--color-doux)]">
              {{ record.surTours ? 'mesuré' : 'estimé' }}
            </td>
          </tr>
        </tbody>
      </table>
    </CarteBase>

    <CarteBase v-if="synthese.avantPendantCycle" titre="Ce que ce cycle a changé">
      <p class="mb-3 text-sm text-[var(--color-doux)]">
        {{ synthese.avantPendantCycle.avant.libelle }} → {{ synthese.avantPendantCycle.pendant.libelle }}
      </p>
      <dl class="grid gap-3 sm:grid-cols-3">
        <div
          v-for="delta in synthese.avantPendantCycle.deltas"
          :key="delta.mesure"
          class="rounded-lg border border-[var(--color-bordure)] px-3 py-2"
        >
          <dt class="text-xs text-[var(--color-doux)]">{{ delta.mesure }}</dt>
          <dd class="tabulaire mt-1">
            <span class="font-semibold" :class="delta.amelioration ? 'text-[var(--color-succes)]' : 'text-[var(--color-doux)]'">
              {{ signe(delta.variationPct) }} %
            </span>
            <span class="ml-2 text-xs text-[var(--color-doux)]">
              {{ delta.avant.toFixed(1) }} → {{ delta.pendant.toFixed(1) }}
            </span>
          </dd>
        </div>
      </dl>
    </CarteBase>

    <CarteBase v-if="synthese.plusLonguesSorties.length" titre="Plus longues sorties">
      <ol class="divide-y divide-[var(--color-bordure)] text-sm">
        <li v-for="effort in synthese.plusLonguesSorties" :key="effort.activityId" class="tabulaire flex gap-4 py-1.5">
          <span class="w-16 text-[var(--color-doux)]">{{ dateCourte(effort.date) }}</span>
          <span class="w-20 font-medium">{{ km(effort.distanceKm) }}</span>
          <span class="w-16">{{ allure(effort.allureSecKm) }}</span>
          <span class="text-[var(--color-doux)]">FC {{ effort.fcMoy ?? '—' }}</span>
        </li>
      </ol>
    </CarteBase>
  </div>
</template>
