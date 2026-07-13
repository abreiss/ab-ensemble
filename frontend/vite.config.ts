/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
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
