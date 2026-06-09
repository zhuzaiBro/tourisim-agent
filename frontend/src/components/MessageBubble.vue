<template>
  <div class="msg" :class="[message.role, { error: message.error }]">

    <!-- Avatar -->
    <div class="msg-avatar">
      <div v-if="message.role === 'assistant'" class="avatar ai">
        <span class="avatar-gem">◆</span>
      </div>
      <div v-else class="avatar user">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <circle cx="7" cy="4.5" r="2.5" stroke="currentColor" stroke-width="1.4"/>
          <path d="M1.5 12.5c0-3 2.5-4.5 5.5-4.5s5.5 1.5 5.5 4.5"
            stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
      </div>
    </div>

    <!-- Content -->
    <div class="msg-body">
      <div class="msg-role-label">
        <span v-if="message.role === 'assistant'" class="term-green">voyage@ai</span>
        <span v-else class="term-amber">user@local</span>
      </div>

      <!-- Loading -->
      <div v-if="message.loading" class="msg-loading">
        <span></span><span></span><span></span>
      </div>

      <!-- Bubble -->
      <div
        v-else
        class="msg-bubble"
        :class="message.role"
        v-html="renderedContent"
        @click="handleBubbleClick"
      />

      <!-- Sources -->
      <div v-if="message.sources && message.sources.length > 0" class="sources">
        <button class="sources-toggle" @click="showSources = !showSources">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M1 1.5h10M1 6h10M1 10.5h6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          参考来源 ({{ message.sources.length }})
          <span class="toggle-chevron" :class="{ open: showSources }">›</span>
        </button>

        <div v-show="showSources" class="sources-content">
          <div class="sources-tags">
            <span
              v-for="(src, idx) in message.sources"
              :key="idx"
              class="source-tag"
              :title="src.excerpt"
            >
              {{ cityLabel(src.city) }} · {{ categoryLabel(src.category) }}
            </span>
          </div>
          <div class="source-details">
            <div v-for="(src, idx) in message.sources" :key="idx" class="source-detail-item">
              <span class="detail-num">[{{ idx + 1 }}]</span>
              <div>
                <div class="detail-source">{{ src.source }}</div>
                <div class="detail-excerpt">{{ src.excerpt }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Action bar (assistant only, not loading) -->
      <div v-if="message.role === 'assistant' && !message.loading" class="msg-actions">
        <button
          class="action-btn"
          :class="{ active: copied }"
          @click="copyMessage"
          :title="copied ? '已复制' : '复制'"
        >
          <svg v-if="!copied" width="13" height="13" viewBox="0 0 13 13" fill="none">
            <rect x="3.5" y="3.5" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
            <path d="M9.5 3.5V2A.5.5 0 0 0 9 1.5H2A.5.5 0 0 0 1.5 2v7a.5.5 0 0 0 .5.5h1.5"
              stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
          <svg v-else width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M2 7l3 3 6-6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ copied ? '已复制' : '复制' }}
        </button>

        <button
          class="action-btn"
          @click="$emit('regenerate', message.id)"
          title="重新生成"
        >
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M11 6.5A4.5 4.5 0 1 1 9.5 3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            <path d="M9.5 1v2h2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          重新生成
        </button>

        <button
          class="action-btn"
          :class="{ active: isFav }"
          @click="toggleFavorite"
          title="收藏"
        >
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path
              d="M6.5 2l1.3 2.6 2.9.4-2.1 2 .5 2.9L6.5 8.4 3.9 9.9l.5-2.9-2.1-2 2.9-.4z"
              :fill="isFav ? 'currentColor' : 'none'"
              stroke="currentColor"
              stroke-width="1.2"
              stroke-linejoin="round"
            />
          </svg>
          {{ isFav ? '已收藏' : '收藏' }}
        </button>

        <button
          class="action-btn"
          :class="{ active: savedToNote }"
          @click="saveToNote"
          title="保存到笔记"
        >
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M2 1.5h9a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.2"/>
            <path d="M4 5h5M4 7h5M4 9h2.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
          {{ savedToNote ? '已存入笔记' : '存入笔记' }}
        </button>
      </div>

      <!-- Timestamp -->
      <div class="msg-time">{{ formatTime(message.timestamp) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { ChatMessage } from '@/types'
import { useFavoritesStore } from '@/stores/favorites'
import { useChatStore } from '@/stores/chat'
import { useNotesStore } from '@/stores/notes'

const props = defineProps<{ message: ChatMessage }>()
const emit = defineEmits<{
  regenerate: [id: string]
  'quick-ask': [question: string]
}>()

const favStore = useFavoritesStore()
const chatStore = useChatStore()
const notesStore = useNotesStore()
const showSources = ref(false)
const copied = ref(false)
const savedToNote = ref(false)

marked.setOptions({ breaks: true, gfm: true })

const renderedContent = computed(() => {
  if (!props.message.content) return ''
  const raw = marked.parse(props.message.content) as string
  return DOMPurify.sanitize(raw, {
    ADD_TAGS: ['table', 'thead', 'tbody', 'tr', 'th', 'td'],
  })
})

const isFav = computed(() => favStore.isSaved(props.message.content))

// Get the user question that preceded this assistant message
function getPrecedingQuestion(): string {
  if (!chatStore.currentSession) return ''
  const msgs = chatStore.currentSession.messages
  const idx = msgs.findIndex(m => m.id === props.message.id)
  if (idx <= 0) return ''
  for (let i = idx - 1; i >= 0; i--) {
    if (msgs[i].role === 'user') return msgs[i].content
  }
  return ''
}

async function toggleFavorite() {
  if (isFav.value) {
    const existing = favStore.items.find(i => i.content === props.message.content)
    if (existing) await favStore.remove(existing.id)
  } else {
    const question = getPrecedingQuestion()
    await favStore.add({
      content: props.message.content,
      cities: props.message.cities ?? chatStore.currentSession?.cities ?? [],
      question: question || '(无问题)',
      sessionTitle: chatStore.currentSession?.title ?? '',
    })
  }
}

async function copyMessage() {
  await navigator.clipboard.writeText(props.message.content)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

async function saveToNote() {
  const question = getPrecedingQuestion()
  const prefix = question ? `> 来自问题：${question}\n\n` : ''
  await notesStore.createNote(prefix + props.message.content, props.message.content)
  savedToNote.value = true
  setTimeout(() => { savedToNote.value = false }, 3000)
}

/** Click on bold terms in AI bubble → emit quick-ask */
function handleBubbleClick(e: MouseEvent) {
  if (props.message.role !== 'assistant') return
  const target = e.target as HTMLElement
  if (target.tagName === 'STRONG' || target.tagName === 'B') {
    const term = target.textContent?.trim()
    if (term) emit('quick-ask', `告诉我更多关于「${term}」的旅游信息`)
  }
}

function formatTime(date: Date): string {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(date))
}

function cityLabel(code: string): string {
  const map: Record<string, string> = {
    qingdao: '青岛', beijing: '北京', shanghai: '上海',
    hangzhou: '杭州', chengdu: '成都',
  }
  return map[code] ?? code
}

function categoryLabel(cat: string): string {
  const map: Record<string, string> = {
    attraction: '景点', food: '美食', transport: '交通',
    accommodation: '住宿', festival: '节庆',
  }
  return map[cat] ?? cat
}
</script>

<style scoped>
.msg {
  display: flex;
  gap: 12px;
  padding: 10px 24px;
  animation: msgIn 0.25s ease;
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.msg.user { flex-direction: row-reverse; }

.avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
}

.avatar.ai {
  background: rgba(137, 57, 77, 0.3);
  border: 1px solid var(--forest);
  color: var(--term-green);
}
.avatar-gem { display: none; }
.avatar.ai::after { content: 'AI'; }

.avatar.user {
  background: var(--cream-200);
  color: var(--term-amber);
  border: 1px solid var(--cream-300);
}
.avatar.user::after { content: 'ME'; font-size: 9px; }
.avatar.user svg { display: none; }

.msg-body {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.msg.user .msg-body { align-items: flex-end; }

.msg-role-label {
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--text-3);
  font-weight: 500;
}

/* Loading dots */
.msg-loading {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
  background: var(--white);
  border-radius: var(--radius);
  border: 1px solid var(--cream-300);
}

.msg-loading span {
  width: 6px;
  height: 10px;
  border-radius: 1px;
  background: var(--term-green);
  animation: dot 1.4s infinite ease-in-out both;
}

.msg-loading span:nth-child(1) { animation-delay: -0.32s; }
.msg-loading span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dot {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* Bubbles */
.msg-bubble {
  padding: 12px 16px;
  border-radius: var(--radius);
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-bubble.user {
  background: rgba(137, 57, 77, 0.2);
  color: var(--text);
  border: 1px solid var(--forest);
  border-left: 3px solid var(--forest);
}

.msg-bubble.assistant {
  background: var(--white);
  color: var(--text);
  border: 1px solid var(--cream-300);
  border-left: 3px solid var(--term-green);
}

/* Clickable bold terms */
.msg-bubble.assistant :deep(strong),
.msg-bubble.assistant :deep(b) {
  color: var(--term-green);
  cursor: pointer;
  border-bottom: 1px dotted var(--term-green);
  transition: border-color 0.15s;
}

.msg-bubble.assistant :deep(strong):hover,
.msg-bubble.assistant :deep(b):hover {
  border-bottom-style: solid;
}

.error .msg-bubble.assistant {
  background: rgba(220, 38, 38, 0.1);
  border-color: #dc2626;
  border-left-color: #dc2626;
  color: #f87171;
}

/* Markdown */
.msg-bubble :deep(h1),
.msg-bubble :deep(h2),
.msg-bubble :deep(h3) {
  font-weight: 600;
  margin: 12px 0 6px;
  color: var(--forest-400);
}

.msg-bubble :deep(h1) { font-size: 20px; }
.msg-bubble :deep(h2) { font-size: 18px; }
.msg-bubble :deep(h3) { font-size: 16px; }

.msg-bubble.user :deep(h1),
.msg-bubble.user :deep(h2),
.msg-bubble.user :deep(h3) { color: rgba(255,255,255,0.95); }

.msg-bubble :deep(ul),
.msg-bubble :deep(ol) { padding-left: 18px; margin: 6px 0; }
.msg-bubble :deep(li) { margin: 3px 0; }
.msg-bubble :deep(strong) { font-weight: 600; }
.msg-bubble :deep(em) { font-style: italic; }

.msg-bubble :deep(code) {
  background: var(--cream-200);
  color: var(--term-green);
  padding: 1px 6px;
  border-radius: 2px;
  font-size: 12px;
  border: 1px solid var(--cream-300);
}

.msg-bubble.user :deep(code) {
  background: rgba(0, 0, 0, 0.25);
  color: var(--term-amber);
  border-color: var(--forest);
}

.msg-bubble :deep(blockquote) {
  border-left: 3px solid var(--cream-300);
  padding-left: 12px;
  color: var(--text-2);
  margin: 8px 0;
}

.msg-bubble :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}

.msg-bubble :deep(th),
.msg-bubble :deep(td) {
  border: 1px solid var(--cream-300);
  padding: 7px 12px;
  text-align: left;
}

.msg-bubble :deep(th) {
  background: var(--cream);
  font-weight: 600;
  color: var(--text-2);
}

.msg-bubble :deep(hr) {
  border: none;
  border-top: 1px solid var(--cream-300);
  margin: 10px 0;
}

.msg-bubble :deep(p) { margin: 4px 0; }

/* Sources */
.sources { max-width: 100%; }

.sources-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 6px;
  padding: 5px 10px;
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.sources-toggle:hover { border-color: var(--term-green); color: var(--term-green); }

.toggle-chevron {
  font-size: 16px;
  line-height: 1;
  transition: transform 0.2s;
  display: inline-block;
}

.toggle-chevron.open { transform: rotate(90deg); }

.sources-content {
  margin-top: 8px;
  background: var(--cream);
  border: 1.5px solid var(--cream-300);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sources-tags { display: flex; flex-wrap: wrap; gap: 6px; }

.source-tag {
  font-size: 11px;
  padding: 3px 9px;
  background: var(--white);
  border: 1px solid var(--cream-300);
  border-radius: 12px;
  color: var(--text-2);
}

.source-details { display: flex; flex-direction: column; gap: 8px; }

.source-detail-item { display: flex; gap: 8px; font-size: 12px; }

.detail-num {
  color: var(--text-3);
  flex-shrink: 0;
  font-weight: 500;
  margin-top: 1px;
}

.detail-source { font-weight: 500; color: var(--text-2); margin-bottom: 2px; }
.detail-excerpt { color: var(--text-3); line-height: 1.5; font-size: 11.5px; }

/* Action bar */
.msg-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.msg:hover .msg-actions { opacity: 1; }

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 11.5px;
  color: var(--text-3);
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover { border-color: var(--term-green); color: var(--term-green); }

.action-btn.active {
  background: rgba(var(--forest-rgb),0.07);
  border-color: var(--forest);
  color: var(--forest);
}

/* Timestamp */
.msg-time { font-size: 10.5px; color: var(--text-3); padding: 0 2px; }
</style>
