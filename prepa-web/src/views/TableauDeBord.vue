<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BandeauCycle from '@/components/cycle/BandeauCycle.vue'
import FicheSeance from '@/components/cycle/FicheSeance.vue'
import LigneAConfirmer from '@/components/cycle/LigneAConfirmer.vue'
import SeanceDuJour from '@/components/cycle/SeanceDuJour.vue'
import Semainier from '@/components/cycle/Semainier.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import { useEntrainement } from '@/stores/entrainement'
import { dateCourte, joursDepuis, km } from '@/composables/useFormat'
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

/** Les séances de la semaine affichée : tenues sur prévues, les annulées mises de côté. */
const seancesSemaine = computed(() => {
  const seances = (semaine.value?.seances ?? []).filter(
    (s) => s.type !== 'REPOS' && s.statut !== 'ANNULEE',
  )
  return {
    faites: seances.filter((s) => s.statut === 'REALISEE' || s.statut === 'ANALYSEE').length,
    total: seances.length,
  }
})

const partDeLaCible = computed(() => {
  const cible = semaine.value?.volumeCibleKm
  return cible ? Math.min(100, (volumeSemaine.value / cible) * 100) : 0
})

/**
 * Les phases d'une préparation, en un mot.
 *
 * <p>« Spécifique » ne veut rien dire pour qui découvre l'application, mais le propos de la
 * phase — « travailler l'allure de course » — n'a pas sa place à côté de la séance du jour :
 * c'est une explication, pas une consigne. Le mot seul situe ; l'explication vit dans la
 * méthodologie.
 */
const PHASES: Record<string, string> = {
  BASE: 'Fondation',
  DEVELOPPEMENT: 'Développement',
  SPECIFIQUE: 'Spécifique',
  AFFUTAGE: 'Affûtage',
  DECHARGE: 'Décharge',
  LIBRE: 'Entretien',
  REPRISE: 'Reprise',
}

const phase = computed(() => (semaine.value?.bloc ? PHASES[semaine.value.bloc] : null))

/** Avancement du cycle : ce qui est derrière, ce qui reste. */
const avancement = computed(() => {
  const seances = entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.type !== 'REPOS')
  const faites = seances.filter((s) => s.statut === 'REALISEE' || s.statut === 'ANALYSEE').length
  const restantes = seances.filter((s) => s.statut === 'A_VENIR').length
  return { faites, restantes }
})

/**
 * Un jour sans séance n'est pas un écran vide : dire quand tombe la suivante évite d'aller
 * la chercher dans le semainier.
 */
const prochaine = computed(() => {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  return entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.date > aujourdhui && s.statut === 'A_VENIR' && s.type !== 'REPOS')
    .sort((a, b) => a.date.localeCompare(b.date))[0]
})

const quandProchaine = computed(() => {
  if (!prochaine.value) return ''
  const jours = joursDepuis(prochaine.value.date)
  return jours === 1 ? 'Demain' : `Dans ${jours} jours`
})

/**
 * Séances passées que rien n'est venu renseigner : une sortie faite sans montre, ou pas faite.
 * Celles enregistrées par la montre sont déjà comptées et n'apparaissent pas ici.
 */
