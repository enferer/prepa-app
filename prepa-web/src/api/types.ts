/**
 * Contrat de l'API, decrit cote client.
 *
 * <p>Ces types suivent les DTOs du serveur. Ils sont ecrits a la main plutot que generes :
 * le contrat est restreint et stable, et une generation automatique ajouterait une etape de
 * construction pour un benefice mince.
 */

export type TypeSeance =
  | 'EF' | 'SL' | 'SEUIL' | 'VMA' | 'AM' | 'COTES' | 'RENFO' | 'COURSE' | 'CROSS' | 'REPOS'

/**
 * Cycle de vie d'une seance.
 *
 * `REALISEE` et `NON_REALISEE` sont des constats poses automatiquement — une sortie est
 * comptee des que la montre l'a transmise. `ANALYSEE` est le jugement du coach.
 */
export type StatutSeance =
  | 'A_VENIR' | 'REALISEE' | 'NON_REALISEE' | 'ANALYSEE' | 'DEPLACEE' | 'ANNULEE'

export type TypeCycle = 'PREPA' | 'LIBRE'

export type StatutCycle = 'PLANIFIE' | 'ACTIF' | 'TERMINE' | 'ABANDONNE'

export type TypeActivite =
  | 'RUN' | 'TRAIL' | 'TREADMILL' | 'BIKE' | 'SWIM' | 'STRENGTH' | 'HIKE' | 'OTHER'

export type IntensiteTour =
  | 'WARMUP' | 'ACTIVE' | 'INTERVAL' | 'REST' | 'RECOVERY' | 'COOLDOWN' | 'UNKNOWN'

export type BlocEntrainement =
  | 'BASE' | 'DEVELOPPEMENT' | 'SPECIFIQUE' | 'AFFUTAGE' | 'DECHARGE' | 'LIBRE' | 'REPRISE'

export interface Athlete {
  id: string
  /** Absent des athletes qu'on ne fait que consulter : le menu n'a besoin que du nom. */
  email?: string
  displayName: string
  timezone: string
  locale: string
  role: 'ATHLETE' | 'ADMIN'
  garminDisplayName?: string
}

export interface AllureCible {
  secKm?: number
  affichage?: string
  note?: string
}

export interface Cycle {
  id: string
  slug: string
  nom: string
  type: TypeCycle
  statut: StatutCycle
  dateDebut: string
  dateFin: string
  courseNom?: string
  courseDate?: string
  courseDistanceM?: number
  chronoViseSec?: number
  joursAvantCourse?: number
  ligneDirectrice?: string
  ligneDirectriceType?: string
  horizonSemaines?: number
  alluresCibles: Record<string, AllureCible>
  bilan?: string
  nbSemaines: number
}

/**
 * Ce que fait un morceau de seance, et donc la couleur qu'il porte. Le serveur le deduit de
 * la description ecrite par le coach — voir StructureSeance cote API.
 */
export type RoleBloc =
  | 'ECHAUFFEMENT' | 'ENDURANCE' | 'EFFORT' | 'LIGNES' | 'RECUPERATION' | 'RETOUR_AU_CALME'

/**
 * Un bloc du deroule d'une seance prevue.
 *
 * `dureeEstimeeSec` couvre les repetitions et les recuperations qui les separent : c'est elle
 * qui donne sa largeur au bloc sur le dessin.
 */
export interface BlocPrevu {
  role: RoleBloc
  repetitions: number
  distanceKm?: number
  dureeSec?: number
  allureSecKm?: number
  recupSec?: number
  dureeEstimeeSec: number
  libelle: string
}

export interface Seance {
  id: string
  weekId: string
  date: string
  ordre: number
  type: TypeSeance
  titre: string
  description?: string
  statut: StatutSeance
  alluresTexte?: string
  distanceCibleKm?: number
  dureeCibleMin?: number
  focus?: string
  commentaireCoach?: string
  commentaireAthlete?: string
  activityId?: string
  rapprochement: 'AUTO' | 'MANUEL' | 'AUCUN'
  /** Le deroule de la seance, pret a dessiner. Vide pour ce qui ne se court pas. */
  structure: BlocPrevu[]
  /** Vrai quand le coach a pose lui-meme le deroule, faux quand il a ete relu dans la consigne. */
  structureSaisie: boolean
}

