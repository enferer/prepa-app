/**
 * Mise en forme des grandeurs d'entrainement.
 *
 * <p>L'athlete lit des allures en minutes par kilometre et des durees en heures, jamais des
 * secondes brutes : la conversion se fait ici, une fois, et aucune vue ne manipule de nombre
 * technique.
 */

/** Allure en secondes par kilometre vers {@code m:ss}. */
export function allure(secKm?: number | null): string {
  if (!secKm) return '—'
  const minutes = Math.floor(secKm / 60)
  const secondes = Math.round(secKm % 60)
  return `${minutes}:${String(secondes).padStart(2, '0')}`
}

/** Duree en secondes vers {@code 1h08:50} ou {@code 41:39}. */
export function duree(secondes?: number | null): string {
  if (!secondes) return '—'
  const heures = Math.floor(secondes / 3600)
  const minutes = Math.floor((secondes % 3600) / 60)
  const reste = Math.round(secondes % 60)
  if (heures > 0) {
    return `${heures}h${String(minutes).padStart(2, '0')}:${String(reste).padStart(2, '0')}`
  }
  return `${minutes}:${String(reste).padStart(2, '0')}`
}

/** Duree en secondes vers une forme courte : {@code 1h08}, {@code 42 min}. */
export function dureeCourte(secondes?: number | null): string {
  if (!secondes) return '—'
  const heures = Math.floor(secondes / 3600)
  const minutes = Math.round((secondes % 3600) / 60)
  return heures > 0 ? `${heures}h${String(minutes).padStart(2, '0')}` : `${minutes} min`
}

export function distance(metres?: number | null, decimales = 1): string {
  if (metres === undefined || metres === null) return '—'
  return `${(metres / 1000).toFixed(decimales)} km`
}

export function km(valeur?: number | null, decimales = 1): string {
  if (valeur === undefined || valeur === null) return '—'
  return `${valeur.toFixed(decimales)} km`
}

const JOURS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi']
const MOIS = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
]

export function dateCourte(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${d.getDate()} ${MOIS[d.getMonth()].slice(0, 4)}.`
}

export function dateLongue(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return `${JOURS[d.getDay()]} ${d.getDate()} ${MOIS[d.getMonth()]}`
}

export function jourSemaine(iso: string): string {
  return JOURS[new Date(iso).getDay()]
}

/** Nombre de jours d'ecart avec aujourd'hui, positif dans le futur. */
export function joursDepuis(iso: string): number {
  const jour = new Date(iso)
  jour.setHours(0, 0, 0, 0)
  const aujourdhui = new Date()
  aujourdhui.setHours(0, 0, 0, 0)
  return Math.round((jour.getTime() - aujourdhui.getTime()) / 86_400_000)
}

export function estAujourdhui(iso: string): boolean {
  return joursDepuis(iso) === 0
}

/** Chrono vise, en {@code 4h00} ou {@code 1h35:20}. */
export function chrono(secondes?: number | null): string {
  if (!secondes) return '—'
  const heures = Math.floor(secondes / 3600)
  const minutes = Math.floor((secondes % 3600) / 60)
  const reste = secondes % 60
  const base = `${heures}h${String(minutes).padStart(2, '0')}`
  return reste ? `${base}:${String(reste).padStart(2, '0')}` : base
}

export function pourcentage(valeur?: number | null, decimales = 0): string {
  if (valeur === undefined || valeur === null) return '—'
  return `${valeur.toFixed(decimales)} %`
}

export function signe(valeur: number, decimales = 0): string {
  return `${valeur > 0 ? '+' : ''}${valeur.toFixed(decimales)}`
}
