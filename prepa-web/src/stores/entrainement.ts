import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { activitesApi, athletesApi, cyclesApi, seancesApi } from '@/api'
import { ErreurHttp } from '@/api/http'
import type {
  ActivityResume, Athlete, Cycle, CycleDetail, Seance, Semaine, StatutSeance,
} from '@/api/types'
import { useAuth } from './auth'

/**
 * Etat d'entrainement de l'athlete consulte : son cycle en cours, son plan, ses activites.
 *
 * <p>Un athlete n'a qu'un cycle actif a la fois — c'est celui qu'on garde charge. Consulter
 * un cycle termine recharge depuis l'API plutot que de maintenir un cache de tous les cycles.
 *
 * <p>L'athlete consulte n'est pas toujours celui qui est connecte : on peut regarder
 * l'entrainement d'un autre. Ce store porte donc la question « qui regarde-t-on » — d'ou
 * decoule {@code lectureSeule}, que les ecrans lisent pour retirer les gestes d'ecriture.
 * Le journal et le profil ne suivent pas ce choix : ils restent ceux de l'athlete connecte.
 */
export const useEntrainement = defineStore('entrainement', () => {
  const auth = useAuth()

  const cycleActif = ref<CycleDetail | null>(null)
  const cycles = ref<Cycle[]>([])
  const activites = ref<ActivityResume[]>([])
  const chargement = ref(false)
  const sansCycle = ref(false)
  const erreur = ref<string | null>(null)

  /** Les athletes que l'on peut consulter. Vide tant que la session n'est pas ouverte. */
  const athletes = ref<Athlete[]>([])
  /** Null quand on se regarde soi-meme — le cas de tres loin le plus frequent. */
  const consulte = ref<Athlete | null>(null)

  const athleteId = computed(() => consulte.value?.id ?? auth.athlete?.id)
  const athleteAffiche = computed(() => consulte.value ?? auth.athlete)
  const lectureSeule = computed(() => consulte.value !== null)

  const semaines = computed<Semaine[]>(() => cycleActif.value?.semaines ?? [])

  /** La semaine qui contient aujourd'hui, ou a defaut la derniere commencee. */
  const semaineCourante = computed<Semaine | null>(() => {
    const aujourdhui = new Date().toISOString().slice(0, 10)
    const courante = semaines.value.find(
      (s) => s.dateDebut <= aujourdhui && finDe(s) >= aujourdhui,
    )
    if (courante) return courante
    const passees = semaines.value.filter((s) => s.dateDebut <= aujourdhui)
    return passees.length ? passees[passees.length - 1] : (semaines.value[0] ?? null)
  })

  const seancesDuJour = computed<Seance[]>(() => {
    const aujourdhui = new Date().toISOString().slice(0, 10)
    return semaines.value.flatMap((s) => s.seances).filter((s) => s.date === aujourdhui)
  })

  /**
   * Assiduite : part des seances tranchees qui ont ete tenues.
   * Le renforcement en est exclu — il n'est pas enregistre par la montre, le compter
   * ferait chuter le chiffre pour une raison qui n'a rien a voir avec l'entrainement.
   */
  const assiduite = computed(() => {
    const concernees = semaines.value
      .flatMap((s) => s.seances)
      .filter((s) => s.type !== 'RENFO' && s.type !== 'REPOS')
    // Une séance tenue l'est, que le coach l'ait déjà commentée ou non.
    const tenues = concernees.filter((s) => s.statut === 'REALISEE' || s.statut === 'ANALYSEE').length
    const manquees = concernees.filter((s) => s.statut === 'NON_REALISEE').length
    const tranchees = tenues + manquees
    return {
      tenues,
      manquees,
      total: concernees.length,
      pourcentage: tranchees ? Math.round((tenues / tranchees) * 100) : null,
    }
  })

  /** Volume realise sur une semaine donnee, toutes courses a pied confondues. */
  function volumeRealise(semaine: Semaine): number {
    const fin = finDe(semaine)
    return activites.value
      .filter((a) => a.date >= semaine.dateDebut && a.date <= fin)
      .filter((a) => ['RUN', 'TRAIL', 'TREADMILL'].includes(a.type))
      .reduce((total, a) => total + (a.distanceM ?? 0) / 1000, 0)
  }

  /**
   * Bascule sur un autre athlete, ou revient a soi quand l'identifiant est celui du compte.
   * Le plan charge n'a plus rien a voir : on recharge tout plutot que de melanger deux plans.
   */
  async function consulter(id: string | null) {
    const cible = id && id !== auth.athlete?.id ? (athletes.value.find((a) => a.id === id) ?? null) : null
    if (cible?.id === consulte.value?.id) return
    consulte.value = cible
    await charger()
  }

  /** La liste des profils consultables ; son echec ne doit pas priver l'ecran de son plan. */
  async function chargerAthletes() {
    athletes.value = await athletesApi.lister().catch(() => [])
  }

  async function charger() {
    if (!athleteId.value) return
    chargement.value = true
    erreur.value = null
    sansCycle.value = false
    try {
      const [detail, liste, sorties] = await Promise.all([
        cyclesApi.actif(athleteId.value).catch((e) => {
          // Un athlete sans cycle actif n'est pas un cas d'erreur : c'est un etat normal,
          // juste apres une cloture par exemple.
          if (e instanceof ErreurHttp && e.statut === 404) {
            sansCycle.value = true
            return null
          }
          throw e
        }),
        cyclesApi.lister(athleteId.value),
        activitesApi.lister(athleteId.value),
      ])
      cycleActif.value = detail
      cycles.value = liste
      activites.value = sorties
    } catch (e) {
      erreur.value = e instanceof Error ? e.message : 'Chargement impossible'
    } finally {
      chargement.value = false
    }
  }

  async function changerStatut(seance: Seance, statut: StatutSeance) {
    if (lectureSeule.value) return
    const misAJour = await seancesApi.changerStatut(seance.id, statut)
    remplacerSeance(misAJour)
  }

  async function commenter(seance: Seance, commentaire: string) {
    if (lectureSeule.value) return
    remplacerSeance(await seancesApi.commenter(seance.id, commentaire))
  }

  /** Met a jour une seance en place, sans recharger tout le plan. */
  function remplacerSeance(seance: Seance) {
    const semaine = cycleActif.value?.semaines.find((s) => s.id === seance.weekId)
    if (!semaine) return
    const index = semaine.seances.findIndex((s) => s.id === seance.id)
    if (index >= 0) semaine.seances[index] = seance
  }

  function finDe(semaine: Semaine): string {
    const fin = new Date(semaine.dateDebut)
    fin.setDate(fin.getDate() + 6)
    return fin.toISOString().slice(0, 10)
  }

  return {
    cycleActif, cycles, activites, chargement, erreur, sansCycle,
    athletes, consulte, athleteId, athleteAffiche, lectureSeule,
    semaines, semaineCourante, seancesDuJour, assiduite,
    charger, chargerAthletes, consulter, changerStatut, commenter, volumeRealise, finDe,
  }
})
