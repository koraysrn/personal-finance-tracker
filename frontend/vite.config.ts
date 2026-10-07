import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // Build directly into the Spring Boot static resources directory so that
    // the packaged jar serves the UI without a separate web server.
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
});
