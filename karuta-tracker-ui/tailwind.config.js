/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--primary)',
          hover: 'var(--primary-hover)',
          active: 'var(--primary-active)',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          hover: 'var(--secondary-hover)',
          active: 'var(--secondary-active)',
        },
        surface: {
          DEFAULT: 'var(--surface)',
          disabled: 'var(--surface-disabled)',
        },
        bg: 'var(--bg)',
        text: {
          DEFAULT: 'var(--text)',
          muted: 'var(--text-muted)',
          inverse: 'var(--text-inverse)',
          placeholder: 'var(--text-placeholder)',
          disabled: 'var(--text-disabled)',
        },
        border: {
          strong: 'var(--border-strong)',
          subtle: 'var(--border-subtle)',
        },
        focus: 'var(--focus)',
        status: {
          success: 'var(--status-success)',
          warning: 'var(--status-warning)',
          danger: 'var(--status-danger)',
          info: 'var(--status-info)',
          'success-surface': 'var(--status-success-surface)',
          'warning-surface': 'var(--status-warning-surface)',
          'danger-surface': 'var(--status-danger-surface)',
          'info-surface': 'var(--status-info-surface)',
        },
      },
    },
  },
  plugins: [],
}
