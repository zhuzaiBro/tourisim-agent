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
  /* Terminal dark + burgundy accent #89394D */
  --term-bg:        #0a0a0f;
  --term-bg-2:      #12121a;
  --term-bg-3:      #1a1a24;
  --term-green:     #6fcf6f;
  --term-amber:     #e8b86d;
  --term-cyan:      #6ebfbf;

  --cream:          #0a0a0f;
  --cream-200:      #12121a;
  --cream-300:      #2d2430;
  --forest:         #89394D;
  --forest-600:     #6E2D3E;
  --forest-400:     #A85568;
  --forest-rgb:     137, 57, 77;
  --earth:          #e8b86d;
  --earth-light:    #c9a87c;
  --gold:           #6fcf6f;
  --accent-rgb:     110, 207, 111;
  --text-on-theme:  #FAF6F0;
  --text:           #e2dcd8;
  --text-2:         #a89a9c;
  --text-3:         #6a5c5e;
  --text-rgb:       226, 220, 216;
  --white:          #16161f;
  --shadow-sm:      0 1px 4px rgba(0, 0, 0, 0.35);
  --shadow-md:      0 4px 20px rgba(0, 0, 0, 0.45);
  --shadow-lg:      0 8px 40px rgba(0, 0, 0, 0.55);
  --radius:         4px;
  --radius-lg:      6px;

  /* Element Plus */
  --el-color-primary: #89394D;
  --el-color-primary-light-3: #A85568;
  --el-color-primary-light-5: #7a3a4d;
  --el-color-primary-light-7: #3d2830;
  --el-color-primary-light-8: #2a1e24;
  --el-color-primary-light-9: #1a1216;
  --el-color-primary-dark-2: #6E2D3E;
  --el-bg-color: #0a0a0f;
  --el-bg-color-overlay: #16161f;
  --el-text-color-primary: #e2dcd8;
  --el-text-color-regular: #a89a9c;
  --el-border-color: #2d2430;
  --el-fill-color-blank: #12121a;
}

html, body {
  height: 100%;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  font-family: 'JetBrains Mono', 'SF Mono', 'Fira Code', 'PingFang SC', 'Microsoft YaHei', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text);
  background: var(--cream);
  background-image:
    radial-gradient(ellipse at 20% 0%, rgba(137, 57, 77, 0.12) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 100%, rgba(110, 207, 111, 0.04) 0%, transparent 40%);
}

.sidebar,
.visual-panel,
.a-sidebar {
  color: var(--text-on-theme);
}

::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: var(--cream-200); }
::-webkit-scrollbar-thumb { background: var(--cream-300); border-radius: 2px; }
::-webkit-scrollbar-thumb:hover { background: var(--forest); }

.el-button--primary {
  --el-button-bg-color: var(--forest) !important;
  --el-button-border-color: var(--forest) !important;
  --el-button-hover-bg-color: var(--forest-600) !important;
  --el-button-hover-border-color: var(--forest-600) !important;
  --el-button-text-color: var(--text-on-theme) !important;
}
</style>
