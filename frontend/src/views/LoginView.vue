<template>
  <div class="auth-page">

    <!-- ─── Left visual panel ──────────────────────────────── -->
    <div class="visual-panel">
      <div class="vp-bg">
        <div class="vp-dots"></div>
        <div class="vp-glow"></div>
        <svg class="vp-terrain" viewBox="0 0 800 200" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0,180 L80,130 L160,155 L260,90 L340,120 L440,60 L520,100 L620,70 L700,110 L800,80 L800,200 L0,200 Z"
            fill="rgba(255,255,255,0.04)" />
          <path d="M0,200 L100,160 L200,175 L320,120 L400,145 L500,100 L580,130 L680,90 L800,120 L800,200 Z"
            fill="rgba(255,255,255,0.06)" />
        </svg>
      </div>

      <div class="vp-content">
        <div class="vp-terminal">
          <div class="vp-term-bar">
            <div class="term-window-dots">
              <span class="dot red"></span>
              <span class="dot yellow"></span>
              <span class="dot green"></span>
            </div>
            <span class="vp-term-title">voyage — bash</span>
          </div>
          <div class="vp-term-body">
            <p><span class="term-green">$</span> cat README.md</p>
            <p class="vp-term-output"># Voyage — AI 旅游规划终端</p>
            <p class="vp-term-output">探索中国，从一场对话开始旅程。</p>
            <p class="vp-term-dim">// RAG 知识库 · 高德实时数据 · 多 Agent 编排</p>
            <p>&nbsp;</p>
            <p><span class="term-green">$</span> voyage cities --list</p>
            <p class="vp-term-output">青岛 · 北京 · 上海 · 成都 · …</p>
            <p>&nbsp;</p>
            <p><span class="term-green">$</span> <span class="term-cursor"></span></p>
          </div>
        </div>
      </div>
    </div>

    <!-- ─── Right form panel ───────────────────────────────── -->
    <div class="form-panel">
      <div class="form-inner">

        <div class="form-nav">
          <router-link to="/register" class="form-nav-link">还没账号？注册</router-link>
        </div>

        <div class="form-body">
          <h2 class="form-title"><span class="term-green">$</span> auth login</h2>
          <p class="form-sub term-comment">登录账号，继续规划旅程</p>

          <form @submit.prevent="handleLogin" class="f-form">
            <div class="f-field">
              <label class="f-label">邮箱地址</label>
              <input
                v-model="email"
                class="f-input"
                type="email"
                placeholder="name@example.com"
                autocomplete="email"
              />
            </div>

            <div class="f-field">
              <label class="f-label">
                密码
                <a href="#" class="f-hint-link" @click.prevent>忘记密码？</a>
              </label>
              <input
                v-model="password"
                class="f-input"
                type="password"
                placeholder="输入密码"
                autocomplete="current-password"
              />
            </div>

            <p v-if="errorMsg" class="f-error">{{ errorMsg }}</p>

            <button type="submit" class="f-btn-primary" :disabled="loading">
              <span v-if="loading" class="btn-loading">登录中...</span>
              <template v-else>登录 <span class="btn-arrow">→</span></template>
            </button>
          </form>

          <p class="form-register-link">
            还没有账号？
            <router-link to="/register">立即注册</router-link>
          </p>
        </div>

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

const email = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!email.value || !password.value) {
    errorMsg.value = '请填写邮箱和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await authApi.login({ email: email.value, password: password.value })
    const user: User = { userId: res.userId, username: res.username, email: res.email }
    authStore.setAuth(res.token, user)
    router.push('/app')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '登录失败，请检查邮箱和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────── */
.auth-page {
  display: flex;
  min-height: 100vh;
}

/* ── Visual Panel ───────────────────────────────────── */
.visual-panel {
  position: relative;
  flex: 0 0 50%;
  background: #0a0a0f;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-right: 2px solid var(--forest);
}

@media (max-width: 768px) {
  .visual-panel { display: none; }
  .form-panel { flex: 1; }
}

.vp-bg {
  position: absolute;
  inset: 0;
}

.vp-dots,
.vp-glow,
.vp-terrain { display: none; }

.vp-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  padding: 48px 40px;
}

