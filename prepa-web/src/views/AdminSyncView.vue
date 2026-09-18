<script setup lang="ts">
/**
 * Exploitation de la synchronisation Garmin.
 *
 * <p>La question à laquelle cet écran répond : « pourquoi les séances de cet athlète ne
 * remontent-elles plus ? ». Tant que la synchronisation vivait dans un conteneur séparé, il
 * fallait aller lire ses journaux pour le savoir ; les comptes en défaut remontent désormais en
 * tête, avec le message d'erreur et de quoi relancer.
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { adminSyncApi } from '@/api'
import { ErreurHttp } from '@/api/http'
import type { EtatSyncGlobal, PassageSync } from '@/api/types'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import PastilleSync from '@/components/ui/PastilleSync.vue'
import { dateLongue, dureeCourte } from '@/composables/useFormat'

/** Cadence du suivi d'un passage en cours. Assez pour voir bouger, assez peu pour ne pas peser. */
const RYTHME_MS = 5000

const etat = ref<EtatSyncGlobal | null>(null)
const passages = ref<PassageSync[]>([])
const filtre = ref<string | null>(null)
const chargement = ref(true)
const erreur = ref<string | null>(null)
const enAttente = ref<string | null>(null)

let minuterie: ReturnType<typeof setInterval> | null = null

const athleteFiltre = computed(
  () => etat.value?.comptes.find((c) => c.athleteId === filtre.value)?.athlete ?? null,
)

async function charger() {
  try {
    const [global, historique] = await Promise.all([
      adminSyncApi.etat(),
      adminSyncApi.passages(filtre.value ?? undefined),
    ])
    etat.value = global
    passages.value = historique
    erreur.value = null
    // Le suivi ne s'arme que tant qu'il se passe quelque chose : au repos, l'écran est statique
    // et n'interroge plus le serveur.
    global.enCours ? armerLeSuivi() : desarmerLeSuivi()
  } catch (e) {
    erreur.value = e instanceof ErreurHttp ? e.message : 'Impossible de lire l’état de la synchronisation'
  } finally {
    chargement.value = false
  }
}

function armerLeSuivi() {
  minuterie ??= setInterval(charger, RYTHME_MS)
}

function desarmerLeSuivi() {
  if (minuterie) {
    clearInterval(minuterie)
    minuterie = null
  }
}

async function lancer(athleteId?: string) {
  enAttente.value = athleteId ?? 'tout'
  try {
    await (athleteId ? adminSyncApi.synchroniser(athleteId) : adminSyncApi.toutSynchroniser())
    erreur.value = null
    // On repasse aussitôt : le bandeau doit basculer sans attendre le prochain battement.
    await charger()
  } catch (e) {
    erreur.value =
      e instanceof ErreurHttp ? e.message : 'La synchronisation n’a pas pu être lancée'
  } finally {
    enAttente.value = null
  }
}

function filtrerSur(athleteId: string) {
  filtre.value = filtre.value === athleteId ? null : athleteId
  charger()
}

function declencheur(passage: PassageSync): string {
  switch (passage.declencheur) {
    case 'MANUEL':
      return passage.demandePar ? `À la main — ${passage.demandePar}` : 'À la main'
    case 'DEMANDE':
      return 'Sur demande'
    default:
      return 'Planifié'
  }
}

function bilan(passage: PassageSync): string {
  if (passage.statut !== 'OK') return '—'
  const morceaux = [`${passage.importees ?? 0} importée(s)`]
  if (passage.misesAJour) morceaux.push(`${passage.misesAJour} enrichie(s)`)
  if (passage.doublons) morceaux.push(`${passage.doublons} déjà connue(s)`)
  return morceaux.join(', ')
}

function heure(iso?: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}

onMounted(charger)
onUnmounted(desarmerLeSuivi)
</script>

