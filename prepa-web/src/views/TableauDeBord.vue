<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BandeauCycle from '@/components/cycle/BandeauCycle.vue'
import CarteSeance from '@/components/cycle/CarteSeance.vue'
import FicheSeance from '@/components/cycle/FicheSeance.vue'
import Semainier from '@/components/cycle/Semainier.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { useEntrainement } from '@/stores/entrainement'
import { dateCourte, km, pourcentage } from '@/composables/useFormat'
import type { Seance, StatutSeance } from '@/api/types'

const entrainement = useEntrainement()
const router = useRouter()

const cycle = computed(() => entrainement.cycleActif?.cycle ?? null)

/** Semaine affichée : celle en cours au chargement, puis celle qu'on choisit avec les flèches. */
const semaineAffichee = ref<string | null>(null)

watch(
  () => entrainement.semaineCourante?.id,
  (id) => {
    if (id && !semaineAffichee.value) semaineAffichee.value = id
  },
  { immediate: true },
)

const semaine = computed(
  () =>
    entrainement.semaines.find((s) => s.id === semaineAffichee.value)
      ?? entrainement.semaineCourante,
)

const position = computed(() => entrainement.semaines.findIndex((s) => s.id === semaine.value?.id))

function deplacer(pas: number) {
  const suivante = entrainement.semaines[position.value + pas]
  if (suivante) semaineAffichee.value = suivante.id
}

const estLaSemaineCourante = computed(
  () => semaine.value?.id === entrainement.semaineCourante?.id,
)

const volumeSemaine = computed(() => (semaine.value ? entrainement.volumeRealise(semaine.value) : 0))

/**
 * Les phases d'une préparation, en clair. « Spécifique » ne veut rien dire pour qui découvre
 * l'application ; ce que la semaine cherche à produire, si.
 */
const PHASES: Record<string, { nom: string; propos: string }> = {
  BASE: { nom: 'Fondation', propos: 'construire le socle aérobie' },
  DEVELOPPEMENT: { nom: 'Développement', propos: 'monter le volume et l’intensité' },
  SPECIFIQUE: { nom: 'Spécifique', propos: 'travailler l’allure de course' },
  AFFUTAGE: { nom: 'Affûtage', propos: 'arriver frais le jour J' },
  DECHARGE: { nom: 'Décharge', propos: 'absorber la charge, récupérer' },
  LIBRE: { nom: 'Entretien', propos: 'suivre la ligne directrice' },
  REPRISE: { nom: 'Reprise', propos: 'revenir progressivement' },
}

const phase = computed(() => (semaine.value?.bloc ? PHASES[semaine.value.bloc] : null))

/** Avancement du cycle : ce qui est derrière, ce qui reste. */
const avancement = computed(() => {
  const seances = entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.type !== 'REPOS')
  const faites = seances.filter((s) => s.statut === 'REALISEE' || s.statut === 'ANALYSEE').length
  const restantes = seances.filter((s) => s.statut === 'A_VENIR').length
  return { faites, restantes, total: seances.length }
})

/**
 * Séances passées que rien n'est venu renseigner : une sortie faite sans montre, ou pas faite.
 * Celles enregistrées par la montre sont déjà comptées et n'apparaissent pas ici.
 */
const aClarifier = computed(() => {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  return entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.date < aujourdhui && s.statut === 'A_VENIR' && s.type !== 'REPOS')
    .slice(-3)
})

async function trancher(seance: Seance, statut: StatutSeance) {
  await entrainement.changerStatut(seance, statut)
}

/**
 * Ouvre la fiche d'une séance. Cliquer une séance à venir montre la consigne ; cliquer une
 * séance faite donne le choix d'aller voir ce qui a réellement été couru.
 */
const seanceOuverte = ref<Seance | null>(null)

function ouvrirFiche(seance: Seance) {
  seanceOuverte.value = seance
}

function ouvrirActivite(seance: Seance) {
  if (seance.activityId) router.push({ name: 'seance', params: { id: seance.activityId } })
}