.vp-terminal {
  width: 100%;
  max-width: 520px;
  border: 1px solid var(--cream-300);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.vp-term-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--cream-200);
  border-bottom: 1px solid var(--cream-300);
}

.vp-term-title {
  font-size: 11px;
  color: var(--text-3);
}

.vp-term-body {
  padding: 24px 20px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text);
  background: var(--cream);
}

.vp-term-output { color: var(--text-2); padding-left: 0; }
.vp-term-dim { color: var(--forest-400); font-size: 12px; }

/* ── Form Panel ─────────────────────────────────────── */
.form-panel {
  flex: 0 0 50%;
  background: var(--cream);
  display: flex;
  flex-direction: column;
}

.form-inner {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 32px 48px 48px;
}

.form-nav {
  display: flex;
  justify-content: flex-end;
  margin-bottom: auto;
  padding-bottom: 40px;
}

.form-nav-link {
  font-size: 14px;
  color: var(--text-2);
  text-decoration: none;
  border-bottom: 1px solid var(--cream-300);
  padding-bottom: 1px;
  transition: color 0.2s, border-color 0.2s;
}

.form-nav-link:hover {
  color: var(--forest);
  border-color: var(--forest);
}

.form-body {
  max-width: 380px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  gap: 8px;
}

.form-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.3;
  margin-bottom: 6px;
}

.form-sub {
  font-size: 14px;
  color: var(--text-3);
  margin-bottom: 24px;
}

.f-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.f-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.f-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-2);
}

.f-hint-link {
  font-size: 12px;
  text-transform: none;
  letter-spacing: 0;
  color: var(--text-3);
  text-decoration: none;
  transition: color 0.2s;
}

.f-hint-link:hover { color: var(--earth); }

.f-input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--cream-300);
  border-radius: var(--radius);
  background: var(--cream-200);
  font-size: 13px;
  color: var(--text);
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  -webkit-appearance: none;
}

.f-input::placeholder { color: var(--text-3); }

.f-input:focus {
  border-color: var(--forest);
  box-shadow: 0 0 0 3px rgba(var(--forest-rgb),0.08);
}

.f-btn-primary {
  width: 100%;
  height: 42px;
  background: var(--forest);
  color: var(--text-on-theme);
  border: 1px solid var(--forest-400);
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.2s, transform 0.1s;
  margin-top: 4px;
}

.f-btn-primary:hover { background: var(--forest-600); }
.f-btn-primary:active { transform: scale(0.99); }

.btn-arrow {
  font-size: 18px;
  transition: transform 0.2s;
}

.f-btn-primary:hover .btn-arrow { transform: translateX(3px); }

.f-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-3);
  font-size: 12px;
}

.f-divider::before,
.f-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--cream-300);
}

.f-btn-ghost {
  width: 100%;
  height: 46px;
  background: transparent;
  color: var(--text-2);
  border: 1.5px solid var(--cream-300);
  border-radius: var(--radius);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.f-btn-ghost:hover {
  border-color: var(--forest);
  color: var(--forest);
}

.form-register-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-3);
  margin-top: 8px;
}

.form-register-link a {
  color: var(--forest);
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}

.form-register-link a:hover { border-color: var(--forest); }

.f-error {
  font-size: 12px;
  color: #f87171;
  background: rgba(220, 38, 38, 0.1);
  border: 1px solid #dc2626;
  border-radius: var(--radius);
  padding: 10px 14px;
  margin: 0;
}
.f-error::before { content: 'ERR: '; color: #dc2626; font-weight: 700; }

.f-btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-loading {
  font-size: 14px;
}
</style>
