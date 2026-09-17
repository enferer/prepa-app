<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import CalendrierAnnuel from '@/components/charts/CalendrierAnnuel.vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import TuileChiffre from '@/components/ui/TuileChiffre.vue'
import { useEntrainement } from '@/stores/entrainement'
import { chrono, dateLongue, km } from '@/composables/useFormat'

const entrainement = useEntrainement()
const router = useRouter()

const MOIS = [
  'janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
  'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.',
]

const LIGNES: Record<string, string> = {
  MAINTIEN_CHARGE: 'maintien de charge',
  VO2MAX: 'VO2max',
  ENDURANCE_FONDAMENTALE: 'endurance',
  TRAIL_DENIVELE: 'trail',
  VITESSE_COURTE: 'vitesse',
  REPRISE_POST_COURSE: 'reprise',
  RETOUR_BLESSURE: 'retour de blessure',
  AUTRE: 'libre',
}

const activitesCourse = computed(() =>
  entrainement.activites.filter((a) => ['RUN', 'TRAIL', 'TREADMILL'].includes(a.type)),
)

/** Volume par mois sur les douze derniers, y compris les mois vides. */
const volumeMensuel = computed(() => {
  const table = new Map<string, number>()
  const maintenant = new Date()
  for (let i = 11; i >= 0; i--) {
    const mois = new Date(maintenant.getFullYear(), maintenant.getMonth() - i, 1)
    table.set(`${mois.getFullYear()}-${String(mois.getMonth() + 1).padStart(2, '0')}`, 0)
  }
  for (const activite of activitesCourse.value) {
    const cle = activite.date.slice(0, 7)
    if (table.has(cle)) table.set(cle, table.get(cle)! + (activite.distanceM ?? 0) / 1000)
  }
  return [...table].map(([cle, valeur]) => ({
    cle,
    libelle: MOIS[Number(cle.slice(5)) - 1],
    annee: cle.slice(0, 4),
    km: valeur,
  }))
})

const maxMensuel = computed(() => Math.max(10, ...volumeMensuel.value.map((m) => m.km)))

/** Ce que l'année représente, en un chiffre chacun. */
const bilanAnnee = computed(() => {
  const debutAnnee = `${new Date().getFullYear()}-01-01`
  const cetteAnnee = activitesCourse.value.filter((a) => a.date >= debutAnnee)
  const kmTotal = cetteAnnee.reduce((t, a) => t + (a.distanceM ?? 0) / 1000, 0)
  const denivele = cetteAnnee.reduce((t, a) => t + (a.denivelePosM ?? 0), 0)
  const heures = cetteAnnee.reduce((t, a) => t + a.dureeSec, 0) / 3600
  return { sorties: cetteAnnee.length, kmTotal, denivele, heures }
})

/** Les cycles, du plus récent au plus ancien, avec ce qu'ils ont porté. */
const cycles = computed(() =>
  entrainement.cycles.map((cycle) => {
    const sorties = activitesCourse.value.filter(
      (a) => a.date >= cycle.dateDebut && a.date <= cycle.dateFin,
    )
    return {
      ...cycle,
      km: sorties.reduce((t, a) => t + (a.distanceM ?? 0) / 1000, 0),
      sorties: sorties.length,
      intention:
        cycle.type === 'PREPA'
          ? `${cycle.courseNom ?? 'course'}${cycle.chronoViseSec ? ` en ${chrono(cycle.chronoViseSec)}` : ''}`
          : LIGNES[cycle.ligneDirectriceType ?? 'AUTRE'],
    }
  }),
)

/** Ce qui arrive : la course, et les semaines clés d'ici là. */
const echeances = computed(() => {
  const aujourdhui = new Date().toISOString().slice(0, 10)
  const liste: { date: string; libelle: string; detail: string }[] = []

  const cycle = entrainement.cycleActif?.cycle
  if (cycle?.courseDate && cycle.courseDate >= aujourdhui) {
    liste.push({
      date: cycle.courseDate,
      libelle: cycle.courseNom ?? 'Course',
      detail: cycle.chronoViseSec ? `objectif ${chrono(cycle.chronoViseSec)}` : 'jour J',
    })
  }

  // Les plus longues sorties à venir : ce sont elles qui structurent les semaines.
  entrainement.semaines
    .flatMap((s) => s.seances)
    .filter((s) => s.date >= aujourdhui && s.type === 'SL' && s.distanceCibleKm)
    .sort((a, b) => Number(b.distanceCibleKm) - Number(a.distanceCibleKm))
    .slice(0, 3)
    .forEach((s) =>
      liste.push({ date: s.date, libelle: s.titre, detail: km(s.distanceCibleKm) }),
    )

  return liste.sort((a, b) => a.date.localeCompare(b.date)).slice(0, 5)
})