export interface Semaine {
  id: string
  numero: number
  dateDebut: string
  bloc?: BlocEntrainement
  /** Ce que la semaine vise : l'intention du coach, posee a la conception du plan. */
  volumeCibleKm: number
  /** Ce que les seances totalisent, hors seances annulees. Peut diverger de la cible. */
  volumePlanifieKm: number
  nbQualiteCible?: number
  deniveleCibleM?: number
  detaillee: boolean
  note?: string
  seances: Seance[]
}

export interface CycleDetail {
  cycle: Cycle
  semaines: Semaine[]
}

export interface ActivityResume {
  id: string
  garminActivityId?: number
  type: TypeActivite
  titre?: string
  startedAt: string
  date: string
  dureeSec: number
  distanceM?: number
  allureMoySecKm?: number
  fcMoy?: number
  denivelePosM?: number
  aDetail: boolean
  rpe?: number
}

export interface Tour {
  index: number
  distanceM?: number
  dureeSec: number
  allureSecKm?: number
  gapSecKm?: number
  fcMoy?: number
  fcMax?: number
  cadenceMoy?: number
  denivelePosM?: number
  deniveleNegM?: number
  deniveleNetM: number
  intensite: IntensiteTour
}

export interface Bloc {
  intensite: IntensiteTour
  premierTour: number
  nbTours: number
  distanceM: number
  dureeSec: number
  allureSecKm?: number
  fcMoy?: number
  fcDebut?: number
  fcFin?: number
  denivelePosM: number
  deniveleNegM: number
  alluresParTour: (number | null)[]
}

export interface Meteo {
  temperatureC?: number
  ressentiC?: number
  humidite?: number
  ventKmh?: number
  description?: string
}

export interface ZoneFc {
  zone: number
  secondes: number
  borneBasse?: number
}

export interface ActivityDetail {
  resume: ActivityResume
  dureeMouvementSec?: number
  meilleureAllureSecKm?: number
  gapMoySecKm?: number
  fcMax?: number
  fcMin?: number
  cadenceMoy?: number
  deniveleNegM?: number
  altitudeMinM?: number
  altitudeMaxM?: number
  calories?: number
  teAerobie?: number
  teAnaerobie?: number
  teLabel?: string
  chargeEntrainement?: number
  vo2max?: number
  lieu?: string
  ressenti?: string
  meteo?: Meteo
  zonesFc?: ZoneFc[]
  tours: Tour[]
  structuree: boolean
  blocs: Bloc[]
}

export interface EntreeJournal {
  id: string
  date: string
  contenu: string
  humeur?: number
  fatigue?: number
  sommeilH?: number
  douleur: boolean
}

export interface SemaineVolume {
  lundi: string
  anneeIso: number
  numeroIso: number
  km: number
  nbCourses: number
  deniveleM?: number
}

export interface Synthese {
  fenetre?: { debut: string; fin: string; nbCourses: number; libelle: string }
  volumeHebdo: SemaineVolume[]
  volume: {
    nbCourses: number
    kmTotal: number
    semainesActives: number
    kmParSemaineMoyen: number
    kmParSemaineMin: number
    kmParSemaineMax: number
    seancesParSemaine: number
  }
  allureEf: { allureMoySecKm?: number; nbSeances: number; seuilFcRetenu: number; seuilRelatif: boolean }
  effortsRapides: EffortNotable[]
  plusLonguesSorties: EffortNotable[]
  plusLongueDeToujours?: EffortNotable
  records: RecordEstime[]
  tendanceFc?: { fcMoyRecente?: number; fcMoyPrecedente?: number; ecart?: number; lecture: string }
  avantPendantCycle?: Comparaison
  alluresParType: AllureParType[]
  repartition?: RepartitionIntensite
}

/** Allure réellement tenue sur un type de séance, face à l'allure visée. */
export interface AllureParType {
  type: TypeSeance
  libelle: string
  nbSeances: number
  allureReelleSecKm?: number
  allureCibleSecKm?: number
  ecartSecKm?: number
  surLesBlocsDEffort: boolean
  /** Sorties laissees de cote faute de pouvoir neutraliser leur relief. */
  nbEcartees: number
  /** Au moins une sortie retenue l'a ete par son allure corrigee de la pente. */
  surAllureCorrigee: boolean
}