<template>
  <div v-if="chargement" class="text-sm text-[var(--color-doux)]">Chargement…</div>

  <div v-else class="space-y-5">
    <p
      v-if="erreur"
      class="rounded-lg bg-[var(--color-manque-fond)] px-4 py-3 text-sm text-[var(--color-manque)]"
    >
      {{ erreur }}
    </p>

    <!-- 1. Ce qui tourne maintenant -->
    <CarteBase titre="Synchronisation Garmin">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p v-if="etat?.enCours" class="flex items-center gap-2 text-sm">
          <span class="inline-block size-2 animate-pulse rounded-full bg-[var(--color-accent)]" />
          <span>
            Passage en cours
            <template v-if="etat.athleteEnCours">— {{ etat.athleteEnCours }}</template>
          </span>
        </p>
        <p v-else class="text-sm text-[var(--color-doux)]">
          Aucune synchronisation en cours. Le passage complet tourne toutes les trente minutes.
        </p>

        <button
          class="rounded-md bg-[var(--color-accent)] px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          :disabled="etat?.enCours || enAttente !== null"
          @click="lancer()"
        >
          {{ enAttente === 'tout' ? 'Lancement…' : 'Tout synchroniser' }}
        </button>
      </div>
    </CarteBase>

    <!-- 2. Les comptes reliés, les défaillants en tête -->
    <CarteBase titre="Comptes reliés" :sous-titre="`${etat?.comptes.length ?? 0} athlète(s)`">
      <EtatVide
        v-if="!etat?.comptes.length"
        titre="Aucun compte Garmin relié"
        message="Relie un compte depuis le profil d’un athlète pour que ses séances remontent seules."
      />
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="text-left text-xs uppercase tracking-wide text-[var(--color-doux)]">
            <tr class="border-b border-[var(--color-bordure)]">
              <th class="py-2 pr-3 font-medium">Athlète</th>
              <th class="py-2 pr-3 font-medium">Compte Garmin</th>
              <th class="py-2 pr-3 font-medium">Dernière sync</th>
              <th class="py-2 pr-3 font-medium">État</th>
              <th class="py-2 font-medium"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="compte in etat.comptes"
              :key="compte.athleteId"
              class="cursor-pointer border-b border-[var(--color-bordure)] last:border-0 hover:bg-[var(--color-appui)]"
              :class="filtre === compte.athleteId ? 'bg-[var(--color-appui)]' : ''"
              @click="filtrerSur(compte.athleteId)"
            >
              <td class="py-2 pr-3 font-medium">{{ compte.athlete }}</td>
              <td class="py-2 pr-3 text-[var(--color-doux)]">
                {{ compte.garminDisplayName ?? '—' }}
              </td>
              <td class="tabulaire py-2 pr-3 text-[var(--color-doux)]">
                {{ dateLongue(compte.derniereSync) }}
              </td>
              <td class="py-2 pr-3">
                <PastilleSync :statut="compte.dernierStatut" />
                <p
                  v-if="compte.dernierMessage && compte.dernierStatut !== 'OK'"
                  class="mt-1 max-w-sm text-xs text-[var(--color-doux)]"
                >
                  {{ compte.dernierMessage }}
                </p>
              </td>
              <td class="py-2 text-right">
                <button
                  class="rounded-md border border-[var(--color-bordure)] px-2 py-1 text-xs hover:bg-[var(--color-surface)] disabled:opacity-50"
                  :disabled="etat.enCours || enAttente !== null"
                  @click.stop="lancer(compte.athleteId)"
                >
                  {{ enAttente === compte.athleteId ? '…' : 'Synchroniser' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </CarteBase>

    <!-- 3. L'historique -->
    <CarteBase
      titre="Derniers passages"
      :sous-titre="athleteFiltre ? `Filtré sur ${athleteFiltre}` : undefined"
    >
      <template #entete>
        <button
          v-if="filtre"
          class="text-xs text-[var(--color-accent)]"
          @click="filtre = null; charger()"
        >
          Voir tout
        </button>
      </template>

      <EtatVide
        v-if="!passages.length"
        titre="Aucun passage enregistré"
        message="L’historique se remplit au premier passage, planifié ou lancé à la main."
      />
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="text-left text-xs uppercase tracking-wide text-[var(--color-doux)]">
            <tr class="border-b border-[var(--color-bordure)]">
              <th class="py-2 pr-3 font-medium">Quand</th>
              <th class="py-2 pr-3 font-medium">Athlète</th>
              <th class="py-2 pr-3 font-medium">Origine</th>
              <th class="py-2 pr-3 font-medium">Durée</th>
              <th class="py-2 pr-3 font-medium">Période</th>
              <th class="py-2 pr-3 font-medium">État</th>
              <th class="py-2 font-medium">Résultat</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="passage in passages"
              :key="passage.id"
              class="border-b border-[var(--color-bordure)] last:border-0"
            >
              <td class="tabulaire py-2 pr-3 whitespace-nowrap">
                {{ dateLongue(passage.demarreA) }} · {{ heure(passage.demarreA) }}
              </td>
              <td class="py-2 pr-3">{{ passage.athlete ?? '—' }}</td>
              <td class="py-2 pr-3 text-[var(--color-doux)]">{{ declencheur(passage) }}</td>
              <td class="tabulaire py-2 pr-3 text-[var(--color-doux)]">
                {{ passage.dureeSec != null ? dureeCourte(passage.dureeSec) : '—' }}
              </td>
              <td class="py-2 pr-3 whitespace-nowrap text-[var(--color-doux)]">
                {{ passage.fenetreDu ?? '—' }} → {{ passage.fenetreAu ?? '—' }}
              </td>
              <td class="py-2 pr-3"><PastilleSync :statut="passage.statut" /></td>
              <td class="py-2 text-[var(--color-doux)]">
                <span v-if="passage.statut === 'OK'">{{ bilan(passage) }}</span>
                <span v-else class="text-xs">{{ passage.message ?? '—' }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </CarteBase>
  </div>
</template>
