import { api } from './http'
import type {
  ActivityDetail, ActivityResume, Athlete, Blessure, Contrainte, Cycle, CycleDetail,
  EntreeJournal, ProfilSportif, RecordPersonnel, Seance, SeanceSignature, StatutSeance,
  Synthese, BilanHebdo,
} from './types'

/** Appels de l'API, regroupes par domaine. */

export const authApi = {
  connexion: (email: string, motDePasse: string) =>
    api.post<{ accessToken: string; refreshToken: string; expiresInSec: number }>(
      '/auth/login', { email, motDePasse }),
  deconnexion: (refreshToken: string) => api.post<void>('/auth/logout', { refreshToken }),
  moi: () => api.get<Athlete>('/me'),
}

export const cyclesApi = {
  lister: (athleteId: string) => api.get<Cycle[]>(`/athletes/${athleteId}/cycles`),
  actif: (athleteId: string) => api.get<CycleDetail>(`/athletes/${athleteId}/cycles/actif`),
  detail: (cycleId: string) => api.get<CycleDetail>(`/cycles/${cycleId}`),
  bilan: (cycleId: string) => api.get<BilanCycle>(`/cycles/${cycleId}/summary`),
  rapports: (cycleId: string) => api.get<BilanHebdo[]>(`/cycles/${cycleId}/reports`),
}

export interface BilanCycle {
  nom: string
  type: string
  dateDebut: string
  dateFin: string
  nbSemaines: number
  nbSeances: number
  seancesValidees: number
  seancesManquees: number
  assiduitePct?: number
  volumeCibleTotalKm: number
}

export const seancesApi = {
  /** Marquer une seance faite ou manquee : c'est de la description, pas de la conception. */
  changerStatut: (seanceId: string, statut: StatutSeance) =>
    api.patch<Seance>(`/sessions/${seanceId}`, { statut }),
  commenter: (seanceId: string, commentaireAthlete: string) =>
    api.patch<Seance>(`/sessions/${seanceId}/commentaire`, { commentaireAthlete }),
  decaler: (seanceId: string, date: string) =>
    api.patch<Seance>(`/sessions/${seanceId}/commentaire`, { date }),
  lierActivite: (seanceId: string, activityId: string | null) =>
    api.post<Seance>(`/sessions/${seanceId}/link-activity`, { activityId }),
}

export const activitesApi = {
  lister: (athleteId: string, debut?: string, fin?: string) => {
    const params = debut && fin ? `?debut=${debut}&fin=${fin}` : ''
    return api.get<ActivityResume[]>(`/athletes/${athleteId}/activities${params}`)
  },
  detail: (activityId: string) => api.get<ActivityDetail>(`/activities/${activityId}`),
  ressenti: (activityId: string, rpe?: number, ressenti?: string) =>
    api.patch<ActivityResume>(`/activities/${activityId}/feedback`, { rpe, ressenti }),
}

export const journalApi = {
  lister: (athleteId: string) => api.get<EntreeJournal[]>(`/athletes/${athleteId}/journal`),
  enregistrer: (athleteId: string, entree: Partial<EntreeJournal> & { date: string; contenu: string }) =>
    api.put<EntreeJournal>(`/athletes/${athleteId}/journal`, entree),
  supprimer: (athleteId: string, entryId: string) =>
    api.delete<void>(`/athletes/${athleteId}/journal/${entryId}`),
}

export const profilApi = {
  lire: (athleteId: string) => api.get<ProfilSportif>(`/athletes/${athleteId}/profile`),
  enregistrer: (athleteId: string, profil: Partial<ProfilSportif>) =>
    api.put<ProfilSportif>(`/athletes/${athleteId}/profile`, profil),
  records: (athleteId: string) => api.get<RecordPersonnel[]>(`/athletes/${athleteId}/records`),
  ajouterRecord: (athleteId: string, record: { distanceM: number; tempsSec: number; date: string; contexte?: string }) =>
    api.post<RecordPersonnel>(`/athletes/${athleteId}/records`, record),
  supprimerRecord: (athleteId: string, recordId: string) =>
    api.delete<void>(`/athletes/${athleteId}/records/${recordId}`),
  blessures: (athleteId: string) => api.get<Blessure[]>(`/athletes/${athleteId}/injuries`),
  ajouterBlessure: (athleteId: string, blessure: Partial<Blessure> & { zone: string; debut: string }) =>
    api.post<Blessure>(`/athletes/${athleteId}/injuries`, blessure),
  modifierBlessure: (athleteId: string, id: string, blessure: Partial<Blessure> & { zone: string; debut: string }) =>
    api.patch<Blessure>(`/athletes/${athleteId}/injuries/${id}`, blessure),
  contraintes: (athleteId: string) => api.get<Contrainte[]>(`/athletes/${athleteId}/constraints`),
  ajouterContrainte: (athleteId: string, c: { type: string; detail: string; debut?: string; fin?: string }) =>
    api.post<Contrainte>(`/athletes/${athleteId}/constraints`, c),
  supprimerContrainte: (athleteId: string, id: string) =>
    api.delete<void>(`/athletes/${athleteId}/constraints/${id}`),
  signatures: (athleteId: string) => api.get<SeanceSignature[]>(`/athletes/${athleteId}/signature-sessions`),
}

export const analyseApi = {
  synthese: (athleteId: string, jours = 90) =>
    api.get<Synthese>(`/athletes/${athleteId}/analysis?jours=${jours}`),
}
