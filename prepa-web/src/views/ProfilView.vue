<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import { profilApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { allure, chrono, dateLongue, km } from '@/composables/useFormat'
import type { Blessure, Contrainte, ProfilSportif, RecordPersonnel, SeanceSignature } from '@/api/types'

const auth = useAuth()
const router = useRouter()

const profil = ref<ProfilSportif | null>(null)
const records = ref<RecordPersonnel[]>([])
const blessures = ref<Blessure[]>([])
const contraintes = ref<Contrainte[]>([])
const signatures = ref<SeanceSignature[]>([])
const enregistrement = ref(false)

const DISTANCES: Record<number, string> = {
  1000: '1 km', 5000: '5 km', 10000: '10 km', 21097: 'Semi', 42195: 'Marathon',
}

const STATUTS: Record<string, string> = {
  ACTIVE: 'en cours',
  SURVEILLANCE: 'sous surveillance',
  RESOLUE: 'résolue',
}

async function charger() {
  if (!auth.athlete) return
  const id = auth.athlete.id
  ;[profil.value, records.value, blessures.value, contraintes.value, signatures.value] =
    await Promise.all([
      profilApi.lire(id),
      profilApi.records(id),
      profilApi.blessures(id),
      profilApi.contraintes(id),
      profilApi.signatures(id),
    ])
}

async function enregistrerProfil() {
  if (!auth.athlete || !profil.value) return
  enregistrement.value = true
  try {
    profil.value = await profilApi.enregistrer(auth.athlete.id, profil.value)
  } finally {
    enregistrement.value = false
  }
}

async function deconnecter() {
  await auth.deconnecter()
  router.push({ name: 'connexion' })
}

onMounted(charger)
</script>

<template>
  <div v-if="profil" class="space-y-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold">{{ auth.athlete?.displayName }}</h1>
      <button class="text-sm text-[var(--color-doux)] hover:underline" @click="deconnecter">
        Se déconnecter
      </button>
    </div>

    <CarteBase
      titre="Repères physiologiques"
      sous-titre="Ta fréquence cardiaque maximale sert à calibrer tes zones : sans elle, le coach travaille avec une moyenne qui n'est pas la tienne."
    >
      <div class="grid gap-3 sm:grid-cols-3">
        <label class="text-sm">
          <span class="text-[var(--color-doux)]">FC maximale</span>
          <input
            v-model.number="profil.fcMax"
            type="number"
            class="mt-1 w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2"
          />
        </label>
        <label class="text-sm">
          <span class="text-[var(--color-doux)]">FC au repos</span>
          <input
            v-model.number="profil.fcRepos"
            type="number"
            class="mt-1 w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2"
          />
        </label>
        <label class="text-sm">
          <span class="text-[var(--color-doux)]">VMA (km/h)</span>
          <input
            v-model.number="profil.vmaKmh"
            type="number"
            step="0.1"
            class="mt-1 w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2"
          />
        </label>
      </div>
      <p class="mt-2 text-xs text-[var(--color-doux)]">
        Seuil d'endurance retenu actuellement : {{ profil.seuilFcEnduranceFondamentale }} bpm.
      </p>
      <button
        class="mt-3 rounded-lg bg-[var(--color-accent)] px-4 py-1.5 text-sm font-medium text-white disabled:opacity-60"
        :disabled="enregistrement"
        @click="enregistrerProfil"
      >
        {{ enregistrement ? 'Enregistrement…' : 'Enregistrer' }}
      </button>
    </CarteBase>

    <CarteBase v-if="records.length" titre="Records">
      <ol class="divide-y divide-[var(--color-bordure)] text-sm">
        <li v-for="record in records" :key="record.id" class="tabulaire flex flex-wrap gap-4 py-2">
          <span class="w-20 font-medium">{{ DISTANCES[record.distanceM] ?? km(record.distanceM / 1000) }}</span>
          <span class="w-20">{{ chrono(record.tempsSec) }}</span>
          <span class="w-16 text-[var(--color-doux)]">{{ allure(record.allureSecKm) }}/km</span>
          <span class="text-[var(--color-doux)]">{{ dateLongue(record.date) }}</span>
        </li>
      </ol>
    </CarteBase>

    <CarteBase v-if="blessures.length" titre="Blessures suivies">
      <ol class="divide-y divide-[var(--color-bordure)]">
        <li v-for="blessure in blessures" :key="blessure.id" class="py-2">
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <span class="font-medium">{{ blessure.zone }}</span>
            <span
              class="rounded-full px-2 py-0.5 text-xs"
              :class="blessure.statut === 'RESOLUE'
                ? 'bg-[var(--color-succes-fond)] text-[var(--color-succes)]'
                : 'bg-[var(--color-alerte-fond)] text-[var(--color-alerte)]'"
            >
              {{ STATUTS[blessure.statut] }}
            </span>
            <span v-if="blessure.palier" class="text-xs text-[var(--color-doux)]">
              palier {{ blessure.palier }}
            </span>
          </div>
          <p v-if="blessure.consignes" class="mt-1 text-sm text-[var(--color-doux)]">
            {{ blessure.consignes }}
          </p>
        </li>
      </ol>
    </CarteBase>

    <CarteBase v-if="contraintes.length" titre="Contraintes">
      <ol class="divide-y divide-[var(--color-bordure)] text-sm">
        <li v-for="contrainte in contraintes" :key="contrainte.id" class="py-2">
          <span class="font-medium capitalize">{{ contrainte.type.toLowerCase() }}</span>
          <span v-if="contrainte.debut" class="ml-2 text-[var(--color-doux)]">
            {{ dateLongue(contrainte.debut) }}<template v-if="contrainte.fin"> → {{ dateLongue(contrainte.fin) }}</template>
          </span>
          <p class="mt-0.5 text-[var(--color-doux)]">{{ contrainte.detail }}</p>
        </li>
      </ol>
    </CarteBase>

    <CarteBase v-if="signatures.length" titre="Tes séances préférées">
      <ol class="divide-y divide-[var(--color-bordure)]">
        <li v-for="seance in signatures" :key="seance.id" class="py-2">
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <EtiquetteType :type="seance.typeSeance" compact />
            <span class="font-medium">{{ seance.nom }}</span>
            <span v-if="!seance.actif" class="text-xs text-[var(--color-doux)]">en pause</span>
          </div>
          <p v-if="seance.description" class="mt-0.5 text-sm text-[var(--color-doux)]">
            {{ seance.description }}
          </p>
        </li>
      </ol>
    </CarteBase>

    <CarteBase titre="Renforcement">
      <p class="text-sm">
        {{ profil.renfoActif ? 'Actif' : 'Désactivé' }}
        <template v-if="profil.renfoActif && profil.renfoFrequence">
          — {{ profil.renfoFrequence }} séance<template v-if="profil.renfoFrequence > 1">s</template> par semaine
        </template>
      </p>
      <p v-if="profil.renfoFocus" class="mt-1 text-sm text-[var(--color-doux)]">{{ profil.renfoFocus }}</p>
    </CarteBase>
  </div>
</template>
