<template>
  <Teleport to="body">
    <Transition name="notes-backdrop">
      <div v-if="open" class="notes-backdrop" @click.self="$emit('close')" />
    </Transition>
    <Transition name="notes-panel">
      <div v-if="open" class="notes-panel">

        <!-- ── Left: Note List ───────────────────────────── -->
        <aside class="nl-sidebar">
          <div class="nl-sidebar-head">
            <div class="nl-head-left">
              <span class="nl-icon">✦</span>
              <span class="nl-title">笔记本</span>
              <span class="nl-count">{{ notesStore.notes.length }}</span>
            </div>
            <div class="nl-head-right">
              <button class="nl-new-btn" @click="handleNew" title="新建笔记">
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <path d="M6.5 1v11M1 6.5h11" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                </svg>
              </button>
              <button class="nl-close-btn" @click="$emit('close')">
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <path d="M1.5 1.5l10 10M11.5 1.5l-10 10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Search -->
          <div class="nl-search-wrap">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" class="nl-search-icon">
              <circle cx="5.5" cy="5.5" r="4" stroke="currentColor" stroke-width="1.3"/>
              <path d="M8.5 8.5l3 3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
            <input
              v-model="search"
              class="nl-search"
              placeholder="搜索笔记…"
              type="text"
            />
            <button v-if="search" class="nl-search-clear" @click="search = ''">×</button>
          </div>

          <!-- Note list -->
          <div class="nl-list">
            <!-- Pinned section -->
            <div v-if="filteredPinned.length" class="nl-section-label">📌 已置顶</div>
            <div
              v-for="note in filteredPinned"
              :key="note.id"
              class="nl-item"
              :class="{ active: currentId === note.id }"
              @click="selectNote(note.id)"
            >
              <div class="nl-item-title">{{ note.title || '无标题' }}</div>
              <div class="nl-item-preview">{{ previewText(note.content) }}</div>
              <div class="nl-item-meta">
                <div class="nl-item-tags">
                  <span v-for="tag in note.tags.slice(0, 2)" :key="tag" class="nl-tag-mini">{{ tag }}</span>
                </div>
                <span class="nl-item-date">{{ formatDate(note.updatedAt) }}</span>
              </div>
            </div>

            <!-- Divider -->
            <div v-if="filteredPinned.length && filteredUnpinned.length" class="nl-divider" />

            <!-- Unpinned section -->
            <div v-if="filteredPinned.length && filteredUnpinned.length" class="nl-section-label">全部笔记</div>
            <div
              v-for="note in filteredUnpinned"
              :key="note.id"
              class="nl-item"
              :class="{ active: currentId === note.id }"
              @click="selectNote(note.id)"
            >
              <div class="nl-item-title">{{ note.title || '无标题' }}</div>
              <div class="nl-item-preview">{{ previewText(note.content) }}</div>
              <div class="nl-item-meta">
                <div class="nl-item-tags">
                  <span v-for="tag in note.tags.slice(0, 2)" :key="tag" class="nl-tag-mini">{{ tag }}</span>
                </div>
                <span class="nl-item-date">{{ formatDate(note.updatedAt) }}</span>
              </div>
            </div>

            <!-- Empty -->
            <div v-if="filteredAll.length === 0" class="nl-empty">
              <div class="nl-empty-icon">✦</div>
              <div v-if="search">没有匹配「{{ search }}」的笔记</div>
              <div v-else>
                <p>还没有笔记</p>
                <p>点击右上角 <strong>+</strong> 新建</p>
              </div>
            </div>
          </div>
        </aside>

        <!-- ── Right: Editor ─────────────────────────────── -->
        <section class="nl-editor-area" v-if="currentNote">
          <!-- Editor toolbar -->
          <div class="nl-editor-toolbar">
            <div class="nl-toolbar-left">
              <!-- Pin -->
              <button
                class="nl-tool-btn"
                :class="{ active: currentNote.pinned }"
                @click="notesStore.togglePin(currentNote.id)"
                :title="currentNote.pinned ? '取消置顶' : '置顶'"
              >
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <path d="M9 1L12 4 8.5 6.5l-.5 4-2-2L2 12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M7 6L1 12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
                </svg>
                {{ currentNote.pinned ? '已置顶' : '置顶' }}
              </button>

              <!-- Preview toggle -->
              <button
                class="nl-tool-btn"
                :class="{ active: previewMode }"
                @click="previewMode = !previewMode"
                title="预览 Markdown"
              >
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <circle cx="6.5" cy="6.5" r="2" stroke="currentColor" stroke-width="1.3"/>
                  <path d="M1 6.5C2.5 3.5 4.5 2 6.5 2s4 1.5 5.5 4.5C10.5 10 8.5 11 6.5 11S2.5 10 1 6.5z" stroke="currentColor" stroke-width="1.3"/>
                </svg>
                {{ previewMode ? '编辑' : '预览' }}
              </button>
            </div>

            <div class="nl-toolbar-right">
              <!-- Tag input -->
              <div class="nl-tag-editor">
                <div class="nl-tags-row">
                  <span
                    v-for="tag in currentNote.tags"
                    :key="tag"
                    class="nl-tag"
                  >
                    {{ tag }}
                    <button @click="removeTag(tag)" class="nl-tag-remove">×</button>
                  </span>
                  <input
                    v-model="tagInput"
                    class="nl-tag-input"
                    placeholder="+ 标签"
                    @keydown.enter.prevent="addTag"
                    @keydown.comma.prevent="addTag"
                    @keydown.backspace="handleTagBackspace"
                  />
                </div>
              </div>

              <!-- Export dropdown -->
              <div class="nl-export-wrap" ref="exportWrap">
                <button class="nl-tool-btn" @click="showExport = !showExport">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                    <path d="M6.5 1v8M3.5 6l3 3 3-3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M1 10v1.5a.5.5 0 0 0 .5.5h11a.5.5 0 0 0 .5-.5V10" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
                  </svg>
                  导出
                </button>
                <div v-if="showExport" class="nl-export-menu">
                  <button @click="exportMd">
                    <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                      <rect x="1" y="1" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M3.5 4.5v4l1.5-2 1.5 2v-4M8.5 4.5v4h2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    下载 .md 文件
                  </button>
                  <button @click="exportTxt">
                    <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                      <rect x="1" y="1" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M4 4.5h5M6.5 4.5v4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                    </svg>
                    下载 .txt 文件
                  </button>
                  <button @click="copyToClipboard">
                    <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                      <rect x="3" y="3" width="8" height="8" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M9 3V2A.5.5 0 0 0 8.5 1.5h-6A.5.5 0 0 0 2 2v7a.5.5 0 0 0 .5.5H3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                    </svg>
                    {{ copied ? '已复制！' : '复制到剪贴板' }}
                  </button>
                  <button @click="printNote">
                    <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                      <rect x="3" y="1" width="7" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
                      <rect x="2" y="8" width="9" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M3 5v3M10 5v3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                      <circle cx="9.5" cy="6.5" r="0.7" fill="currentColor"/>
                    </svg>
                    打印 / PDF
                  </button>
                </div>
              </div>

              <!-- Delete -->
              <button class="nl-tool-btn danger" @click="handleDelete" title="删除笔记">
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <path d="M1.5 3.5h10M4 3.5V2.5a.5.5 0 0 1 .5-.5h4a.5.5 0 0 1 .5.5v1m1.5 0-.5 7.5a.5.5 0 0 1-.5.5h-6a.5.5 0 0 1-.5-.5L3 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Title input -->
          <div class="nl-title-wrap">
            <input
              v-model="titleModel"
              class="nl-note-title"
              placeholder="笔记标题…"
              @input="debouncedSave"
            />
          </div>

          <!-- Editor or Preview -->
          <div class="nl-content-area">
            <textarea
              v-if="!previewMode"
              v-model="contentModel"
              class="nl-textarea"
              placeholder="开始记录…&#10;&#10;支持 Markdown 格式，如 **加粗**、# 标题、- 列表"
              @input="debouncedSave"
            />
            <div
              v-else
              class="nl-preview"
              v-html="renderedPreview"
            />
          </div>

          <!-- Footer: stats -->
          <div class="nl-editor-footer">
            <div class="nl-stats">
              <span class="nl-stat">{{ currentNote.wordCount }} 字</span>
              <span class="nl-stat-sep">·</span>
              <span class="nl-stat">{{ contentModel.length }} 字符</span>
              <span class="nl-stat-sep">·</span>
              <span class="nl-stat">{{ lineCount }} 行</span>
            </div>
            <div class="nl-save-status">
              <span v-if="saving" class="nl-saving">
                <span class="nl-saving-dot" />
                保存中…
              </span>
              <span v-else class="nl-saved">
                更新于 {{ formatDateTime(currentNote.updatedAt) }}
              </span>
            </div>
          </div>
        </section>

        <!-- Empty state when no note selected -->
        <section v-else class="nl-welcome">
          <div class="nl-welcome-inner">
            <div class="nl-welcome-glyph">✦</div>
            <h2 class="nl-welcome-title">旅途笔记本</h2>
            <p class="nl-welcome-desc">记录你的旅行灵感、攻略与回忆</p>
            <button class="nl-welcome-btn" @click="handleNew">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M7 1v12M1 7h12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
              </svg>
              新建第一篇笔记
            </button>
          </div>
        </section>

      </div>
    </Transition>
  </Teleport>

  <!-- Delete confirmation modal -->
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="showDeleteConfirm" class="nl-modal-backdrop" @click.self="showDeleteConfirm = false">
        <div class="nl-modal">
          <div class="nl-modal-icon">
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
              <path d="M3 5.5h16M7.5 5.5V4a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 .5.5v1.5M18 5.5 17 18a1 1 0 0 1-1 .9H6A1 1 0 0 1 5 18L4 5.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h3 class="nl-modal-title">删除笔记？</h3>
          <p class="nl-modal-desc">「{{ currentNote?.title || '此笔记' }}」将被永久删除，无法恢复。</p>
          <div class="nl-modal-actions">
            <button class="nl-modal-cancel" @click="showDeleteConfirm = false">取消</button>
            <button class="nl-modal-confirm" @click="confirmDelete">确认删除</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useNotesStore } from '@/stores/notes'

