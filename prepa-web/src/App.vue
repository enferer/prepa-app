<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useEntrainement } from '@/stores/entrainement'
import { useTheme } from '@/stores/theme'

const auth = useAuth()
const entrainement = useEntrainement()
const theme = useTheme()
const route = useRoute()

const connecte = computed(() => auth.athlete !== null)

const ONGLETS = [
  { nom: 'tableau-de-bord', libelle: 'Aujourd’hui', icone: '◎' },
  { nom: 'plan', libelle: 'Plan', icone: '▤' },
  { nom: 'seances', libelle: 'Séances', icone: '▶' },
  { nom: 'stats', libelle: 'Stats', icone: '◫' },
  { nom: 'journal', libelle: 'Journal', icone: '✎' },
]

onMounted(async () => {
  if (await auth.restaurer()) await entrainement.charger()
})

// Une connexion en cours de session doit charger les donnees sans rechargement de page.
watch(
  () => auth.athlete?.id,
  (id, precedent) => {
    if (id && id !== precedent) entrainement.charger()
  },
)
</script>

<template>
  <div v-if="!connecte" class="min-h-dvh">
    <RouterView />
  </div>

  <div v-else class="min-h-dvh pb-20 sm:pb-0">
    <header
      class="sticky top-0 z-10 border-b border-[var(--color-bordure)] bg-[var(--color-surface)]/95 backdrop-blur"
    >
      <div class="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        <RouterLink :to="{ name: 'tableau-de-bord' }" class="font-semibold">
          {{ auth.athlete?.displayName }}
        </RouterLink>

        <nav class="ml-auto hidden items-center gap-1 sm:flex">
          <RouterLink
            v-for="onglet in ONGLETS"
            :key="onglet.nom"
            :to="{ name: onglet.nom }"
            class="rounded-md px-3 py-1.5 text-sm transition-colors hover:bg-[var(--color-appui)]"
            :class="route.name === onglet.nom
              ? 'bg-[var(--color-accent-fond)] font-medium text-[var(--color-accent)]'
              : 'text-[var(--color-doux)]'"
          >
            {{ onglet.libelle }}
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-1 sm:ml-0">
          <button
            class="rounded-md px-2 py-1.5 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)]"
            :title="theme.sombre ? 'Passer en clair' : 'Passer en sombre'"
            @click="theme.basculer()"
          >
            {{ theme.sombre ? '☀' : '☾' }}
          </button>
          <RouterLink
            :to="{ name: 'profil' }"
            class="rounded-md px-2 py-1.5 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)]"
            title="Profil"
          >
            ⚙
          </RouterLink>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-5xl px-4 py-5">
      <p
        v-if="entrainement.erreur"
        class="mb-4 rounded-lg bg-[var(--color-manque-fond)] px-4 py-3 text-sm text-[var(--color-manque)]"
      >
        {{ entrainement.erreur }}
      </p>
      <RouterView />
    </main>

    <!-- Sur telephone, la navigation revient sous le pouce. -->
    <nav
      class="fixed inset-x-0 bottom-0 z-10 flex border-t border-[var(--color-bordure)] bg-[var(--color-surface)] pb-[env(safe-area-inset-bottom)] sm:hidden"
    >
      <RouterLink
        v-for="onglet in ONGLETS"
        :key="onglet.nom"
        :to="{ name: onglet.nom }"
        class="flex flex-1 flex-col items-center gap-0.5 py-2 text-xs"
        :class="route.name === onglet.nom ? 'text-[var(--color-accent)]' : 'text-[var(--color-doux)]'"
      >
        <span class="text-base leading-none">{{ onglet.icone }}</span>
        {{ onglet.libelle }}
      </RouterLink>
    </nav>
  </div>
</template>