function ouvrirJour(date: string) {
  const activite = entrainement.activites.find((a) => a.date === date)
  if (activite) router.push({ name: 'seance', params: { id: activite.id } })
}
</script>

<template>
  <EtatVide
    v-if="!entrainement.activites.length"
    titre="Rien à montrer pour l'instant"
    message="Cette vue prend son sens après quelques semaines d'entraînement."
  />

  <div v-else class="space-y-5">
    <div>
      <h1 class="text-lg font-semibold">Ta saison</h1>
      <p class="text-sm text-[var(--color-doux)]">
        Ce que tu as fait, et ce qui arrive. Pour savoir si tu progresses, va voir Stats.
      </p>
    </div>

    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <TuileChiffre libelle="Cette année" :valeur="km(bilanAnnee.kmTotal, 0)" :detail="`${bilanAnnee.sorties} sorties`" />
      <TuileChiffre libelle="Temps de course" :valeur="`${Math.round(bilanAnnee.heures)} h`" />
      <TuileChiffre libelle="Dénivelé" :valeur="`${Math.round(bilanAnnee.denivele)} m`" />
      <TuileChiffre
        libelle="Cycles"
        :valeur="String(cycles.length)"
        :detail="cycles.length ? cycles[0].nom : undefined"
      />
    </div>

    <CarteBase
      titre="Ton année, jour par jour"
      sous-titre="Chaque case est un jour ; plus elle est foncée, plus tu as couru. Clique pour ouvrir la séance."
    >
      <CalendrierAnnuel :activites="entrainement.activites" @jour="ouvrirJour" />
    </CarteBase>

    <CarteBase titre="Volume mensuel">
      <div class="flex items-end gap-1.5" style="height: 140px">
        <div
          v-for="mois in volumeMensuel"
          :key="mois.cle"
          class="flex flex-1 flex-col items-center justify-end gap-1"
          :title="`${mois.libelle} ${mois.annee} — ${mois.km.toFixed(0)} km`"
        >
          <span class="tabulaire text-xs text-[var(--color-doux)]">{{ mois.km.toFixed(0) }}</span>
          <div
            class="w-full rounded-t bg-[var(--color-accent)]"
            :style="{ height: `${(mois.km / maxMensuel) * 100}px`, minHeight: mois.km ? '2px' : '0' }"
          />
          <span class="text-xs text-[var(--color-doux)]">{{ mois.libelle }}</span>
        </div>
      </div>
    </CarteBase>

    <CarteBase v-if="echeances.length" titre="Ce qui arrive">
      <ol class="divide-y divide-[var(--color-bordure)]">
        <li v-for="echeance in echeances" :key="echeance.date + echeance.libelle" class="flex gap-4 py-2 text-sm">
          <span class="w-36 shrink-0 text-[var(--color-doux)]">{{ dateLongue(echeance.date) }}</span>
          <span class="font-medium">{{ echeance.libelle }}</span>
          <span class="ml-auto text-[var(--color-doux)]">{{ echeance.detail }}</span>
        </li>
      </ol>
    </CarteBase>

    <CarteBase titre="Tes cycles" sous-titre="Ce que chaque période a porté.">
      <ol class="space-y-3">
        <li
          v-for="cycle in cycles"
          :key="cycle.id"
          class="rounded-lg border p-3"
          :class="cycle.statut === 'ACTIF'
            ? 'border-[var(--color-accent)] bg-[var(--color-accent-fond)]'
            : 'border-[var(--color-bordure)]'"
        >
          <div class="flex flex-wrap items-baseline gap-2">
            <span class="font-medium">{{ cycle.nom }}</span>
            <span class="text-xs text-[var(--color-doux)]">
              {{ cycle.type === 'PREPA' ? 'préparation' : 'cycle libre' }} · {{ cycle.intention }}
            </span>
            <span v-if="cycle.statut === 'ACTIF'" class="ml-auto text-xs text-[var(--color-accent)]">en cours</span>
          </div>
          <p class="tabulaire mt-1 text-sm text-[var(--color-doux)]">
            {{ dateLongue(cycle.dateDebut) }} → {{ dateLongue(cycle.dateFin) }}
            · {{ cycle.nbSemaines }} semaines · {{ km(cycle.km, 0) }} parcourus en {{ cycle.sorties }} sorties
          </p>
          <p v-if="cycle.bilan" class="mt-2 text-sm">{{ cycle.bilan }}</p>
        </li>
      </ol>
    </CarteBase>
  </div>
</template>
