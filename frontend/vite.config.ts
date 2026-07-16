/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['apple-touch-icon.png', 'favicon.ico'],
      manifest: {
        name: 'Ensemble',
        short_name: 'Ensemble',
        start_url: '/',
        display: 'standalone',
        background_color: '#f3ecdd',
        theme_color: '#f3ecdd',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'maskable-icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        navigateFallbackDenylist: [/^\/api/],
      },
    }),
  ],
  server: {
    // Dev-only: proxy API calls to the Spring backend so the browser can use
    // same-origin `/api/**` requests during `vite dev`.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // Built assets land in Spring's static resources so one process serves both.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    css: true,
  },
})
