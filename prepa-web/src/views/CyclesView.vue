<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { cyclesApi, type BilanCycle } from '@/api'
import { useEntrainement } from '@/stores/entrainement'
import { chrono, dateLongue, km, pourcentage } from '@/composables/useFormat'
import type { BilanHebdo, Cycle } from '@/api/types'

const entrainement = useEntrainement()

const bilans = ref<Record<string, BilanCycle>>({})
const rapports = ref<BilanHebdo[]>([])
const deplie = ref<string | null>(null)

const LIBELLES_LIGNE: Record<string, string> = {
  MAINTIEN_CHARGE: 'Maintenir la charge',
  VO2MAX: 'Progresser en VO2max',
  ENDURANCE_FONDAMENTALE: 'Construire l’endurance',
  TRAIL_DENIVELE: 'Trail et dénivelé',
  VITESSE_COURTE: 'Vitesse courte',
  REPRISE_POST_COURSE: 'Reprise après course',
  RETOUR_BLESSURE: 'Retour de blessure',
  AUTRE: 'Autre',
}

const cyclesTries = computed(() => entrainement.cycles)

async function ouvrir(cycle: Cycle) {
  if (deplie.value === cycle.id) {
    deplie.value = null
    return
  }
  deplie.value = cycle.id
  if (!bilans.value[cycle.id]) {
    bilans.value[cycle.id] = await cyclesApi.bilan(cycle.id)
  }
  rapports.value = await cyclesApi.rapports(cycle.id)
}

onMounted(() => {
  const actif = entrainement.cycleActif?.cycle
  if (actif) ouvrir(actif)
})
</script>

<template>
  <div class="space-y-5">
    <div>
      <h1 class="text-lg font-semibold">Tes cycles</h1>
      <p class="mt-1 text-sm text-[var(--color-doux)]">
        Une préparation vise une course ; un cycle libre suit une ligne directrice sur un horizon
        choisi. C'est ton coach qui les ouvre et les clôture.
      </p>
    </div>

    <CarteBase
      v-for="cycle in cyclesTries"
      :key="cycle.id"
      :titre="cycle.nom"
      :sous-titre="cycle.type === 'PREPA'
        ? `Préparation — ${cycle.courseNom ?? 'course'} le ${dateLongue(cycle.courseDate)}`
        : `Cycle libre — ${LIBELLES_LIGNE[cycle.ligneDirectriceType ?? 'AUTRE']}`"
    >
      <template #entete>
        <div class="flex items-center gap-2">
          <span
            v-if="cycle.statut === 'ACTIF'"
            class="rounded-full bg-[var(--color-succes-fond)] px-2 py-0.5 text-xs font-medium text-[var(--color-succes)]"
          >
            en cours
          </span>
          <button class="text-sm text-[var(--color-accent)]" @click="ouvrir(cycle)">
            {{ deplie === cycle.id ? 'Replier' : 'Détails' }}
          </button>
        </div>
      </template>

      <p class="text-sm text-[var(--color-doux)]">
        du {{ dateLongue(cycle.dateDebut) }} au {{ dateLongue(cycle.dateFin) }} · {{ cycle.nbSemaines }} semaines
        <template v-if="cycle.chronoViseSec"> · objectif {{ chrono(cycle.chronoViseSec) }}</template>
      </p>

      <p v-if="cycle.ligneDirectrice" class="mt-1 text-sm italic">« {{ cycle.ligneDirectrice }} »</p>

      <div v-if="deplie === cycle.id" class="mt-4 space-y-4">
        <div v-if="bilans[cycle.id]" class="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <TuileChiffre
            libelle="Assiduité"
            :valeur="bilans[cycle.id].assiduitePct !== undefined && bilans[cycle.id].assiduitePct !== null
              ? pourcentage(bilans[cycle.id].assiduitePct!)
              : '—'"
            :detail="`${bilans[cycle.id].seancesValidees} tenues · ${bilans[cycle.id].seancesManquees} manquées`"
          />
          <TuileChiffre libelle="Séances" :valeur="String(bilans[cycle.id].nbSeances)" />
          <TuileChiffre libelle="Semaines" :valeur="String(bilans[cycle.id].nbSemaines)" />
          <TuileChiffre libelle="Volume visé" :valeur="km(bilans[cycle.id].volumeCibleTotalKm, 0)" />
        </div>

        <div v-if="cycle.bilan" class="rounded-lg bg-[var(--color-accent-fond)] px-3 py-2 text-sm">
          <span class="font-medium text-[var(--color-accent)]">Bilan de ton coach — </span>{{ cycle.bilan }}
        </div>

        <div v-if="Object.keys(cycle.alluresCibles ?? {}).length">
          <h3 class="text-sm font-medium">Allures cibles</h3>
          <div class="mt-2 flex flex-wrap gap-2">
            <span
              v-for="(valeur, zone) in cycle.alluresCibles"
              :key="zone"
              class="tabulaire rounded-lg bg-[var(--color-appui)] px-2.5 py-1 text-sm"
              :title="valeur.note"
            >
              <span class="text-[var(--color-doux)]">{{ zone }}</span> {{ valeur.affichage ?? '—' }}
            </span>
          </div>
        </div>

        <div v-if="deplie === cycle.id && rapports.length">
          <h3 class="text-sm font-medium">Bilans de semaine</h3>
          <ol class="mt-2 divide-y divide-[var(--color-bordure)]">
            <li v-for="rapport in rapports" :key="rapport.id" class="py-2">
              <p class="text-sm font-medium">Semaine du {{ dateLongue(rapport.dateDebut) }}</p>
              <p class="mt-0.5 text-sm whitespace-pre-line text-[var(--color-doux)]">{{ rapport.bilan }}</p>
              <p v-if="rapport.consignes" class="mt-1 text-sm">
                <span class="text-[var(--color-doux)">Pour la suite — </span>{{ rapport.consignes }}
              </p>
            </li>
          </ol>
        </div>
      </div>
    </CarteBase>
  </div>
</template>