const props = defineProps<{ open: boolean; initialContent?: string }>()
const emit = defineEmits<{ close: [] }>()

const notesStore = useNotesStore()

const currentId = ref<string | null>(null)
const search = ref('')
const previewMode = ref(false)
const tagInput = ref('')
const showExport = ref(false)
const showDeleteConfirm = ref(false)
const saving = ref(false)
const copied = ref(false)
const exportWrap = ref<HTMLElement | null>(null)

// Local edit buffers (avoid mutating store directly on every keypress)
const titleModel = ref('')
const contentModel = ref('')

let saveTimer: ReturnType<typeof setTimeout> | null = null

const currentNote = computed(() => {
  if (!currentId.value) return null
  return notesStore.notes.find(n => n.id === currentId.value) ?? null
})

const lineCount = computed(() => contentModel.value.split('\n').length)

const renderedPreview = computed(() => {
  if (!contentModel.value) return '<p class="preview-empty">没有内容可预览</p>'
  const raw = marked.parse(contentModel.value) as string
  return DOMPurify.sanitize(raw, {
    ADD_TAGS: ['table', 'thead', 'tbody', 'tr', 'th', 'td'],
  })
})

// Filter logic
const filteredAll = computed(() => {
  if (!search.value.trim()) return notesStore.sortedNotes
  const q = search.value.toLowerCase()
  return notesStore.sortedNotes.filter(n =>
    n.title.toLowerCase().includes(q) ||
    n.content.toLowerCase().includes(q) ||
    n.tags.some(t => t.toLowerCase().includes(q))
  )
})

