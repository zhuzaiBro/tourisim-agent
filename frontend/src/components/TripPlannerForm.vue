<template>
  <div class="planner">

    <!-- Preferences panel -->
    <div class="pref-panel" :class="{ open: prefOpen }">
      <div class="pref-inner">
        <div class="pref-grid">
          <!-- Trip type -->
          <div class="pref-block">
            <div class="pref-label">旅行类型</div>
            <div class="pref-types">
              <button
                v-for="t in tripTypes"
                :key="t.value"
                class="type-chip"
                :class="{ active: preferences.type === t.value }"
                @click="preferences.type = preferences.type === t.value ? undefined : t.value"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <!-- Days -->
          <div class="pref-block">
            <div class="pref-label">旅行天数</div>
            <div class="days-control">
              <button class="days-btn" @click="adjustDays(-1)">−</button>
              <span class="days-value">{{ preferences.days }}</span>
              <button class="days-btn" @click="adjustDays(1)">+</button>
              <span class="days-unit">天</span>
            </div>
          </div>

          <!-- Budget -->
          <div class="pref-block">
            <div class="pref-label">预算</div>
            <div class="pref-types">
              <button
                v-for="b in ['经济', '中等', '豪华']"
                :key="b"
                class="type-chip"
                :class="{ active: preferences.budget === b }"
                @click="preferences.budget = b"
              >
                {{ b }}
              </button>
            </div>
          </div>
        </div>

        <!-- Interests -->
        <div class="pref-block">
          <div class="pref-label">兴趣偏好</div>
          <div class="interest-chips">
            <button
              v-for="i in interestOptions"
              :key="i"
              class="interest-chip"
              :class="{ active: preferences.interests?.includes(i) }"
              @click="toggleInterest(i)"
            >
              {{ i }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Input row -->
    <div class="input-row">
      <!-- Toggle preferences -->
      <button
        class="pref-toggle"
        :class="{ active: prefOpen }"
        @click="prefOpen = !prefOpen"
        title="行程偏好"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M2 4h12M4 8h8M6 12h4"
            stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>

      <!-- Textarea -->
      <div class="input-wrap">
        <span class="input-prompt">$</span>
        <textarea
          v-model="inputText"
          class="msg-input"
          :placeholder="placeholder"
          rows="1"
          ref="textareaRef"
          @keydown.enter.exact.prevent="handleSend"
          @input="autoResize"
        />
      </div>

      <!-- Voice input button -->
      <button
        class="voice-btn"
        :class="{ listening: isListening }"
        @click="toggleVoice"
        :title="isListening ? '停止语音' : '语音输入'"
        type="button"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <rect x="5" y="1" width="6" height="9" rx="3" :fill="isListening ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.4"/>
          <path d="M2.5 8.5A5.5 5.5 0 0 0 13.5 8.5M8 14v-1.5"
            stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
      </button>

      <!-- Send button -->
      <button
        class="send-btn"
        :class="{ loading: chatStore.loading, disabled: !canSend }"
        :disabled="!canSend"
        @click="handleSend"
      >
        <span v-if="!chatStore.loading" class="send-icon">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M14 8H2M9 3l5 5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <span v-else class="loading-dots">
          <i></i><i></i><i></i>
        </span>
      </button>
    </div>

    <div class="input-hint">
      <span class="term-comment">Enter 发送 · Shift+Enter 换行</span>
      <span v-if="prefOpen && hasPreferences" class="hint-pref">
        <span class="hint-sep">·</span>
        已设置偏好
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useCityStore } from '@/stores/city'
import type { TravelPreferences } from '@/types'
import { ElMessage } from 'element-plus'

const emit = defineEmits<{
  send: [payload: { question: string; preferences?: TravelPreferences }]
}>()

const chatStore = useChatStore()
const cityStore = useCityStore()
const inputText = ref('')
const prefOpen = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const preferences = ref<TravelPreferences>({
  type: undefined,
  days: 3,
  budget: '中等',
  interests: [],
})

const tripTypes = [
  { value: 'family',       label: '亲子' },
  { value: 'couple',       label: '情侣' },
  { value: 'food',         label: '美食' },
  { value: 'photography',  label: '摄影' },
  { value: 'elderly',      label: '老人' },
  { value: 'backpacker',   label: '背包' },
]

const interestOptions = [
  '历史文化', '自然风光', '海鲜美食', '网红打卡', '购物逛街',
  '亲水活动', '登山徒步', '博物馆', '夜生活', '当地体验',
]

const canSend = computed(() =>
  !chatStore.loading &&
  inputText.value.trim().length > 0 &&
  cityStore.selectedCities.length > 0
)

const hasPreferences = computed(() =>
  preferences.value.type ||
  preferences.value.interests?.length
)

const placeholder = computed(() => {
  if (cityStore.selectedCities.length === 0) return '请先在左侧选择城市...'
  const names = cityStore.selectedCities
    .map(code => cityStore.cities.find(c => c.code === code)?.nameCn || code)
    .join('、')
  return `问问关于 ${names} 的旅游问题...`
})

function adjustDays(delta: number) {
  const v = (preferences.value.days ?? 3) + delta
  preferences.value.days = Math.max(1, Math.min(14, v))
}

