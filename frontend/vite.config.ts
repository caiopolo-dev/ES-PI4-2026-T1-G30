import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

// Vite executa apenas no desenvolvimento. O servidor principal e de producao e o Java.
const javaBackend = 'http://127.0.0.1:8080';

export default defineConfig({
  base: '/',
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/health': { target: javaBackend, changeOrigin: true },
      '/auth': { target: javaBackend, changeOrigin: true },
      '/treatments': { target: javaBackend, changeOrigin: true },
      '/doses': { target: javaBackend, changeOrigin: true },
      '/adherence': { target: javaBackend, changeOrigin: true },
      '/medications': { target: javaBackend, changeOrigin: true },
    },
  },
});