const filteredPinned = computed(() => filteredAll.value.filter(n => n.pinned))
const filteredUnpinned = computed(() => filteredAll.value.filter(n => !n.pinned))

// Sync local buffers when currentNote changes
watch(currentNote, (note) => {
  if (note) {
    titleModel.value = note.title
    contentModel.value = note.content
  }
}, { immediate: true })

// When panel opens and has initialContent, create a note from it
watch(() => props.open, async (val) => {
  if (val && props.initialContent) {
    const note = await notesStore.createNote(props.initialContent, props.initialContent)
    await nextTick()
    selectNote(note.id)
  } else if (val && !currentId.value && notesStore.sortedNotes.length > 0) {
    selectNote(notesStore.sortedNotes[0].id)
  }
})

// Click-outside to close export menu
function handleDocClick(e: MouseEvent) {
  if (showExport.value && exportWrap.value && !exportWrap.value.contains(e.target as Node)) {
    showExport.value = false
  }
}
onMounted(() => document.addEventListener('click', handleDocClick))
onBeforeUnmount(() => document.removeEventListener('click', handleDocClick))

function selectNote(id: string) {
  // Save pending changes for current note first
  if (currentId.value) flushSave()
  currentId.value = id
  previewMode.value = false
}

async function handleNew() {
  const note = await notesStore.createNote()
  currentId.value = note.id
  titleModel.value = note.title
  contentModel.value = note.content
  previewMode.value = false
  nextTick(() => {
    const el = document.querySelector('.nl-note-title') as HTMLInputElement
    el?.focus()
    el?.select()
  })
}

