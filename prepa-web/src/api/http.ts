import type { ErreurApi } from './types'

/**
 * Client HTTP de l'application.
 *
 * <p>Le jeton d'acces vit quinze minutes ; plutot que de laisser l'athlete tomber sur un ecran
 * de connexion au milieu de sa lecture, une reponse 401 declenche un renouvellement silencieux
 * et la requete est rejouee une fois. Si le renouvellement echoue a son tour, la session est
 * vraiment finie et on redirige.
 */

const BASE = '/api/v1'

const CLE_ACCES = 'prepa.accessToken'
const CLE_RAFRAICHISSEMENT = 'prepa.refreshToken'

export class ErreurHttp extends Error {
  readonly statut: number
  readonly code: string
  readonly details?: Record<string, unknown>

  constructor(statut: number, code: string, message: string, details?: Record<string, unknown>) {
    super(message)
    this.statut = statut
    this.code = code
    this.details = details
  }
}

export const jetons = {
  acces: () => localStorage.getItem(CLE_ACCES),
  rafraichissement: () => localStorage.getItem(CLE_RAFRAICHISSEMENT),
  enregistrer(acces: string, rafraichissement: string) {
    localStorage.setItem(CLE_ACCES, acces)
    localStorage.setItem(CLE_RAFRAICHISSEMENT, rafraichissement)
  },
  effacer() {
    localStorage.removeItem(CLE_ACCES)
    localStorage.removeItem(CLE_RAFRAICHISSEMENT)
  },
}

/** Renouvellement en cours, partage : dix requetes simultanees n'en declenchent qu'un. */
let renouvellementEnCours: Promise<boolean> | null = null

async function renouveler(): Promise<boolean> {
  const refresh = jetons.rafraichissement()
  if (!refresh) return false

  renouvellementEnCours ??= (async () => {
    try {
      const reponse = await fetch(`${BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refresh }),
      })
      if (!reponse.ok) return false
      const corps = await reponse.json()
      jetons.enregistrer(corps.accessToken, corps.refreshToken)
      return true
    } catch {
      return false
    } finally {
      renouvellementEnCours = null
    }
  })()

  return renouvellementEnCours
}

interface Options {
  methode?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  corps?: unknown
  /** Usage interne : empeche une boucle de renouvellement. */
  dejaRejoue?: boolean
}

export async function appeler<T>(chemin: string, options: Options = {}): Promise<T> {
  const { methode = 'GET', corps, dejaRejoue = false } = options

  const entetes: Record<string, string> = {}
  const acces = jetons.acces()
  if (acces) entetes.Authorization = `Bearer ${acces}`
  if (corps !== undefined) entetes['Content-Type'] = 'application/json'

  const reponse = await fetch(`${BASE}${chemin}`, {
    method: methode,
    headers: entetes,
    body: corps === undefined ? undefined : JSON.stringify(corps),
  })

  if (reponse.status === 401 && !dejaRejoue && !chemin.startsWith('/auth/')) {
    if (await renouveler()) {
      return appeler<T>(chemin, { ...options, dejaRejoue: true })
    }
    jetons.effacer()
    window.location.hash = '#/connexion'
    throw new ErreurHttp(401, 'UNAUTHENTICATED', 'Session expiree')
  }

  if (!reponse.ok) {
    const erreur = (await reponse.json().catch(() => null)) as ErreurApi | null
    throw new ErreurHttp(
      reponse.status,
      erreur?.error.code ?? 'ERREUR',
      erreur?.error.message ?? 'Une erreur est survenue',
      erreur?.error.details,
    )
  }

  if (reponse.status === 204) return undefined as T
  return (await reponse.json()) as T
}

export const api = {
  get: <T>(chemin: string) => appeler<T>(chemin),
  post: <T>(chemin: string, corps?: unknown) => appeler<T>(chemin, { methode: 'POST', corps }),
  put: <T>(chemin: string, corps?: unknown) => appeler<T>(chemin, { methode: 'PUT', corps }),
  patch: <T>(chemin: string, corps?: unknown) => appeler<T>(chemin, { methode: 'PATCH', corps }),
  delete: <T>(chemin: string) => appeler<T>(chemin, { methode: 'DELETE' }),
}
