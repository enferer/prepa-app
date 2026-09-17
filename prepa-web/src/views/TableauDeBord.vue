<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BandeauCycle from '@/components/cycle/BandeauCycle.vue'
import CarteSeance from '@/components/cycle/CarteSeance.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import EtiquetteType from '@/components/ui/EtiquetteType.vue'
import PastilleStatut from '@/components/ui/PastilleStatut.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { useEntrainement } from '@/stores/entrainement'
import { dateCourte, jourSemaine, km, pourcentage } from '@/composables/useFormat'
import type { Seance, StatutSeance } from '@/api/types'

const entrainement = useEntrainement()
const router = useRouter()

const cycle = computed(() => entrainement.cycleActif?.cycle ?? null)
const semaine = computed(() => entrainement.semaineCourante)

/** Les sept jours de la semaine en cours, avec leurs séances — la lecture la plus utile au quotidien. */
const jours = computed(() => {
  if (!semaine.value) return []
  return Array.from({ length: 7 }, (_, decalage) => {
    const jour = new Date(semaine.value!.dateDebut)
    jour.setDate(jour.getDate() + decalage)
    const iso = jour.toISOString().slice(0, 10)
    return {
      iso,
      aujourdhui: iso === new Date().toISOString().slice(0, 10),
      seances: semaine.value!.seances.filter((s) => s.date === iso),
    }
  })
})

const volumeSemaine = computed(() =>
  semaine.value ? entrainement.volumeRealise(semaine.value) : 0,
)

const partDeLaCible = computed(() => {
  if (!semaine.value?.volumeCibleKm) return null
  return (volumeSemaine.value / semaine.value.volumeCibleKm) * 100
})

/** Séances échues non tranchées : ce que l'athlète peut clarifier tout de suite. */
const aTrancher = computed(() => {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  return entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.date < aujourdhui && s.statut === 'A_VENIR' && s.type !== 'REPOS')
    .slice(-3)
})

async function trancher(seance: Seance, statut: StatutSeance) {
  await entrainement.changerStatut(seance, statut)
}

function ouvrirActivite(seance: Seance) {
  if (seance.activityId) router.push({ name: 'seance', params: { id: seance.activityId } })
}
</script>

<template>
  <div v-if="entrainement.chargement" class="text-sm text-[var(--color-doux)]">Chargement…</div>

  <EtatVide
    v-else-if="entrainement.sansCycle"
    titre="Aucun cycle en cours"
    message="Ton coach n'a pas encore ouvert de cycle. Demande-lui d'en démarrer un — une préparation si tu vises une course, un cycle libre sinon."
  />

  <div v-else-if="cycle" class="space-y-5">
    <BandeauCycle :cycle="cycle" :semaine-courante="semaine?.numero" />

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <TuileChiffre
        libelle="Cette semaine"
        :valeur="km(volumeSemaine)"
        :detail="semaine ? `cible ${km(semaine.volumeCibleKm)}` : undefined"
        :ton="partDeLaCible !== null && partDeLaCible < 75 ? 'alerte' : 'neutre'"
      />
      <TuileChiffre
        libelle="Assiduité"
        :valeur="entrainement.assiduite.pourcentage !== null ? pourcentage(entrainement.assiduite.pourcentage) : '—'"
        :detail="`${entrainement.assiduite.tenues} tenues · ${entrainement.assiduite.manquees} manquées`"
      />
      <TuileChiffre
        libelle="Séances du cycle"
        :valeur="String(entrainement.assiduite.total)"
        :detail="`${cycle.nbSemaines} semaines`"
      />
      <TuileChiffre
        libelle="Bloc"
        :valeur="semaine?.bloc ? semaine.bloc.toLowerCase() : '—'"
        :detail="semaine?.detaillee === false ? 'cibles seules' : undefined"
      />
    </div>

    <CarteBase v-if="entrainement.seancesDuJour.length" titre="Aujourd'hui">
      <div class="space-y-3">
        <CarteSeance
          v-for="seance in entrainement.seancesDuJour"
          :key="seance.id"
          :seance="seance"
          mise-en-avant
          @statut="(s) => trancher(seance, s)"
          @ouvrir="ouvrirActivite(seance)"
        />
      </div>
    </CarteBase>

    <CarteBase v-else titre="Aujourd'hui">
      <p class="text-sm text-[var(--color-doux)]">
        Rien de prévu aujourd'hui. Repos ou séance libre — à toi de voir.
      </p>
    </CarteBase>

    <CarteBase
      v-if="semaine"
      titre="Ta semaine"
      :sous-titre="semaine.detaillee === false
        ? 'Semaine en cibles seules : ton coach la détaillera au prochain point.'
        : undefined"
    >
      <ol class="divide-y divide-[var(--color-bordure)]">
        <li
          v-for="jour in jours"
          :key="jour.iso"
          class="flex items-start gap-3 py-2"
          :class="jour.aujourdhui ? 'font-medium' : ''"
        >
          <div class="w-20 shrink-0 text-sm" :class="jour.aujourdhui ? 'text-[var(--color-accent)]' : 'text-[var(--color-doux)]'">
            {{ jourSemaine(jour.iso).slice(0, 3) }} {{ dateCourte(jour.iso) }}
          </div>
          <div v-if="!jour.seances.length" class="text-sm text-[var(--color-doux)]">repos</div>
          <div v-else class="flex flex-wrap items-center gap-2">
            <template v-for="seance in jour.seances" :key="seance.id">
              <EtiquetteType :type="seance.type" compact />
              <span class="text-sm">{{ seance.titre }}</span>
              <PastilleStatut :statut="seance.statut" />
            </template>
          </div>
        </li>
      </ol>
    </CarteBase>

    <CarteBase
      v-if="aTrancher.length"
      titre="À clarifier"
      sous-titre="Ces séances sont passées sans être marquées. Dis à ton coach ce qu'il en est."
    >
      <div class="space-y-3">
        <CarteSeance
          v-for="seance in aTrancher"
          :key="seance.id"
          :seance="seance"
          @statut="(s) => trancher(seance, s)"
          @ouvrir="ouvrirActivite(seance)"
        />
      </div>
    </CarteBase>
  </div>
</template>
