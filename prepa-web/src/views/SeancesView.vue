<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BarreZonesFc from '@/components/activity/BarreZonesFc.vue'
import ChoixSeance from '@/components/activity/ChoixSeance.vue'
import TableauTours from '@/components/activity/TableauTours.vue'
import GrapheSeance from '@/components/charts/GrapheSeance.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { activitesApi } from '@/api'
import { useEntrainement } from '@/stores/entrainement'
import { allure, dateLongue, distance, duree, dureeCourte } from '@/composables/useFormat'
import type { ActivityDetail, ActivityResume } from '@/api/types'

const entrainement = useEntrainement()
const route = useRoute()
const router = useRouter()

const detail = ref<ActivityDetail | null>(null)
const chargement = ref(false)

const LIBELLES_TYPE: Record<string, string> = {
  RUN: 'Course à pied',
  TRAIL: 'Trail',
  TREADMILL: 'Tapis',
  BIKE: 'Vélo',
  SWIM: 'Natation',
  STRENGTH: 'Renforcement',
  HIKE: 'Marche',
  OTHER: 'Autre',
}

const liste = computed(() => entrainement.activites)

const selection = computed<ActivityResume | null>(() => {
  const id = route.params.id as string | undefined
  if (id) return liste.value.find((a) => a.id === id) ?? null
  return liste.value[0] ?? null
})

const position = computed(() => liste.value.findIndex((a) => a.id === selection.value?.id))

async function charger(id: string) {
  chargement.value = true
  try {
    detail.value = await activitesApi.detail(id)
  } finally {
    chargement.value = false
  }
}

function selectionner(activite: ActivityResume) {
  router.push({ name: 'seance', params: { id: activite.id } })
}

function selectionnerParId(id: string) {
  const activite = liste.value.find((a) => a.id === id)
  if (activite) selectionner(activite)
  listeOuverte.value = false
}

/**
 * Sur téléphone, la liste et le détail ne tiennent pas côte à côte : la liste s'ouvre à la
 * demande et se referme dès qu'on a choisi. Sur écran large elle reste là, en permanence.
 */
const listeOuverte = ref(false)

/**
 * Navigation d'une séance à l'autre. La liste va du plus récent au plus ancien : reculer
 * dans le temps, c'est avancer dans la liste.
 */
function deplacer(pas: number) {
  const suivante = liste.value[position.value + pas]
  if (suivante) selectionner(suivante)
}

function auClavier(evenement: KeyboardEvent) {
  if (evenement.key === 'ArrowLeft') deplacer(1)
  if (evenement.key === 'ArrowRight') deplacer(-1)
}

onMounted(() => {
  window.addEventListener('keydown', auClavier)
  if (selection.value) charger(selection.value.id)
})

watch(selection, (activite) => {
  if (activite) charger(activite.id)
})
</script>

