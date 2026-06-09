<template>
  <div class="auth-page">

    <!-- ─── Left visual panel ──────────────────────────────── -->
    <div class="visual-panel">
      <div class="vp-bg">
        <div class="vp-dots"></div>
        <div class="vp-glow"></div>
        <svg class="vp-terrain" viewBox="0 0 800 200" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0,140 L60,100 L130,125 L230,70 L320,100 L420,45 L510,80 L600,55 L700,85 L800,65 L800,200 L0,200 Z"
            fill="rgba(var(--accent-rgb),0.12)" />
          <path d="M0,200 L100,160 L200,175 L320,120 L400,145 L500,100 L580,130 L680,90 L800,120 L800,200 Z"
            fill="rgba(255,255,255,0.05)" />
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
            <span class="vp-term-title">voyage — signup</span>
          </div>
          <div class="vp-term-body">
            <p><span class="term-green">$</span> voyage register --help</p>
            <p class="vp-term-output">创建账号，获取专属 AI 旅游助手</p>
            <p class="vp-term-dim">// 深度知识库 · 个性推荐 · 智能行程</p>
            <p>&nbsp;</p>
            <div class="vp-features">
              <div class="feature-item" v-for="f in features" :key="f.text">
                <span class="feature-icon">{{ f.icon }}</span>
                <span class="vp-term-output">{{ f.text }}</span>
              </div>
            </div>
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
          <router-link to="/login" class="form-nav-link">已有账号？登录</router-link>
        </div>

        <div class="form-body">
          <h2 class="form-title"><span class="term-green">$</span> auth register</h2>
          <p class="form-sub term-comment">加入旅途，开始智能旅游规划</p>

          <form @submit.prevent="handleRegister" class="f-form">
            <div class="f-row">
              <div class="f-field">
                <label class="f-label">姓名</label>
                <input v-model="name" class="f-input" type="text" placeholder="你的姓名" autocomplete="name" />
              </div>
            </div>

            <div class="f-field">
              <label class="f-label">邮箱地址</label>
              <input v-model="email" class="f-input" type="email" placeholder="name@example.com" autocomplete="email" />
            </div>

            <div class="f-field">
              <label class="f-label">设置密码</label>
              <input v-model="password" class="f-input" type="password" placeholder="至少 8 位" autocomplete="new-password" />
              <div class="f-strength" v-if="password.length > 0">
                <div class="strength-bar">
                  <div class="strength-fill" :class="strengthClass" :style="{ width: strengthWidth }"></div>
                </div>
                <span class="strength-label" :class="strengthClass">{{ strengthLabel }}</span>
              </div>
            </div>

            <div class="f-field">
              <label class="f-label">确认密码</label>
              <input v-model="confirmPassword" class="f-input" type="password" placeholder="再次输入密码" autocomplete="new-password" />
            </div>

            <label class="f-checkbox">
              <input type="checkbox" v-model="agreed" />
              <span>我已阅读并同意 <a href="#" @click.prevent>服务条款</a> 和 <a href="#" @click.prevent>隐私政策</a></span>
            </label>

            <p v-if="errorMsg" class="f-error">{{ errorMsg }}</p>

            <button type="submit" class="f-btn-primary" :disabled="!agreed || loading">
              <span v-if="loading" class="btn-loading">注册中...</span>
              <template v-else>注册账号 <span class="btn-arrow">→</span></template>
            </button>
          </form>

          <p class="form-login-link">
            已有账号？
            <router-link to="/login">立即登录</router-link>
          </p>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import type { User } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const agreed = ref(false)
const loading = ref(false)
const errorMsg = ref('')

const features = [
  { icon: '✦', text: '多城市联游智能规划' },
  { icon: '✦', text: '深度知识库，真实旅游数据' },
  { icon: '✦', text: '个性化行程定制推荐' },
  { icon: '✦', text: '流式 AI 对话，实时回答' },
]

const strengthScore = computed(() => {
  const p = password.value
  if (!p) return 0
  let s = 0
  if (p.length >= 8) s++
  if (/[A-Z]/.test(p)) s++
  if (/[0-9]/.test(p)) s++
  if (/[^A-Za-z0-9]/.test(p)) s++
  return s
})

const strengthClass = computed(() => {
  const s = strengthScore.value
  if (s <= 1) return 'weak'
  if (s <= 2) return 'fair'
  if (s <= 3) return 'good'
  return 'strong'
})

