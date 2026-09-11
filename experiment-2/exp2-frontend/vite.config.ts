import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The study application: this is the build that ships (into the Spring Boot jar's static/).
//
// public/ holds only this app's own favicon. The portal's branding and news imagery are imported as
// modules inside @insurance-portal/core, so the bundler emits them and they arrive with the
// package - no shared public/ directory to keep in sync with a repository we no longer own.
export default defineConfig({
  plugins: [react()],

  resolve: {
    // Only needed when @insurance-portal/core is `npm link`ed for parallel work on the portal:
    // Node would then resolve react from inside the linked package's real path while this app uses
    // its own copy, and two React instances mean `Invalid hook call` at runtime. Harmless (and
    // inert) for a normal registry install, where a dependency's devDependencies are not installed.
    dedupe: ['react', 'react-dom', 'react-router-dom'],
  },

  server: {
    // Dev: forward /api calls to the Spring Boot backend so there's no CORS
    // and the frontend can use same-origin relative URLs (as it will in prod).
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
