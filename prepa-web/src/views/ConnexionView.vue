<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useEntrainement } from '@/stores/entrainement'

const auth = useAuth()
const entrainement = useEntrainement()
const router = useRouter()

const email = ref('')
const motDePasse = ref('')
const erreur = ref<string | null>(null)
const envoi = ref(false)

async function soumettre() {
  erreur.value = null
  envoi.value = true
  try {
    await auth.connecter(email.value, motDePasse.value)
    await entrainement.charger()
    router.push({ name: 'tableau-de-bord' })
  } catch (e) {
    erreur.value = e instanceof Error ? e.message : 'Connexion impossible'
  } finally {
    envoi.value = false
  }
}
</script>

<template>
  <div class="flex min-h-dvh items-center justify-center px-4">
    <form
      class="w-full max-w-sm rounded-xl border border-[var(--color-bordure)] bg-[var(--color-surface)] p-6"
      @submit.prevent="soumettre"
    >
      <h1 class="text-lg font-semibold">Suivi d'entraînement</h1>
      <p class="mt-1 text-sm text-[var(--color-doux)]">Connecte-toi pour retrouver ton plan.</p>

      <label class="mt-5 block text-sm font-medium" for="email">Email</label>
      <input
        id="email"
        v-model="email"
        type="email"
        autocomplete="username"
        required
        class="mt-1 w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2"
      />

      <label class="mt-4 block text-sm font-medium" for="motdepasse">Mot de passe</label>
      <input
        id="motdepasse"
        v-model="motDePasse"
        type="password"
        autocomplete="current-password"
        required
        class="mt-1 w-full rounded-lg border border-[var(--color-bordure)] bg-[var(--color-fond)] px-3 py-2"
      />

      <p v-if="erreur" class="mt-3 text-sm text-[var(--color-manque)]">{{ erreur }}</p>

      <button
        type="submit"
        :disabled="envoi"
        class="mt-5 w-full rounded-lg bg-[var(--color-accent)] px-4 py-2.5 font-medium text-white disabled:opacity-60"
      >
        {{ envoi ? 'Connexion…' : 'Se connecter' }}
      </button>
    </form>
  </div>
</template>
