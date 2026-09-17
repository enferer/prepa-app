<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import CarteSeance from '@/components/cycle/CarteSeance.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import { useEntrainement } from '@/stores/entrainement'
import { dateCourte, km, pourcentage } from '@/composables/useFormat'
import type { Seance, Semaine, StatutSeance } from '@/api/types'

const entrainement = useEntrainement()
const router = useRouter()

const aVenirSeulement = ref(false)
const depliees = ref<Set<string>>(new Set())

const aujourdhui = new Date().toISOString().slice(0, 10)

const semaines = computed(() => {
  const toutes = entrainement.semaines
  return aVenirSeulement.value
    ? toutes.filter((s) => entrainement.finDe(s) >= aujourdhui)
    : toutes
})

// La semaine en cours est ouverte d'emblee : c'est celle qu'on vient consulter.
if (entrainement.semaineCourante) depliees.value.add(entrainement.semaineCourante.id)

function basculer(semaine: Semaine) {
  if (depliees.value.has(semaine.id)) depliees.value.delete(semaine.id)
  else depliees.value.add(semaine.id)
  depliees.value = new Set(depliees.value)
}

function realise(semaine: Semaine) {
  return entrainement.volumeRealise(semaine)
}

function part(semaine: Semaine) {
  return semaine.volumeCibleKm ? (realise(semaine) / semaine.volumeCibleKm) * 100 : null
}

async function trancher(seance: Seance, statut: StatutSeance) {
  await entrainement.changerStatut(seance, statut)
}

function ouvrirActivite(seance: Seance) {
  if (seance.activityId) router.push({ name: 'seance', params: { id: seance.activityId } })
}
</script>

<template>
  <EtatVide
    v-if="!entrainement.cycleActif"
    titre="Pas de plan à afficher"
    message="Aucun cycle n'est en cours."
  />

  <div v-else class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold">{{ entrainement.cycleActif.cycle.nom }}</h1>
      <label class="flex items-center gap-2 text-sm text-[var(--color-doux)]">
        <input v-model="aVenirSeulement" type="checkbox" class="accent-[var(--color-accent)]" />
        À venir seulement
      </label>
    </div>

    <CarteBase
      v-for="semaine in semaines"
      :key="semaine.id"
      :titre="`Semaine ${semaine.numero}`"
      :sous-titre="semaine.bloc ? semaine.bloc.toLowerCase() : undefined"
    >
      <template #entete>
        <button class="text-sm text-[var(--color-accent)]" @click="basculer(semaine)">
          {{ depliees.has(semaine.id) ? 'Replier' : 'Déplier' }}
        </button>
      </template>

      <div class="flex flex-wrap items-center gap-x-5 gap-y-1 text-sm">
        <span class="text-[var(--color-doux)]">
          du {{ dateCourte(semaine.dateDebut) }} au {{ dateCourte(entrainement.finDe(semaine)) }}
        </span>
        <span class="tabulaire">
          {{ km(realise(semaine)) }} / {{ km(semaine.volumeCibleKm) }}
          <span v-if="part(semaine) !== null" class="text-[var(--color-doux)]">
            ({{ pourcentage(part(semaine)) }})
          </span>
        </span>
        <span v-if="semaine.deniveleCibleM" class="text-[var(--color-doux)]">
          D+ visé {{ semaine.deniveleCibleM }} m
        </span>
      </div>

      <p v-if="semaine.note" class="mt-2 text-sm text-[var(--color-doux)]">{{ semaine.note }}</p>

      <!--
        Une semaine non detaillee n'est pas une semaine vide : le coach n'y a pas encore pose
        de seances, et c'est volontaire. On le dit, sinon l'absence se lit comme un oubli.
      -->
      <p
        v-if="!semaine.detaillee"
        class="mt-3 rounded-lg bg-[var(--color-appui)] px-3 py-2 text-sm text-[var(--color-doux)]"
      >
        Semaine en cibles seules — ton coach la détaillera au fil des points hebdomadaires.
        Vise {{ km(semaine.volumeCibleKm) }}<template v-if="semaine.nbQualiteCible">
          et {{ semaine.nbQualiteCible }} séance<template v-if="semaine.nbQualiteCible > 1">s</template>
          de qualité</template>.
      </p>

      <div v-else-if="depliees.has(semaine.id)" class="mt-3 space-y-3">
        <CarteSeance
          v-for="seance in semaine.seances"
          :key="seance.id"
          :seance="seance"
          @statut="(s) => trancher(seance, s)"
          @ouvrir="ouvrirActivite(seance)"
        />
      </div>

      <p v-else class="mt-3 text-sm text-[var(--color-doux)]">
        {{ semaine.seances.length }} séance<template v-if="semaine.seances.length > 1">s</template>
      </p>
    </CarteBase>
  </div>
</template>