async function trancherDepuisLaFiche(statut: StatutSeance) {
  if (!seanceOuverte.value) return
  await entrainement.changerStatut(seanceOuverte.value, statut)
  seanceOuverte.value = null
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
    <BandeauCycle :cycle="cycle" :semaine-courante="entrainement.semaineCourante?.numero" />

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <TuileChiffre
        libelle="Séances faites"
        :valeur="String(avancement.faites)"
        :detail="`${avancement.restantes} encore à faire`"
      />
      <TuileChiffre
        libelle="Séances tenues"
        :valeur="entrainement.assiduite.pourcentage !== null
          ? pourcentage(entrainement.assiduite.pourcentage)
          : '—'"
        :detail="`${entrainement.assiduite.manquees} non faite${entrainement.assiduite.manquees > 1 ? 's' : ''}`"
      />
      <TuileChiffre
        libelle="Cette semaine"
        :valeur="km(volumeSemaine)"
        :detail="semaine ? `objectif ${km(semaine.volumeCibleKm)}` : undefined"
      />
      <TuileChiffre
        libelle="Phase"
        :valeur="phase?.nom ?? '—'"
        :detail="phase?.propos"
      />
    </div>

    <CarteBase v-if="entrainement.seancesDuJour.length" titre="Ta séance du jour">
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

    <CarteBase v-else titre="Ta séance du jour">
      <p class="text-sm text-[var(--color-doux)]">
        Rien de prévu aujourd'hui. Repos, ou sortie libre si l'envie est là.
      </p>
    </CarteBase>

    <CarteBase v-if="semaine">
      <template #entete>
        <div class="flex items-center gap-1">
          <button
            class="rounded-md border border-[var(--color-bordure)] px-2 py-1 text-sm disabled:opacity-40"
            :disabled="position <= 0"
            title="Semaine précédente"
            @click="deplacer(-1)"
          >
            ◀
          </button>
          <button
            class="rounded-md border border-[var(--color-bordure)] px-2 py-1 text-sm disabled:opacity-40"
            :disabled="position >= entrainement.semaines.length - 1"
            title="Semaine suivante"
            @click="deplacer(1)"
          >
            ▶
          </button>
        </div>
      </template>

      <div class="mb-3">
        <h2 class="font-semibold">
          Semaine {{ semaine.numero }}
          <span v-if="estLaSemaineCourante" class="text-[var(--color-accent)]">— en cours</span>
        </h2>
        <p class="text-sm text-[var(--color-doux)]">
          du {{ dateCourte(semaine.dateDebut) }} au {{ dateCourte(entrainement.finDe(semaine)) }}
          <template v-if="phase"> · {{ phase.nom.toLowerCase() }}, {{ phase.propos }}</template>
        </p>
      </div>

      <p
        v-if="!semaine.detaillee"
        class="mb-3 rounded-lg bg-[var(--color-appui)] px-3 py-2 text-sm text-[var(--color-doux)]"
      >
        Semaine encore en objectifs seuls — ton coach la détaillera au prochain point.
        Vise {{ km(semaine.volumeCibleKm) }}<template v-if="semaine.nbQualiteCible">
          et {{ semaine.nbQualiteCible }} séance<template v-if="semaine.nbQualiteCible > 1">s</template>
          de qualité</template>.
      </p>

      <Semainier
        v-else
        :semaine="semaine"
        :volume-realise="entrainement.volumeRealise(semaine)"
        @ouvrir="ouvrirFiche"
      />

      <p v-if="semaine.note" class="mt-3 rounded-lg bg-[var(--color-accent-fond)] px-3 py-2 text-sm">
        <span class="font-medium text-[var(--color-accent)]">Note de ton coach — </span>{{ semaine.note }}
      </p>
    </CarteBase>

    <CarteBase
      v-if="aClarifier.length"
      titre="À confirmer"
      sous-titre="Ces séances sont passées sans qu'aucune activité ne leur corresponde. Si tu les as faites sans montre, dis-le."
    >
      <div class="space-y-3">
        <CarteSeance
          v-for="seance in aClarifier"
          :key="seance.id"
          :seance="seance"
          @statut="(s) => trancher(seance, s)"
          @ouvrir="ouvrirActivite(seance)"
        />
      </div>
    </CarteBase>
    <FicheSeance
      :seance="seanceOuverte"
      @fermer="seanceOuverte = null"
      @statut="trancherDepuisLaFiche"
      @ouvrir-activite="seanceOuverte && ouvrirActivite(seanceOuverte)"
    />
  </div>
</template>
