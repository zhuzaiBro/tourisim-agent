<template>
  <RouterView />
</template>

<style>
*, *::before, *::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  /* UI.md — Cyberpunk Terminal Elegance */
  --bg-primary: #0a0e27;
  --bg-secondary: #1a1f3a;
  --bg-tertiary: #242d4a;
  --accent-green: #00ff88;
  --accent-cyan: #00d4ff;
  --accent-green-rgb: 0, 255, 136;
  --accent-cyan-rgb: 0, 212, 255;
  --text-primary: #e8f0ff;
  --text-secondary: #a8b8d8;
  --text-muted: #6b7a99;
  --border-light: rgba(255, 255, 255, 0.1);
  --border-medium: rgba(255, 255, 255, 0.2);
  --glass-bg: rgba(26, 31, 58, 0.45);
  --glass-border: rgba(255, 255, 255, 0.1);
  --transition-smooth: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);

  /* 兼容现有组件变量名 */
  --term-bg: var(--bg-primary);
  --term-bg-2: var(--bg-secondary);
  --term-bg-3: var(--bg-tertiary);
  --term-green: var(--accent-green);
  --term-cyan: var(--accent-cyan);
  --term-amber: var(--accent-cyan);

  --cream: var(--bg-primary);
  --cream-200: var(--bg-secondary);
  --cream-300: var(--bg-tertiary);
  --forest: var(--accent-green);
  --forest-600: #00cc6a;
  --forest-400: #66ffbb;
  --forest-rgb: var(--accent-green-rgb);
  --earth: var(--accent-cyan);
  --earth-light: #66e5ff;
  --gold: var(--accent-green);
  --accent-rgb: var(--accent-green-rgb);
  --text-on-theme: var(--text-primary);
  --text: var(--text-primary);
  --text-2: var(--text-secondary);
  --text-3: var(--text-muted);
  --text-rgb: 232, 240, 255;
  --white: rgba(26, 31, 58, 0.72);
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.35);
  --shadow-md: 0 8px 24px rgba(0, 0, 0, 0.45);
  --shadow-lg: 0 16px 48px rgba(0, 0, 0, 0.55);
  --radius: 8px;
  --radius-lg: 12px;

  /* Element Plus */
  --el-color-primary: #00ff88;
  --el-color-primary-light-3: #33ffaa;
  --el-color-primary-light-5: #1a3d4a;
  --el-color-primary-light-7: #1a2540;
  --el-color-primary-light-8: #141c32;
  --el-color-primary-light-9: #0f1528;
  --el-color-primary-dark-2: #00cc6a;
  --el-bg-color: var(--bg-primary);
  --el-bg-color-overlay: var(--bg-secondary);
  --el-text-color-primary: var(--text-primary);
  --el-text-color-regular: var(--text-secondary);
  --el-border-color: var(--border-light);
  --el-fill-color-blank: var(--bg-secondary);
}

html, body {
  height: 100%;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  font-family: 'Inter', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text);
  background: var(--bg-primary);
  background-image:
    radial-gradient(ellipse at 15% 0%, rgba(0, 212, 255, 0.08) 0%, transparent 55%),
    radial-gradient(ellipse at 85% 100%, rgba(0, 255, 136, 0.06) 0%, transparent 45%);
}

code, pre, .mono,
.sb-prompt, .term-prompt, .vp-term-body,
.form-title, .mh-city-tag {
  font-family: 'JetBrains Mono', 'SF Mono', 'Fira Code', monospace;
}

.sidebar,
.visual-panel,
.a-sidebar {
  color: var(--text-on-theme);
}

::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: var(--bg-secondary); }
::-webkit-scrollbar-thumb { background: var(--bg-tertiary); border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: var(--accent-cyan); }

.el-button--primary {
  --el-button-bg-color: rgba(var(--accent-green-rgb), 0.12) !important;
  --el-button-border-color: var(--accent-green) !important;
  --el-button-hover-bg-color: rgba(var(--accent-green-rgb), 0.22) !important;
  --el-button-hover-border-color: var(--accent-green) !important;
  --el-button-text-color: var(--accent-green) !important;
  transition: var(--transition-smooth) !important;
}

.el-button--primary:active {
  transform: scale(0.95);
}

h1, .h1 {
  font-family: 'JetBrains Mono', monospace;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--accent-green);
}

h2, .h2 {
  font-size: 24px;
  line-height: 1.3;
  font-weight: 600;
  color: var(--text-primary);
}

h3, .h3 {
  font-size: 18px;
  line-height: 1.4;
  font-weight: 500;
  color: var(--text-secondary);
}
</style>
