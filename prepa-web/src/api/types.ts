/**
 * Contrat de l'API, decrit cote client.
 *
 * <p>Ces types suivent les DTOs du serveur. Ils sont ecrits a la main plutot que generes :
 * le contrat est restreint et stable, et une generation automatique ajouterait une etape de
 * construction pour un benefice mince.
 */

export type TypeSeance =
  | 'EF' | 'SL' | 'SEUIL' | 'VMA' | 'AM' | 'COTES' | 'RENFO' | 'COURSE' | 'CROSS' | 'REPOS'

export type StatutSeance = 'A_VENIR' | 'VALIDEE' | 'MANQUEE' | 'DEPLACEE' | 'ANNULEE'

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
  email: string
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
}

export interface Semaine {
  id: string
  numero: number
  dateDebut: string
  bloc?: BlocEntrainement
  volumeCibleKm: number
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

export interface RecordEstime {
  distanceM: number
  tempsSec?: number
  allureSecKm?: number
  date: string
  activityId: string
  surTours: boolean
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
