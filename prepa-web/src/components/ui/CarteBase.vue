<script setup lang="ts">
defineProps<{ titre?: string; sousTitre?: string }>()
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
      <slot name="entete" />
    </header>
    <slot />
  </section>
</template>
