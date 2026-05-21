/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        signal: {
          safe: '#16a34a',
          soft: '#eab308',
          hard: '#dc2626',
        },
      },
    },
  },
  plugins: [],
};