const aClarifier = computed(() => {
  // Rien a confirmer chez quelqu'un d'autre : ce bloc demande une reponse a l'athlete.
  if (entrainement.lectureSeule) return []
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
    message="Ton coach n'a pas encore ouvert de cycle."
  />

  <div v-else-if="cycle" class="space-y-6">
    <BandeauCycle :cycle="cycle" :semaine-courante="entrainement.semaineCourante?.numero" />

    <!--
      La séance du jour passe devant tout le reste. C'est la question qu'on se pose en ouvrant
      l'application ; l'avancement, lui, attend le bas de l'écran.
    -->
    <SeanceDuJour
      v-for="seance in entrainement.seancesDuJour"
      :key="seance.id"
      :seance="seance"
      :lecture-seule="entrainement.lectureSeule"
      @ouvrir="ouvrirFiche(seance)"
      @ouvrir-activite="ouvrirActivite(seance)"
      @statut="(s) => trancher(seance, s)"
    />

    <!-- Un jour sans séance se dit en deux mots, pas en deux phrases. -->
    <section
      v-if="!entrainement.seancesDuJour.length"
      class="rounded-2xl border border-[var(--color-bordure)] bg-[var(--color-surface)] p-5"
    >
      <h2 class="text-2xl font-semibold">Repos</h2>
      <p v-if="prochaine" class="mt-1 text-sm text-[var(--color-doux)]">
        {{ quandProchaine }} · {{ prochaine.titre }}
      </p>
    </section>

    <!-- La semaine : où l'on en est, ce qu'elle vise, et les sept jours. -->
    <section v-if="semaine">
      <div class="mb-2 flex items-baseline gap-2">
        <h2 class="font-semibold">
          {{ estLaSemaineCourante ? 'Cette semaine' : `Semaine ${semaine.numero}` }}
        </h2>
        <p class="min-w-0 truncate text-sm text-[var(--color-doux)]">
          <template v-if="phase">S{{ semaine.numero }} · {{ phase }} · </template>
          {{ dateCourte(semaine.dateDebut) }} → {{ dateCourte(entrainement.finDe(semaine)) }}
        </p>

        <div class="ml-auto flex shrink-0 items-center gap-1">
          <button
            class="cursor-pointer rounded-md px-2 py-1 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)] disabled:opacity-30"
            :disabled="position <= 0"
            title="Semaine précédente"
            @click="deplacer(-1)"
          >
            ◀
          </button>
          <button
            class="cursor-pointer rounded-md px-2 py-1 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)] disabled:opacity-30"
            :disabled="position >= entrainement.semaines.length - 1"
            title="Semaine suivante"
            @click="deplacer(1)"
          >
            ▶
          </button>
        </div>
      </div>

      <!--
        Ce que la semaine a déjà donné, en deux compteurs : les séances tenues et les
        kilomètres courus, chacun contre ce qui était visé. La barre sous eux dit la même
        chose sans chiffre — c'est elle qu'on lit au premier regard.
      -->
      <div class="mb-3">
        <div class="tabulaire flex flex-wrap gap-2 text-sm">
          <span class="rounded-full bg-[var(--color-appui)] px-2.5 py-0.5">
            {{ seancesSemaine.faites }}/{{ seancesSemaine.total }} séances
          </span>
          <span class="rounded-full bg-[var(--color-appui)] px-2.5 py-0.5">
            {{ km(volumeSemaine, 0) }} / {{ km(semaine.volumeCibleKm, 0) }}
          </span>
        </div>
        <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-[var(--color-appui)]">
          <div
            class="h-full rounded-full transition-[width]"
            :class="partDeLaCible >= 90 ? 'bg-[var(--color-succes)]' : 'bg-[var(--color-accent)]'"
            :style="{ width: `${partDeLaCible}%` }"
          />
        </div>
      </div>

      <p
        v-if="!semaine.detaillee"
        class="rounded-lg bg-[var(--color-appui)] px-3 py-2 text-sm text-[var(--color-doux)]"
      >
        Pas encore détaillée.
        <template v-if="semaine.nbQualiteCible">
          {{ semaine.nbQualiteCible }} séance<template v-if="semaine.nbQualiteCible > 1">s</template>
          de qualité visée<template v-if="semaine.nbQualiteCible > 1">s</template>.
        </template>
      </p>

      <Semainier v-else :semaine="semaine" @ouvrir="ouvrirFiche" />

    </section>

    <!-- Une question fermée, posée en une ligne par séance. -->
    <section v-if="aClarifier.length">
      <h2 class="mb-1 text-xs font-semibold tracking-wide uppercase text-[var(--color-doux)]">
        À confirmer
      </h2>
      <div class="divide-y divide-[var(--color-bordure)]">
        <LigneAConfirmer
          v-for="seance in aClarifier"
          :key="seance.id"
          :seance="seance"
          @statut="(s) => trancher(seance, s)"
          @ouvrir="ouvrirFiche(seance)"
        />
      </div>
    </section>

    <!--
      Où en est la préparation. Utile, jamais urgent : ces chiffres ne changent pas ce qu'on
      fait ce soir. Une ligne discrète suffit — le détail vit dans l'onglet Stats.
    -->
    <p class="tabulaire text-sm text-[var(--color-doux)]">
      {{ avancement.faites }} séances faites · {{ avancement.restantes }} à venir
      <template v-if="entrainement.assiduite.pourcentage !== null">
        · {{ Math.round(entrainement.assiduite.pourcentage) }} % tenues
      </template>
    </p>

    <FicheSeance
      :seance="seanceOuverte"
      :lecture-seule="entrainement.lectureSeule"
      @fermer="seanceOuverte = null"
      @statut="trancherDepuisLaFiche"
      @ouvrir-activite="seanceOuverte && ouvrirActivite(seanceOuverte)"
    />
  </div>
</template>