/** Part du volume couru facile, face à la part couru en intensité. */
export interface RepartitionIntensite {
  kmFacile: number
  kmIntensite: number
  partFacilePct: number
  partIntensitePct: number
  lecture: string
}

export interface EffortNotable {
  activityId: string
  date: string
  type: TypeActivite
  titre?: string
  distanceKm: number
  allureSecKm?: number
  fcMoy?: number
  denivelePosM?: number
}

/**
 * D'ou vient un meilleur temps.
 *
 * `SORTIE` : la sortie fait la distance, le chrono est celui de la course elle-meme.
 * `TOURS` : meilleur segment retrouve en faisant glisser une fenetre sur les tours.
 * `ESTIMATION` : extrapole de l'allure moyenne d'une sortie plus longue — un ordre de grandeur.
 */
export type Provenance = 'SORTIE' | 'TOURS' | 'ESTIMATION'

export interface RecordEstime {
  distanceM: number
  tempsSec?: number
  allureSecKm?: number
  date: string
  activityId: string
  provenance: Provenance
  deniveleNetM?: number
}

export interface Comparaison {
  avant: Periode
  pendant: Periode
  deltas: Delta[]
}

export interface Periode {
  libelle: string
  debut: string
  fin: string
  volume: Synthese['volume']
  allureMoySecKm?: number
  fcMoy?: number
}

export interface Delta {
  mesure: string
  avant: number
  pendant: number
  variationPct: number
  amelioration: boolean
}

export interface ProfilSportif {
  fcMax?: number
  fcRepos?: number
  vmaKmh?: number
  volumeHabituelKm?: number
  joursDisponibles: string[]
  renfoActif: boolean
  renfoFrequence?: number
  renfoMateriel: string[]
  renfoFocus?: string
  notes?: string
  seuilFcEnduranceFondamentale: number
}

export interface RecordPersonnel {
  id: string
  distanceM: number
  tempsSec: number
  date: string
  contexte?: string
  source: string
  allureSecKm: number
}

export interface Blessure {
  id: string
  zone: string
  statut: 'ACTIVE' | 'SURVEILLANCE' | 'RESOLUE'
  palier?: number
  consignes?: string
  debut: string
  fin?: string
}

export interface Contrainte {
  id: string
  type: string
  debut?: string
  fin?: string
  detail: string
}

export interface SeanceSignature {
  id: string
  nom: string
  typeSeance: TypeSeance
  description?: string
  distanceKm?: number
  frequenceSouhaitee?: string
  contexte?: string
  actif: boolean
}

export interface BilanHebdo {
  id: string
  dateDebut: string
  bilan: string
  pointsAttention?: string
  changements?: Record<string, unknown>[]
  consignes?: string
}

export interface ErreurApi {
  error: { code: string; message: string; details?: Record<string, unknown> }
}

// --- Exploitation de la synchronisation Garmin -------------------------------
// Reserve au role ADMIN : ces formes ne viennent que de /api/v1/admin/garmin.

export type StatutSync = 'EN_COURS' | 'OK' | 'AUTH_ERROR' | 'IDENTITE_KO' | 'MFA_REQUISE' | 'ERREUR'

export type DeclencheurSync = 'PLANIFIE' | 'DEMANDE' | 'MANUEL'

/** Un compte Garmin relie, et ou en est sa synchronisation. */
export interface CompteGarmin {
  athleteId: string
  athlete: string
  garminDisplayName?: string
  derniereSync?: string
  dernierStatut?: StatutSync
  dernierMessage?: string
  syncDemande: boolean
}

export interface EtatSyncGlobal {
  enCours: boolean
  athleteEnCours?: string
  comptes: CompteGarmin[]
}

/** Une trace de passage, telle que l'historique la conserve. */
export interface PassageSync {
  id: string
  athleteId: string
  athlete?: string
  demarreA: string
  termineA?: string
  dureeSec?: number
  declencheur: DeclencheurSync
  demandePar?: string
  fenetreDu?: string
  fenetreAu?: string
  statut: StatutSync
  message?: string
  recues?: number
  importees?: number
  misesAJour?: number
  doublons?: number
}
