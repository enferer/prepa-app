<script setup lang="ts">
import { onMounted, ref } from 'vue'
import CarteBase from '@/components/ui/CarteBase.vue'
import EtatVide from '@/components/ui/EtatVide.vue'
import { journalApi } from '@/api'
import { useAuth } from '@/stores/auth'
import { dateLongue } from '@/composables/useFormat'
import type { EntreeJournal } from '@/api/types'

const auth = useAuth()

const entrees = ref<EntreeJournal[]>([])
const contenu = ref('')
const chargement = ref(true)
const envoi = ref(false)

const aujourdhui = new Date().toISOString().slice(0, 10)

async function charger() {
  if (!auth.athlete) return
  chargement.value = true
  try {
    entrees.value = await journalApi.lister(auth.athlete.id)
    // Réécrire le jour même met à jour l'entrée : on repart de ce qui est déjà écrit.
    contenu.value = entrees.value.find((e) => e.date === aujourdhui)?.contenu ?? ''
  } finally {
    chargement.value = false
  }
}

async function enregistrer() {
  if (!auth.athlete || !contenu.value.trim()) return
  envoi.value = true
  try {
    await journalApi.enregistrer(auth.athlete.id, { date: aujourdhui, contenu: contenu.value })
    await charger()
  } finally {
    envoi.value = false
  }
}

onMounted(charger)
</script>

<template>
  <div class="space-y-5">
    <h1 class="text-lg font-semibold">Journal</h1>

    <CarteBase
      titre="Aujourd'hui"
      sous-titre="Ce que la montre ne mesure pas : sommeil, moral, jambes lourdes, une gêne qui commence. Ton coach le lit avant de faire le point."
    >
      <textarea
        v-model="contenu"
        rows="5"
        placeholder="Jambes lourdes ce matin, mieux après vingt minutes…"
        class="w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2 text-sm"
      />
      <div class="mt-3 flex items-center justify-between gap-3">
        <p class="text-xs text-[var(--color-doux)]">
          Écris comme tu parles. Si tu mentionnes une douleur, ton coach le verra.
        </p>
        <button
          class="shrink-0 rounded-lg bg-[var(--color-accent)] px-4 py-1.5 font-medium text-white disabled:opacity-60"
          :disabled="envoi || !contenu.trim()"
          @click="enregistrer"
        >
          {{ envoi ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
      </div>
    </CarteBase>

    <div v-if="chargement" class="text-sm text-[var(--color-doux)]">Chargement…</div>

    <EtatVide
      v-else-if="!entrees.length"
      titre="Journal vide"
      message="Note ce que tu ressens : c'est souvent là que se lit une fatigue avant qu'elle ne se voie dans les chiffres."
    />

    <CarteBase v-else titre="Entrées précédentes">
      <ol class="divide-y divide-[var(--color-bordure)]">
        <li v-for="entree in entrees" :key="entree.id" class="py-3">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-sm font-medium">{{ dateLongue(entree.date) }}</span>
            <span
              v-if="entree.douleur"
              class="rounded-full bg-[var(--color-manque-fond)] px-2 py-0.5 text-xs text-[var(--color-manque)]"
              title="Ton coach est alerté sur ce point"
            >
              gêne signalée
            </span>
          </div>
          <p class="mt-1 text-sm whitespace-pre-line text-[var(--color-doux)]">{{ entree.contenu }}</p>
        </li>
      </ol>
    </CarteBase>
  </div>
</template>
