import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // L'API tourne a part : on la proxifie pour eviter toute question de CORS en developpement.
    // PREPA_API permet d'en viser une autre — utile quand deux instances tournent en parallele.
    proxy: {
      '/api': { target: process.env.PREPA_API ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
})
