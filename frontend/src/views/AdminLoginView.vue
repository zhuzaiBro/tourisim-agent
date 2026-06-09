<template>
  <div class="al-root">
    <!-- Ambient grid background -->
    <div class="al-grid" aria-hidden="true"></div>
    <div class="al-glow" aria-hidden="true"></div>

    <div class="al-card">

      <!-- Header -->
      <div class="al-head">
        <div class="al-badge">
          <span class="al-badge-dot"></span>
          <span>ADMIN</span>
        </div>
        <div class="al-logo">
          <span class="al-logo-mark">◆</span>
          <span class="al-logo-name">Voyage<em>OS</em></span>
        </div>
        <h1 class="al-title">管理员登录</h1>
        <p class="al-desc">仅限授权管理员访问后台系统</p>
      </div>

      <!-- Form (login mode) -->
      <form v-if="mode === 'login'" class="al-form" @submit.prevent="handleLogin">
        <div class="al-field">
          <label class="al-label">管理员邮箱</label>
          <input
            v-model="email"
            type="email"
            class="al-input"
            placeholder="admin@example.com"
            autocomplete="email"
            :disabled="loading"
          />
        </div>
        <div class="al-field">
          <label class="al-label">密码</label>
          <input
            v-model="password"
            type="password"
            class="al-input"
            placeholder="••••••••"
            autocomplete="current-password"
            :disabled="loading"
          />
        </div>

        <Transition name="err">
          <div v-if="errorMsg" class="al-error">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <circle cx="6.5" cy="6.5" r="5.5" stroke="currentColor" stroke-width="1.3"/>
              <path d="M6.5 4v3M6.5 8.5v.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            </svg>
            {{ errorMsg }}
          </div>
        </Transition>

        <button type="submit" class="al-btn" :disabled="loading">
          <span v-if="loading" class="al-spinner"></span>
          <span v-else>进入管理后台</span>
        </button>
      </form>

      <!-- Setup mode (first-run admin creation) -->
      <form v-else-if="mode === 'setup'" class="al-form" @submit.prevent="handleSetup">
        <div class="al-setup-notice">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.3"/>
            <path d="M7 5v3M7 9.5v.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          首次运行 — 创建管理员账号
        </div>
        <div class="al-field">
          <label class="al-label">初始化密钥</label>
          <input v-model="setupKey" type="password" class="al-input" placeholder="ADMIN_SETUP_KEY" :disabled="loading"/>
        </div>
        <div class="al-field">
          <label class="al-label">用户名</label>
          <input v-model="username" type="text" class="al-input" placeholder="管理员名称" :disabled="loading"/>
        </div>
        <div class="al-field">
          <label class="al-label">邮箱</label>
          <input v-model="email" type="email" class="al-input" placeholder="admin@example.com" :disabled="loading"/>
        </div>
        <div class="al-field">
          <label class="al-label">密码 <span class="al-label-hint">（至少 8 位）</span></label>
          <input v-model="password" type="password" class="al-input" placeholder="••••••••" :disabled="loading"/>
        </div>

        <Transition name="err">
          <div v-if="errorMsg" class="al-error">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <circle cx="6.5" cy="6.5" r="5.5" stroke="currentColor" stroke-width="1.3"/>
              <path d="M6.5 4v3M6.5 8.5v.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            </svg>
            {{ errorMsg }}
          </div>
        </Transition>

        <button type="submit" class="al-btn" :disabled="loading">
          <span v-if="loading" class="al-spinner"></span>
          <span v-else>创建管理员账号</span>
        </button>
      </form>

      <!-- Footer links -->
      <div class="al-footer">
        <button
          class="al-toggle-mode"
          type="button"
          @click="toggleMode"
        >
          {{ mode === 'login' ? '首次初始化管理员 →' : '← 已有账号，去登录' }}
        </button>
        <router-link to="/login" class="al-back">返回用户登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import type { User } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'login' | 'setup'>('login')
const email = ref('')
const password = ref('')
const username = ref('')
const setupKey = ref('')
const loading = ref(false)
const errorMsg = ref('')

function toggleMode() {
  mode.value = mode.value === 'login' ? 'setup' : 'login'
  errorMsg.value = ''
}

