import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    // GitHub Pages: https://<user>.github.io/university/ 하위 경로 배포
    base: mode === 'production' ? '/university/' : '/',
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
