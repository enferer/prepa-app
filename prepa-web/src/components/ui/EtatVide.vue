<script setup lang="ts">
defineProps<{ titre: string; message?: string }>()
</script>

<template>
  <div class="rounded-xl border border-dashed border-[var(--color-bordure)] px-5 py-10 text-center">
    <p class="font-medium">{{ titre }}</p>
    <p v-if="message" class="mx-auto mt-1 max-w-md text-sm text-[var(--color-doux)]">{{ message }}</p>
    <div class="mt-3"><slot /></div>
  </div>
</template>