const strengthWidth = computed(() => `${(strengthScore.value / 4) * 100}%`)

const strengthLabel = computed(() => {
  const map: Record<string, string> = { weak: '弱', fair: '一般', good: '较强', strong: '强' }
  return map[strengthClass.value]
})

async function handleRegister() {
  errorMsg.value = ''
  if (!name.value.trim()) { errorMsg.value = '请输入姓名'; return }
  if (!email.value.trim()) { errorMsg.value = '请输入邮箱'; return }
  if (password.value.length < 6) { errorMsg.value = '密码至少 6 位'; return }
  if (password.value !== confirmPassword.value) { errorMsg.value = '两次密码不一致'; return }
  if (!agreed.value) { errorMsg.value = '请同意服务条款'; return }

  loading.value = true
  try {
    const res = await authApi.register({ username: name.value, email: email.value, password: password.value })
    const user: User = { userId: res.userId, username: res.username, email: res.email }
    authStore.setAuth(res.token, user)
    router.push('/app')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  min-height: 100vh;
}

/* ── Visual Panel ─── */
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

.vp-bg { position: absolute; inset: 0; }

.vp-dots {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255,255,255,0.12) 1px, transparent 1px);
  background-size: 28px 28px;
}

.vp-glow {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(var(--accent-rgb),0.15) 0%, transparent 70%);
  bottom: -200px;
  left: -100px;
}

.vp-terrain {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  width: 100%;
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

.vp-term-title { font-size: 11px; color: var(--text-3); }

.vp-term-body {
  padding: 24px 20px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--text);
  background: var(--cream);
}

.vp-term-output { color: var(--text-2); }
.vp-term-dim { color: var(--forest-400); font-size: 12px; }

.vp-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255,255,255,0.65);
  font-size: 14px;
}

.feature-icon {
  color: var(--gold);
  font-size: 9px;
  flex-shrink: 0;
}

/* ── Form Panel ─── */
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
  padding-bottom: 32px;
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
  gap: 6px;
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
  margin-bottom: 20px;
}

.f-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.f-row { display: flex; gap: 12px; }
.f-row .f-field { flex: 1; }

.f-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.f-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-2);
}

.f-input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: 1.5px solid var(--cream-300);
  border-radius: var(--radius);
  background: var(--white);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 14px;
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

/* Password strength */
.f-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.strength-bar {
  flex: 1;
  height: 3px;
  background: var(--cream-300);
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s, background 0.3s;
}

.strength-fill.weak { background: #e05252; }
.strength-fill.fair { background: var(--gold); }
.strength-fill.good { background: var(--forest-400); }
.strength-fill.strong { background: var(--forest); }

.strength-label {
  font-size: 11px;
  font-weight: 500;
}

.strength-label.weak { color: #e05252; }
.strength-label.fair { color: var(--gold); }
.strength-label.good { color: var(--forest-400); }
.strength-label.strong { color: var(--forest); }

/* Checkbox */
.f-checkbox {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  line-height: 1.5;
}

.f-checkbox input[type="checkbox"] {
  flex-shrink: 0;
  margin-top: 2px;
  width: 15px;
  height: 15px;
  accent-color: var(--forest);
}

.f-checkbox a {
  color: var(--forest);
  text-decoration: none;
  border-bottom: 1px solid transparent;
}

.f-checkbox a:hover { border-color: var(--forest); }

.f-btn-primary {
  width: 100%;
  height: 46px;
  background: var(--forest);
  color: #fff;
  border: none;
  border-radius: var(--radius);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.2s, transform 0.1s, opacity 0.2s;
}

.f-btn-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.f-btn-primary:not(:disabled):hover { background: var(--forest-600); }
.f-btn-primary:not(:disabled):active { transform: scale(0.99); }

.btn-arrow {
  font-size: 18px;
  transition: transform 0.2s;
}

.f-btn-primary:not(:disabled):hover .btn-arrow { transform: translateX(3px); }

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
  height: 44px;
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

.form-login-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-3);
  margin-top: 4px;
}

.form-login-link a {
  color: var(--forest);
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}

.form-login-link a:hover { border-color: var(--forest); }

.f-error {
  font-size: 13px;
  color: #e53e3e;
  background: #fff5f5;
  border: 1px solid #fed7d7;
  border-radius: var(--radius);
  padding: 10px 14px;
  margin: 0;
}

.f-btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-loading { font-size: 14px; }
</style>
