<script setup lang="ts">
defineProps<{
  titre?: string
  sousTitre?: string
  /**
   * Sur quelles données la carte est calculée.
   *
   * <p>Un filtre de période qui n'agit que sur la moitié des cartes est pire que pas de filtre :
   * on croit lire ses trente derniers jours alors qu'on lit toute sa vie de coureur. Chaque
   * carte dit donc ce qu'elle regarde, plutôt que de laisser deviner.
   */
  portee?: string
}>()
</script>

<template>
  <section
    class="rounded-xl border border-[var(--color-bordure)] bg-[var(--color-surface)] p-4 sm:p-5"
  >
    <header v-if="titre || $slots.entete" class="mb-3 flex items-baseline justify-between gap-3">
      <div>
        <h2 v-if="titre" class="text-sm font-semibold tracking-wide uppercase text-[var(--color-doux)]">
          {{ titre }}
        </h2>
        <p v-if="sousTitre" class="mt-0.5 text-sm text-[var(--color-doux)]">{{ sousTitre }}</p>
      </div>
      <span
        v-if="portee"
        class="shrink-0 rounded-full bg-[var(--color-appui)] px-2 py-0.5 text-xs whitespace-nowrap text-[var(--color-doux)]"
      >
        {{ portee }}
      </span>
      <slot name="entete" />
    </header>
    <slot />
  </section>
</template>