<template>
  <EtatVide
    v-if="!liste.length"
    titre="Aucune séance enregistrée"
    message="Les séances arrivent automatiquement depuis ta montre."
  />

  <div v-else class="lg:grid lg:grid-cols-[17rem_1fr] lg:gap-6">
    <!--
      Le choix de la séance, permanent sur écran large. La liste déroulante qu'il remplace ne
      montrait qu'une ligne à la fois et ne se cherchait pas : retrouver le fractionné d'il y a
      trois semaines demandait de connaître sa date.
    -->
    <aside class="hidden lg:sticky lg:top-4 lg:block">
      <ChoixSeance
        :activites="liste"
        :selection="selection?.id"
        hauteur="calc(100vh - 14rem)"
        @choisir="selectionnerParId"
      />
    </aside>

    <div class="min-w-0 space-y-4">
    <div class="flex items-center gap-2 lg:justify-end">
      <button
        class="flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-lg border border-[var(--color-bordure)] bg-[var(--color-surface)] px-3 py-2 text-left text-sm lg:hidden"
        @click="listeOuverte = !listeOuverte"
      >
        <span class="min-w-0 flex-1 truncate">
          <span class="tabulaire font-medium">{{ dateLongue(selection?.date) }}</span>
          <span class="text-[var(--color-doux)]"> — {{ distance(selection?.distanceM) }}</span>
        </span>
        <span class="shrink-0 text-[var(--color-doux)]">{{ listeOuverte ? '▲' : '▼' }}</span>
      </button>

      <div class="flex shrink-0 gap-1">
        <button
          class="cursor-pointer rounded-md border border-[var(--color-bordure)] px-2.5 py-2 text-sm disabled:opacity-40"
          :disabled="position >= liste.length - 1"
          title="Séance précédente (flèche gauche)"
          @click="deplacer(1)"
        >
          ◀
        </button>
        <button
          class="cursor-pointer rounded-md border border-[var(--color-bordure)] px-2.5 py-2 text-sm disabled:opacity-40"
          :disabled="position <= 0"
          title="Séance suivante (flèche droite)"
          @click="deplacer(-1)"
        >
          ▶
        </button>
      </div>

      <span class="tabulaire hidden shrink-0 text-xs text-[var(--color-doux)] sm:inline">
        {{ position + 1 }} sur {{ liste.length }}
      </span>
    </div>

    <!--
      Sur téléphone la liste se déplie au-dessus du détail : elle doit rester courte et se
      refermer dès qu'on a choisi, sinon on perd de vue la séance qu'on était en train de lire.
    -->
    <div
      v-if="listeOuverte"
      class="rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] p-2 lg:hidden"
    >
      <ChoixSeance
        :activites="liste"
        :selection="selection?.id"
        hauteur="60vh"
        @choisir="selectionnerParId"
      />
    </div>

    <section v-if="detail" class="space-y-4">
      <header class="flex flex-wrap items-baseline gap-3">
        <h1 class="text-lg font-semibold">{{ detail.resume.titre || 'Séance' }}</h1>
        <span class="text-sm text-[var(--color-doux)]">
          {{ LIBELLES_TYPE[detail.resume.type] }} · {{ dateLongue(detail.resume.date) }}
          <template v-if="detail.lieu"> · {{ detail.lieu }}</template>
        </span>
      </header>

      <!-- Une séance de renforcement n'a ni distance ni allure : on n'affiche que ce qui existe. -->
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <TuileChiffre
          v-if="detail.resume.distanceM"
          libelle="Distance"
          :valeur="distance(detail.resume.distanceM, 2)"
        />
        <TuileChiffre libelle="Durée" :valeur="duree(detail.resume.dureeSec)" />
        <TuileChiffre
          v-if="detail.resume.allureMoySecKm"
          libelle="Allure"
          :valeur="`${allure(detail.resume.allureMoySecKm)}/km`"
          :detail="detail.gapMoySecKm ? `GAP ${allure(detail.gapMoySecKm)}` : undefined"
        />
        <TuileChiffre
          v-if="detail.resume.fcMoy"
          libelle="FC moyenne"
          :valeur="String(detail.resume.fcMoy)"
          :detail="detail.fcMax ? `max ${detail.fcMax}` : undefined"
        />
        <TuileChiffre
          v-if="detail.resume.denivelePosM"
          libelle="Dénivelé"
          :valeur="`+${detail.resume.denivelePosM} m`"
          :detail="detail.deniveleNegM ? `−${detail.deniveleNegM} m` : undefined"
        />
        <TuileChiffre v-if="detail.calories" libelle="Calories" :valeur="String(detail.calories)" />
      </div>

      <div class="flex flex-wrap gap-2 text-xs">
        <span
          v-if="detail.meteo?.temperatureC !== undefined"
          class="rounded-full bg-[var(--color-appui)] px-2.5 py-1"
        >
          {{ detail.meteo.temperatureC }} °C
          <template v-if="detail.meteo.ressentiC !== undefined">
            (ressenti {{ detail.meteo.ressentiC }} °C)
          </template>
        </span>
        <span v-if="detail.meteo?.ventKmh" class="rounded-full bg-[var(--color-appui)] px-2.5 py-1">
          vent {{ detail.meteo.ventKmh }} km/h
        </span>
        <span v-if="detail.meteo?.humidite" class="rounded-full bg-[var(--color-appui)] px-2.5 py-1">
          {{ detail.meteo.humidite }} % d'humidité
        </span>
        <span v-if="detail.teAerobie" class="rounded-full bg-[var(--color-appui)] px-2.5 py-1">
          effet aérobie {{ detail.teAerobie }}
        </span>
        <span v-if="detail.teAnaerobie" class="rounded-full bg-[var(--color-appui)] px-2.5 py-1">
          anaérobie {{ detail.teAnaerobie }}
        </span>
      </div>

      <CarteBase v-if="detail.zonesFc?.length" titre="Zones de fréquence cardiaque">
        <BarreZonesFc :zones="detail.zonesFc" />
      </CarteBase>

      <CarteBase v-if="detail.tours.length" titre="Le déroulé de ta séance">
        <GrapheSeance :tours="detail.tours" />
        <div class="mt-5 border-t border-[var(--color-bordure)] pt-4">
          <TableauTours :tours="detail.tours" :blocs="detail.blocs" :structuree="detail.structuree" />
        </div>
      </CarteBase>

      <CarteBase v-else titre="Déroulé">
        <p class="text-sm text-[var(--color-doux)]">
          Pas de détail par tour pour cette séance. Durée totale {{ dureeCourte(detail.resume.dureeSec) }}.
        </p>
      </CarteBase>
    </section>

    <p v-else-if="chargement" class="text-sm text-[var(--color-doux)]">Chargement…</p>
    </div>
  </div>
</template>
