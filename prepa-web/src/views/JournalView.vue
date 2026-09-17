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
const chargement = ref(true)
const envoi = ref(false)

const aujourdhui = new Date().toISOString().slice(0, 10)

/** Saisie du jour : volontairement courte, pour qu'écrire reste une habitude tenable. */
const saisie = ref({
  date: aujourdhui,
  contenu: '',
  humeur: undefined as number | undefined,
  fatigue: undefined as number | undefined,
  douleur: false,
})

async function charger() {
  if (!auth.athlete) return
  chargement.value = true
  try {
    entrees.value = await journalApi.lister(auth.athlete.id)
    const dujour = entrees.value.find((e) => e.date === aujourdhui)
    if (dujour) {
      saisie.value = {
        date: dujour.date,
        contenu: dujour.contenu,
        humeur: dujour.humeur,
        fatigue: dujour.fatigue,
        douleur: dujour.douleur,
      }
    }
  } finally {
    chargement.value = false
  }
}

async function enregistrer() {
  if (!auth.athlete || !saisie.value.contenu.trim()) return
  envoi.value = true
  try {
    await journalApi.enregistrer(auth.athlete.id, { ...saisie.value })
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
      sous-titre="Ce que la montre ne mesure pas : sommeil, moral, gêne naissante. Ton coach le lit."
    >
      <textarea
        v-model="saisie.contenu"
        rows="4"
        placeholder="Jambes lourdes ce matin, mieux après vingt minutes…"
        class="w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2 text-sm"
      />

      <div class="mt-3 flex flex-wrap items-center gap-4 text-sm">
        <label class="flex items-center gap-2">
          <span class="text-[var(--color-doux)]">Humeur</span>
          <select
            v-model="saisie.humeur"
            class="rounded-md border border-[var(--color-bordure)] bg-[var(--color-fond)] px-2 py-1"
          >
            <option :value="undefined">—</option>
            <option v-for="n in 5" :key="n" :value="n">{{ n }}</option>
          </select>
        </label>

        <label class="flex items-center gap-2">
          <span class="text-[var(--color-doux)]">Fatigue</span>
          <select
            v-model="saisie.fatigue"
            class="rounded-md border border-[var(--color-bordure)] bg-[var(--color-fond)] px-2 py-1"
          >
            <option :value="undefined">—</option>
            <option v-for="n in 5" :key="n" :value="n">{{ n }}</option>
          </select>
        </label>

        <!-- Cocher une douleur declenche une question du coach au prochain point. -->
        <label class="flex items-center gap-2">
          <input v-model="saisie.douleur" type="checkbox" class="accent-[var(--color-manque)]" />
          <span>Une douleur ou une gêne</span>
        </label>

        <button
          class="ml-auto rounded-lg bg-[var(--color-accent)] px-4 py-1.5 font-medium text-white disabled:opacity-60"
          :disabled="envoi || !saisie.contenu.trim()"
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
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <span class="font-medium">{{ dateLongue(entree.date) }}</span>
            <span
              v-if="entree.douleur"
              class="rounded-full bg-[var(--color-manque-fond)] px-2 py-0.5 text-xs text-[var(--color-manque)]"
            >
              douleur
            </span>
            <span v-if="entree.humeur" class="text-xs text-[var(--color-doux)]">
              humeur {{ entree.humeur }}/5
            </span>
            <span v-if="entree.fatigue" class="text-xs text-[var(--color-doux)]">
              fatigue {{ entree.fatigue }}/5
            </span>
          </div>
          <p class="mt-1 text-sm whitespace-pre-line text-[var(--color-doux)]">{{ entree.contenu }}</p>
        </li>
      </ol>
    </CarteBase>
  </div>
</template>