function debouncedSave() {
  saving.value = true
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => flushSave(), 600)
}

function flushSave() {
  if (!currentId.value) return
  notesStore.updateNote(currentId.value, {
    title: titleModel.value || '无标题',
    content: contentModel.value,
  })
  saving.value = false
}

// Tags
function addTag() {
  const tag = tagInput.value.replace(',', '').trim()
  if (!tag || !currentNote.value) return
  if (currentNote.value.tags.includes(tag)) { tagInput.value = ''; return }
  notesStore.updateNote(currentNote.value.id, {
    tags: [...currentNote.value.tags, tag],
  })
  tagInput.value = ''
}

function removeTag(tag: string) {
  if (!currentNote.value) return
  notesStore.updateNote(currentNote.value.id, {
    tags: currentNote.value.tags.filter(t => t !== tag),
  })
}

function handleTagBackspace() {
  if (tagInput.value === '' && currentNote.value && currentNote.value.tags.length > 0) {
    removeTag(currentNote.value.tags[currentNote.value.tags.length - 1])
  }
}

// Delete
function handleDelete() {
  showDeleteConfirm.value = true
  showExport.value = false
}

function confirmDelete() {
  if (!currentId.value) return
  const id = currentId.value
  // Select next note
  const remaining = notesStore.sortedNotes.filter(n => n.id !== id)
  currentId.value = remaining.length > 0 ? remaining[0].id : null
  notesStore.deleteNote(id)
  showDeleteConfirm.value = false
}

// Export helpers
function getExportContent(): string {
  if (!currentNote.value) return ''
  const note = currentNote.value
  const meta = [
    `# ${note.title}`,
    '',
    `> 创建于 ${formatDateTime(note.createdAt)}，更新于 ${formatDateTime(note.updatedAt)}`,
    note.tags.length ? `> 标签：${note.tags.join('、')}` : '',
    '',
    '---',
    '',
  ].filter(l => l !== null).join('\n')
  return meta + contentModel.value
}

