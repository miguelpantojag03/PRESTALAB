import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo, Vite reenvía la API al backend. Así el navegador ve un solo origen y no hace falta CORS,
// igual que en producción, donde lo hace nginx (ver nginx.conf).
const backend = process.env.PRESTALAB_API_URL || 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/v3/api-docs': backend,
      '/swagger-ui': backend,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
});
