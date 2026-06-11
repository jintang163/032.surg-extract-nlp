import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    define: {
      __APP_ENV__: JSON.stringify(env.APP_ENV),
    },
    server: {
      host: '0.0.0.0',
      port: 3000,
      open: false,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
        '/nlp': {
          target: env.VITE_NLP_BASE_URL || 'http://localhost:8000',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/nlp/, ''),
        },
      },
    },
    css: {
      preprocessorOptions: {
        less: {
          modifyVars: {
            '@primary-color': '#1677ff',
          },
          javascriptEnabled: true,
        },
      },
    },
    build: {
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            'react-vendor': ['react', 'react-dom', 'react-router-dom'],
            'antd-vendor': ['antd', '@ant-design/icons'],
          },
        },
      },
    },
  }
})