function exportMd() {
  if (!currentNote.value) return
  download(getExportContent(), `${currentNote.value.title || 'note'}.md`, 'text/markdown')
  showExport.value = false
}

function exportTxt() {
  if (!currentNote.value) return
  const plain = contentModel.value.replace(/#{1,6}\s/g, '').replace(/[*_`~]/g, '')
  download(plain, `${currentNote.value.title || 'note'}.txt`, 'text/plain')
  showExport.value = false
}

async function copyToClipboard() {
  await navigator.clipboard.writeText(getExportContent())
  copied.value = true
  setTimeout(() => { copied.value = false }, 2500)
  showExport.value = false
}

function printNote() {
  if (!currentNote.value) return
  const win = window.open('', '_blank')
  if (!win) return
  const raw = marked.parse(getExportContent()) as string
  const safe = DOMPurify.sanitize(raw)
  win.document.write(`<!DOCTYPE html><html><head>
    <meta charset="UTF-8">
    <title>${currentNote.value.title}</title>
    <style>
      body { font-family: 'Georgia', serif; max-width: 700px; margin: 48px auto; color: #1a1a1a; line-height: 1.8; }
      h1,h2,h3 { font-weight: 600; } blockquote { border-left: 3px solid #ccc; padding-left: 12px; color: #666; }
      code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
      table { border-collapse: collapse; width: 100%; } td,th { border: 1px solid #ddd; padding: 8px; }
      @media print { body { margin: 32px; } }
    </style>
  </head><body>${safe}</body></html>`)
  win.document.close()
  win.print()
  showExport.value = false
}

function download(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

// Formatting
function previewText(content: string): string {
  const clean = content.replace(/#{1,6}\s/g, '').replace(/[*_`~>\[\]]/g, '').replace(/\n+/g, ' ').trim()
  return clean.length > 60 ? clean.slice(0, 60) + '…' : clean || '（空白笔记）'
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('zh-CN', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,500;0,600;1,400&family=DM+Sans:wght@300;400;500&display=swap');

/* ── Backdrop ──────────────────────────────────────────── */
.notes-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(10, 12, 10, 0.35);
  backdrop-filter: blur(2px);
  z-index: 200;
}

.notes-backdrop-enter-active, .notes-backdrop-leave-active { transition: opacity 0.28s ease; }
.notes-backdrop-enter-from, .notes-backdrop-leave-to { opacity: 0; }

/* ── Panel ─────────────────────────────────────────────── */
.notes-panel {
  position: fixed;
  top: 0;
  right: 0;
  width: min(920px, 88vw);
  height: 100vh;
  background: #FDFAF6;
  display: flex;
  z-index: 201;
  box-shadow: -8px 0 40px rgba(0,0,0,0.12), -1px 0 0 rgba(0,0,0,0.06);
  overflow: hidden;
}

.notes-panel-enter-active, .notes-panel-leave-active { transition: transform 0.32s cubic-bezier(0.32, 0, 0.12, 1); }
.notes-panel-enter-from, .notes-panel-leave-to { transform: translateX(100%); }

/* ── Sidebar ───────────────────────────────────────────── */
.nl-sidebar {
  width: 270px;
  flex-shrink: 0;
  background: #F7F3EC;
  border-right: 1px solid #E8E0D4;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nl-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 18px 16px;
  border-bottom: 1px solid #E8E0D4;
  flex-shrink: 0;
}

.nl-head-left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.nl-icon {
  color: var(--gold);
  font-size: 11px;
}

.nl-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 17px;
  font-weight: 600;
  color: #2C2416;
  letter-spacing: 0.01em;
}

.nl-count {
  font-size: 11px;
  color: var(--gold);
  background: rgba(var(--accent-rgb), 0.1);
  border: 1px solid rgba(var(--accent-rgb), 0.2);
  border-radius: 20px;
  padding: 1px 7px;
  font-weight: 500;
}

.nl-head-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nl-new-btn, .nl-close-btn {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}

.nl-new-btn {
  background: var(--forest);
  color: rgba(255,255,255,0.85);
}
.nl-new-btn:hover { background: #2D4F3C; }

.nl-close-btn {
  background: transparent;
  color: #9C8E7E;
}
.nl-close-btn:hover { background: #EDE6DA; color: #5C4A2E; }

/* Search */
.nl-search-wrap {
  position: relative;
  padding: 12px 14px;
  border-bottom: 1px solid #E8E0D4;
  flex-shrink: 0;
}

.nl-search-icon {
  position: absolute;
  left: 26px;
  top: 50%;
  transform: translateY(-50%);
  color: #B8A898;
}

.nl-search {
  width: 100%;
  height: 34px;
  background: #FDFAF6;
  border: 1px solid #E0D8CC;
  border-radius: 8px;
  padding: 0 28px 0 32px;
  font-size: 13px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  color: #3C2F1E;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.nl-search::placeholder { color: #C4B8A8; }
.nl-search:focus { border-color: var(--gold); }

.nl-search-clear {
  position: absolute;
  right: 22px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: #B8A898;
  font-size: 16px;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
}

/* Note list */
.nl-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.nl-list::-webkit-scrollbar { width: 4px; }
.nl-list::-webkit-scrollbar-track { background: transparent; }
.nl-list::-webkit-scrollbar-thumb { background: #DDD6CA; border-radius: 2px; }

.nl-section-label {
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #C4B8A8;
  padding: 6px 16px 4px;
  font-weight: 500;
}

.nl-divider {
  height: 1px;
  background: #E8E0D4;
  margin: 8px 16px;
}

.nl-item {
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.12s;
  border-radius: 0;
  position: relative;
}
.nl-item:hover { background: #EDE6DA; }
.nl-item.active { background: rgba(var(--forest-rgb), 0.06); }
.nl-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--forest);
  border-radius: 0 2px 2px 0;
}

.nl-item-title {
  font-size: 13.5px;
  font-weight: 500;
  color: #2C2416;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 3px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}

.nl-item-preview {
  font-size: 12px;
  color: #9C8E7E;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
  margin-bottom: 5px;
}

.nl-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.nl-item-tags { display: flex; gap: 4px; flex-wrap: nowrap; overflow: hidden; }

.nl-tag-mini {
  font-size: 10px;
  background: rgba(var(--accent-rgb), 0.1);
  color: #8B6914;
  border-radius: 3px;
  padding: 1px 5px;
  white-space: nowrap;
}

.nl-item-date {
  font-size: 10.5px;
  color: #C4B8A8;
  white-space: nowrap;
  flex-shrink: 0;
}

/* Empty state */
.nl-empty {
  padding: 40px 20px;
  text-align: center;
  color: #C4B8A8;
  font-size: 13px;
  line-height: 1.8;
}
.nl-empty-icon {
  font-size: 22px;
  color: #DDD6CA;
  margin-bottom: 12px;
}
.nl-empty strong { color: #9C8E7E; }

/* ── Editor Area ───────────────────────────────────────── */
.nl-editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #FDFAF6;
}

/* Toolbar */
.nl-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-bottom: 1px solid #EDE6DA;
  flex-shrink: 0;
  gap: 8px;
  background: #FAF6F0;
}

.nl-toolbar-left, .nl-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.nl-tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: none;
  border: 1px solid #E0D8CC;
  border-radius: 6px;
  padding: 5px 10px;
  font-size: 12px;
  color: #7C6E5E;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.14s;
  white-space: nowrap;
}
.nl-tool-btn:hover { border-color: var(--forest); color: var(--forest); }
.nl-tool-btn.active { background: rgba(var(--forest-rgb), 0.07); border-color: var(--forest); color: var(--forest); }
.nl-tool-btn.danger:hover { border-color: #DC2626; color: #DC2626; }

/* Tag editor */
.nl-tag-editor {
  display: flex;
  align-items: center;
}

.nl-tags-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  min-width: 100px;
  max-width: 260px;
}

.nl-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: rgba(var(--accent-rgb), 0.1);
  border: 1px solid rgba(var(--accent-rgb), 0.25);
  color: #8B6914;
  font-size: 11.5px;
  padding: 2px 6px 2px 8px;
  border-radius: 4px;
}

.nl-tag-remove {
  background: none;
  border: none;
  color: var(--gold);
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 0;
  opacity: 0.6;
  transition: opacity 0.12s;
}
.nl-tag-remove:hover { opacity: 1; }

.nl-tag-input {
  background: none;
  border: none;
  outline: none;
  font-size: 12px;
  color: #5C4A2E;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  min-width: 60px;
  max-width: 100px;
}
.nl-tag-input::placeholder { color: #C4B8A8; }

/* Export dropdown */
.nl-export-wrap { position: relative; }

.nl-export-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: #FDFAF6;
  border: 1px solid #E0D8CC;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
  padding: 5px;
  min-width: 170px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.nl-export-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  padding: 8px 11px;
  font-size: 12.5px;
  color: #5C4A2E;
  cursor: pointer;
  border-radius: 7px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  text-align: left;
  transition: background 0.12s;
  width: 100%;
}
.nl-export-menu button:hover { background: #F0EAE0; }

/* Title */
.nl-title-wrap {
  padding: 20px 28px 6px;
  flex-shrink: 0;
  border-bottom: 1px solid #EDE6DA;
}

.nl-note-title {
  width: 100%;
  background: none;
  border: none;
  outline: none;
  font-family: 'Cormorant Garamond', serif;
  font-size: 28px;
  font-weight: 600;
  color: #1A1208;
  letter-spacing: -0.01em;
  line-height: 1.3;
}
.nl-note-title::placeholder { color: #D8CEBC; }

/* Content area */
.nl-content-area {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.nl-textarea {
  flex: 1;
  width: 100%;
  height: 100%;
  background: none;
  border: none;
  outline: none;
  padding: 18px 28px 16px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 14.5px;
  line-height: 1.85;
  color: #2C2416;
  resize: none;
  box-sizing: border-box;
}
.nl-textarea::placeholder { color: #D0C4B4; }

/* Preview */
.nl-preview {
  flex: 1;
  overflow-y: auto;
  padding: 18px 28px 16px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 14.5px;
  line-height: 1.85;
  color: #2C2416;
}
.nl-preview:deep(h1) { font-family: 'Cormorant Garamond', serif; font-size: 26px; font-weight: 600; color: #1A1208; margin: 16px 0 10px; }
.nl-preview:deep(h2) { font-family: 'Cormorant Garamond', serif; font-size: 22px; font-weight: 600; color: #1A1208; margin: 14px 0 8px; }
.nl-preview:deep(h3) { font-family: 'Cormorant Garamond', serif; font-size: 18px; font-weight: 600; color: #2C2416; margin: 12px 0 6px; }
.nl-preview:deep(p) { margin: 6px 0; }
.nl-preview:deep(ul), .nl-preview:deep(ol) { padding-left: 20px; margin: 6px 0; }
.nl-preview:deep(li) { margin: 3px 0; }
.nl-preview:deep(strong) { font-weight: 600; color: #1A1208; }
.nl-preview:deep(em) { font-style: italic; color: #5C4A2E; }
.nl-preview:deep(blockquote) { border-left: 3px solid #D8C9A8; padding-left: 14px; color: #7C6E5E; margin: 10px 0; font-style: italic; }
.nl-preview:deep(code) { background: #EDE6DA; color: #8B4A1C; padding: 1px 6px; border-radius: 4px; font-size: 13px; font-family: 'SF Mono', 'Fira Code', monospace; }
.nl-preview:deep(pre) { background: #EDE6DA; padding: 14px; border-radius: 8px; overflow-x: auto; margin: 10px 0; }
.nl-preview:deep(pre code) { background: none; padding: 0; }
.nl-preview:deep(hr) { border: none; border-top: 1px solid #E8E0D4; margin: 16px 0; }
.nl-preview:deep(table) { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 13.5px; }
.nl-preview:deep(th), .nl-preview:deep(td) { border: 1px solid #E0D8CC; padding: 7px 12px; }
.nl-preview:deep(th) { background: #F0EAE0; font-weight: 600; color: #4A3A28; }
.nl-preview:deep(.preview-empty) { color: #C4B8A8; font-style: italic; }

/* Footer */
.nl-editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 28px;
  border-top: 1px solid #EDE6DA;
  flex-shrink: 0;
  background: #FAF6F0;
}

.nl-stats { display: flex; align-items: center; gap: 6px; }
.nl-stat { font-size: 11.5px; color: #B8A898; }
.nl-stat-sep { font-size: 11px; color: #D8CEBC; }

.nl-save-status { font-size: 11.5px; }
.nl-saving {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--gold);
}
.nl-saving-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gold);
  animation: pulse-dot 1s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}
.nl-saved { color: #C4B8A8; }

/* ── Welcome state ─────────────────────────────────────── */
.nl-welcome {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FDFAF6;
}

.nl-welcome-inner {
  text-align: center;
  padding: 40px;
}

.nl-welcome-glyph {
  font-size: 32px;
  color: #D8C9A8;
  margin-bottom: 16px;
  animation: welcome-float 3s ease-in-out infinite;
}

@keyframes welcome-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.nl-welcome-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 28px;
  font-weight: 600;
  color: #2C2416;
  margin-bottom: 8px;
  letter-spacing: -0.01em;
}

.nl-welcome-desc {
  font-size: 14px;
  color: #9C8E7E;
  margin-bottom: 28px;
  line-height: 1.6;
}

.nl-welcome-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--forest);
  border: none;
  border-radius: 10px;
  padding: 11px 22px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-on-theme);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: background 0.15s, transform 0.1s;
  letter-spacing: 0.01em;
}
.nl-welcome-btn:hover { background: var(--forest-600); }
.nl-welcome-btn:active { transform: scale(0.98); }

/* ── Delete Modal ──────────────────────────────────────── */
.nl-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(10, 12, 10, 0.4);
  backdrop-filter: blur(3px);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nl-modal {
  background: #FDFAF6;
  border-radius: 14px;
  padding: 28px 32px;
  max-width: 360px;
  width: 90%;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}

.nl-modal-icon {
  width: 48px;
  height: 48px;
  background: rgba(220, 38, 38, 0.08);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
  color: #DC2626;
}

.nl-modal-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 600;
  color: #1A1208;
  margin-bottom: 8px;
}

.nl-modal-desc {
  font-size: 13.5px;
  color: #7C6E5E;
  line-height: 1.6;
  margin-bottom: 22px;
}

.nl-modal-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.nl-modal-cancel, .nl-modal-confirm {
  padding: 9px 20px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.14s;
}

.nl-modal-cancel {
  background: none;
  border: 1px solid #E0D8CC;
  color: #5C4A2E;
}
.nl-modal-cancel:hover { background: #F0EAE0; }

.nl-modal-confirm {
  background: #DC2626;
  border: none;
  color: #fff;
}
.nl-modal-confirm:hover { background: #B91C1C; }

.modal-fade-enter-active, .modal-fade-leave-active { transition: all 0.2s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .nl-modal { transform: scale(0.95); }

@media (max-width: 768px) {
  .nl-root { flex-direction: column; }
  .nl-sidebar {
    width: 100%;
    max-height: 40vh;
    border-right: none;
    border-bottom: 1px solid var(--cream-300);
  }
  .nl-editor { min-height: 0; }
}
</style>
