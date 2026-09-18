<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useEntrainement } from '@/stores/entrainement'
import { useTheme } from '@/stores/theme'

const auth = useAuth()
const entrainement = useEntrainement()
const theme = useTheme()
const route = useRoute()
const router = useRouter()

const connecte = computed(() => auth.athlete !== null)
const admin = computed(() => auth.athlete?.role === 'ADMIN')

const TOUS_ONGLETS = [
  { nom: 'tableau-de-bord', libelle: 'Aujourd’hui', icone: '◎' },
  { nom: 'saison', libelle: 'Saison', icone: '▦' },
  { nom: 'seances', libelle: 'Séances', icone: '▶' },
  { nom: 'stats', libelle: 'Stats', icone: '◫' },
  { nom: 'journal', libelle: 'Journal', icone: '✎' },
]

/**
 * Le journal disparait quand on regarde quelqu'un d'autre : il n'est pas partage, et le
 * laisser afficherait le sien sous le nom d'un autre — plus trompeur qu'utile.
 */
const onglets = computed(() =>
  entrainement.lectureSeule ? TOUS_ONGLETS.filter((o) => o.nom !== 'journal') : TOUS_ONGLETS,
)

const autresProfils = computed(() => entrainement.athletes.length > 1)

onMounted(async () => {
  if (await auth.restaurer()) {
    await Promise.all([entrainement.charger(), entrainement.chargerAthletes()])
  }
})

// Quitter le journal d'office plutot que d'y laisser un ecran qui ne parle plus de la
// personne affichee en haut.
watch(
  () => entrainement.lectureSeule,
  (seule) => {
    if (seule && route.name === 'journal') router.push({ name: 'tableau-de-bord' })
  },
)

// Une connexion en cours de session doit charger les donnees sans rechargement de page.
watch(
  () => auth.athlete?.id,
  (id, precedent) => {
    if (id && id !== precedent) {
      entrainement.charger()
      entrainement.chargerAthletes()
    }
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
        <!--
          Le nom en tete de page devient le choix du profil consulte des qu'il y a quelqu'un
          d'autre a regarder : c'est deja la ou l'oeil cherche a qui appartient l'ecran.
        -->
        <select
          v-if="autresProfils"
          class="-mx-1 max-w-[9rem] rounded-md bg-transparent px-1 py-0.5 font-semibold sm:max-w-none"
          :class="entrainement.lectureSeule ? 'text-[var(--color-accent)]' : ''"
          :value="entrainement.athleteAffiche?.id"
          aria-label="Profil consulté"
          @change="entrainement.consulter(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="a in entrainement.athletes" :key="a.id" :value="a.id">
            {{ a.id === auth.athlete?.id ? `${a.displayName} (moi)` : a.displayName }}
          </option>
        </select>
        <RouterLink v-else :to="{ name: 'tableau-de-bord' }" class="font-semibold">
          {{ auth.athlete?.displayName }}
        </RouterLink>

        <nav class="ml-auto hidden items-center gap-1 sm:flex">
          <RouterLink
            v-for="onglet in onglets"
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
          <!--
            L'exploitation ne rejoint pas les cinq onglets : ceux-ci sont la navigation de
            l'athlete, et un administrateur est d'abord un athlete comme les autres.
          -->
          <RouterLink
            v-if="admin"
            :to="{ name: 'admin-sync' }"
            class="rounded-md px-2 py-1.5 text-sm text-[var(--color-doux)] hover:bg-[var(--color-appui)]"
            :class="route.name === 'admin-sync' ? 'text-[var(--color-accent)]' : ''"
            title="Synchronisation Garmin"
          >
            ⟳
          </RouterLink>
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

    <!--
      Aucun contenu ne doit pouvoir élargir la page : un graphique trop large poussait la
      mise en page entière, jusqu'à faire sortir des onglets de l'écran.
    -->
    <!--
      Dire une fois, en haut, que rien n'est modifiable ici : les boutons d'action ayant
      disparu des ecrans, leur absence seule laisserait croire a une panne.
    -->
    <p
      v-if="entrainement.lectureSeule"
      class="border-b border-[var(--color-bordure)] bg-[var(--color-accent-fond)] px-4 py-2 text-center text-sm text-[var(--color-accent)]"
    >
      Entraînement de {{ entrainement.athleteAffiche?.displayName }} — lecture seule.
      <button class="underline underline-offset-2" @click="entrainement.consulter(null)">
        Revenir à moi
      </button>
    </p>

    <main class="mx-auto max-w-5xl overflow-x-hidden px-4 py-5">
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
        v-for="onglet in onglets"
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