function toggleInterest(i: string) {
  if (!preferences.value.interests) preferences.value.interests = []
  const idx = preferences.value.interests.indexOf(i)
  if (idx === -1) preferences.value.interests.push(i)
  else preferences.value.interests.splice(idx, 1)
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

// ---- Voice input ----
const isListening = ref(false)
let recognition: any = null

function toggleVoice() {
  if (!('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)) {
    ElMessage.warning('当前浏览器不支持语音输入')
    return
  }
  if (isListening.value) {
    recognition?.stop()
    return
  }
  const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = false

  recognition.onstart = () => { isListening.value = true }
  recognition.onresult = (e: any) => {
    const text = e.results[0][0].transcript
    inputText.value += text
    nextTick(autoResize)
  }
  recognition.onerror = () => { isListening.value = false }
  recognition.onend = () => { isListening.value = false }
  recognition.start()
}

function handleSend() {
  if (!canSend.value) return
  const pref = preferences.value
  const hasPref = pref.type || pref.days || pref.interests?.length
  emit('send', {
    question: inputText.value.trim(),
    preferences: hasPref ? { ...pref } : undefined,
  })
  inputText.value = ''
  nextTick(() => {
    if (textareaRef.value) textareaRef.value.style.height = 'auto'
  })
}
</script>

<style scoped>
.planner {
  display: flex;
  flex-direction: column;
  background: var(--cream);
}

/* Preferences panel */
.pref-panel {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
  border-bottom: 1px solid transparent;
}

.pref-panel.open {
  max-height: 300px;
  border-bottom-color: var(--cream-300);
}

.pref-inner {
  padding: 16px 24px;
  background: var(--white);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pref-grid {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.pref-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pref-label {
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 500;
}

/* Trip type chips */
.pref-types {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.type-chip {
  padding: 4px 10px;
  border: 1px solid var(--cream-300);
  border-radius: var(--radius);
  background: var(--cream-200);
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}

.type-chip:hover {
  border-color: var(--term-green);
  color: var(--term-green);
}

.type-chip.active {
  background: rgba(137, 57, 77, 0.25);
  border-color: var(--forest);
  color: var(--term-green);
}

/* Days control */
.days-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.days-btn {
  width: 26px;
  height: 26px;
  background: var(--cream);
  border: 1.5px solid var(--cream-300);
  border-radius: 6px;
  font-size: 16px;
  line-height: 1;
  color: var(--text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.15s, color 0.15s;
}

.days-btn:hover { border-color: var(--forest); color: var(--forest); }

.days-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--term-green);
  min-width: 20px;
  text-align: center;
}

.days-unit {
  font-size: 12px;
  color: var(--text-3);
}

/* Interest chips */
.interest-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.interest-chip {
  padding: 4px 12px;
  border: 1px solid var(--cream-300);
  border-radius: 14px;
  background: transparent;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
}

.interest-chip:hover { border-color: var(--forest); color: var(--forest); }

.interest-chip.active {
  background: rgba(var(--forest-rgb),0.08);
  border-color: var(--forest);
  color: var(--forest);
  font-weight: 500;
}

/* Input row */
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 24px 10px;
}

.pref-toggle {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 10px;
  color: var(--text-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
  margin-bottom: 1px;
}

.pref-toggle:hover,
.pref-toggle.active {
  border-color: var(--forest);
  color: var(--forest);
  background: rgba(var(--forest-rgb),0.06);
}

.input-wrap {
  flex: 1;
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: var(--cream-200);
  border: 1px solid var(--cream-300);
  border-radius: var(--radius);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrap:focus-within {
  border-color: var(--forest);
  box-shadow: 0 0 0 2px rgba(137, 57, 77, 0.2);
}

.input-prompt {
  color: var(--term-green);
  font-weight: 700;
  font-size: 14px;
  line-height: 1.5;
  padding-top: 2px;
  flex-shrink: 0;
  user-select: none;
}

.msg-input {
  flex: 1;
  width: 100%;
  min-height: 24px;
  max-height: 120px;
  padding: 2px 0;
  background: transparent;
  border: none;
  font-size: 13px;
  color: var(--text);
  resize: none;
  outline: none;
  line-height: 1.5;
  display: block;
}

.msg-input::placeholder { color: var(--text-3); }

.voice-btn {
  flex-shrink: 0;
  width: 38px;
  height: 40px;
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 11px;
  color: var(--text-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  margin-bottom: 1px;
}

.voice-btn:hover {
  border-color: var(--forest);
  color: var(--forest);
}

.voice-btn.listening {
  border-color: #dc2626;
  color: #dc2626;
  background: #fef2f2;
  animation: pulse-ring 1s infinite;
}

@keyframes pulse-ring {
  0%, 100% { box-shadow: 0 0 0 0 rgba(220,38,38,0.2); }
  50% { box-shadow: 0 0 0 5px rgba(220,38,38,0); }
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  background: var(--forest);
  border: 1px solid var(--forest-400);
  border-radius: var(--radius);
  color: var(--text-on-theme);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, transform 0.1s, opacity 0.2s;
  margin-bottom: 1px;
}

.send-btn:hover:not(.disabled) { background: var(--forest-600); }
.send-btn:active:not(.disabled) { transform: scale(0.95); }
.send-btn.disabled { opacity: 0.4; cursor: not-allowed; }

.send-icon { display: flex; }

/* Loading dots in send button */
.loading-dots {
  display: flex;
  gap: 3px;
  align-items: center;
}

.loading-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff;
  animation: ldot 1.2s infinite ease-in-out both;
}

.loading-dots i:nth-child(1) { animation-delay: -0.24s; }
.loading-dots i:nth-child(2) { animation-delay: -0.12s; }

@keyframes ldot {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* Hint bar */
.input-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 24px 10px;
  font-size: 11px;
  color: var(--text-3);
}

.hint-sep { color: var(--cream-300); }

.hint-pref { color: var(--forest-400); font-weight: 500; }
</style>