async function handleLogin() {
  if (!email.value || !password.value) { errorMsg.value = '请填写邮箱和密码'; return }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await authApi.adminLogin({ email: email.value, password: password.value })
    const user: User = { userId: res.userId, username: res.username, email: res.email, role: res.role }
    authStore.setAuth(res.token, user)
    router.push('/admin')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function handleSetup() {
  if (!setupKey.value || !username.value || !email.value || !password.value) {
    errorMsg.value = '请填写所有字段'
    return
  }
  if (password.value.length < 8) { errorMsg.value = '密码至少 8 位'; return }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await authApi.adminSetup({
      setupKey: setupKey.value,
      username: username.value,
      email: email.value,
      password: password.value,
    })
    const user: User = { userId: res.userId, username: res.username, email: res.email, role: res.role }
    authStore.setAuth(res.token, user)
    router.push('/admin')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '初始化失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=JetBrains+Mono:wght@400;500&display=swap');

/* ── Root ─────────────────────────────────────────────── */
.al-root {
  min-height: 100vh;
  background: #09090B;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
}

/* Ambient grid */
.al-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(245,158,11,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(245,158,11,0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 60% at 50% 50%, black, transparent);
}

/* Glow orb */
.al-glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(245,158,11,0.06) 0%, transparent 65%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

/* ── Card ─────────────────────────────────────────────── */
.al-card {
  position: relative;
  width: 100%;
  max-width: 380px;
  background: #0D0D10;
  border: 1px solid #1C1C21;
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 0 0 1px rgba(245,158,11,0.04), 0 24px 60px rgba(0,0,0,0.5);
}

/* ── Header ─────────────────────────────────────────────── */
.al-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 28px;
}

.al-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(245,158,11,0.1);
  border: 1px solid rgba(245,158,11,0.2);
  border-radius: 100px;
  padding: 3px 10px 3px 8px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: #F59E0B;
  width: fit-content;
}

.al-badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #F59E0B;
  animation: blink 2s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.al-logo {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 4px;
}

.al-logo-mark { color: #F59E0B; font-size: 10px; }

.al-logo-name {
  font-family: 'Instrument Serif', serif;
  font-size: 17px;
  color: #FAFAFA;
}

.al-logo-name em {
  font-style: italic;
  color: #F59E0B;
}

.al-title {
  font-family: 'Instrument Serif', serif;
  font-size: 26px;
  font-weight: 400;
  color: #FAFAFA;
  letter-spacing: -0.02em;
  line-height: 1.2;
  margin-top: 6px;
}

.al-desc {
  font-size: 12.5px;
  color: #52525B;
  line-height: 1.5;
}

/* ── Form ─────────────────────────────────────────────── */
.al-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.al-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.al-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: #52525B;
  display: flex;
  align-items: center;
  gap: 6px;
}

.al-label-hint {
  font-size: 10px;
  text-transform: none;
  letter-spacing: 0;
  color: #3F3F46;
}

.al-input {
  width: 100%;
  height: 42px;
  background: #111113;
  border: 1px solid #27272A;
  border-radius: 8px;
  padding: 0 13px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  color: #E4E4E7;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
  -webkit-appearance: none;
}

.al-input::placeholder { color: #3F3F46; }

.al-input:focus {
  border-color: rgba(245,158,11,0.5);
  box-shadow: 0 0 0 3px rgba(245,158,11,0.06);
}

.al-input:disabled { opacity: 0.5; cursor: not-allowed; }

/* Error */
.al-error {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: #FCA5A5;
  background: rgba(239,68,68,0.08);
  border: 1px solid rgba(239,68,68,0.2);
  border-radius: 7px;
  padding: 9px 12px;
}

/* Submit button */
.al-btn {
  width: 100%;
  height: 44px;
  background: linear-gradient(135deg, rgba(245,158,11,0.9) 0%, rgba(217,119,6,0.9) 100%);
  border: none;
  border-radius: 8px;
  color: #0D0D10;
  font-size: 13.5px;
  font-weight: 600;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.1s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 4px;
}

.al-btn:hover:not(:disabled) { opacity: 0.92; }
.al-btn:active:not(:disabled) { transform: scale(0.99); }
.al-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Spinner */
.al-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(0,0,0,0.2);
  border-top-color: #0D0D10;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* Setup notice */
.al-setup-notice {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #F59E0B;
  background: rgba(245,158,11,0.07);
  border: 1px solid rgba(245,158,11,0.15);
  border-radius: 7px;
  padding: 9px 12px;
}

/* ── Footer ─────────────────────────────────────────────── */
.al-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #18181B;
}

.al-toggle-mode {
  background: none;
  border: none;
  font-size: 12px;
  color: #52525B;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: color 0.15s;
  padding: 0;
}

.al-toggle-mode:hover { color: #F59E0B; }

.al-back {
  font-size: 12px;
  color: #3F3F46;
  text-decoration: none;
  transition: color 0.15s;
}

.al-back:hover { color: #71717A; }

/* ── Transitions ─────────────────────────────────────────── */
.err-enter-active, .err-leave-active { transition: all 0.2s ease; }
.err-enter-from, .err-leave-to { opacity: 0; transform: translateY(-4px); max-height: 0; }
</style>
