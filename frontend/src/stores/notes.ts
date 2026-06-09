import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userContentApi } from '@/api/userContent'
import { useAuthStore } from '@/stores/auth'

export interface Note {
  id: string
  title: string
  content: string
  tags: string[]
  pinned: boolean
  createdAt: string
  updatedAt: string
  wordCount: number
  sourceMessage?: string
}

function countWords(text: string): number {
  const clean = text.replace(/```[\s\S]*?```/g, '').replace(/[#*`_~>[\]()]/g, '')
  const cjk = (clean.match(/[\u4e00-\u9fff\u3400-\u4dbf\u20000-\u2a6df]/g) || []).length
  const latin = (clean.match(/\b[a-zA-Z0-9]+\b/g) || []).length
  return cjk + latin
}

export const useNotesStore = defineStore('notes', () => {
  const notes = ref<Note[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const pinnedNotes = computed(() =>
    notes.value.filter(n => n.pinned).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  )
  const unpinnedNotes = computed(() =>
    notes.value.filter(n => !n.pinned).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  )
  const sortedNotes = computed(() => [...pinnedNotes.value, ...unpinnedNotes.value])

  async function fetchAll() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      notes.value = []
      loaded.value = true
      return
    }
    loading.value = true
    try {
      notes.value = await userContentApi.listNotes()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function createNote(initialContent = '', sourceMessage?: string): Promise<Note> {
    const lines = initialContent.split('\n')
    const firstLine = lines[0]?.replace(/^#+\s*/, '').trim() || '新笔记'
    const title = firstLine.length > 30 ? firstLine.slice(0, 30) + '…' : firstLine || '新笔记'
    const note = await userContentApi.createNote({
      title,
      content: initialContent,
      tags: [],
      pinned: false,
      sourceMessage,
    })
    notes.value.unshift(note)
    return note
  }

  async function updateNote(id: string, updates: Partial<Omit<Note, 'id' | 'createdAt'>>) {
    const payload: Record<string, unknown> = { ...updates }
    if (updates.content !== undefined) {
      payload.wordCount = countWords(updates.content)
    }
    const updated = await userContentApi.updateNote(id, payload as Parameters<typeof userContentApi.updateNote>[1])
    const idx = notes.value.findIndex(n => n.id === id)
    if (idx !== -1) notes.value[idx] = updated
  }

  async function deleteNote(id: string) {
    await userContentApi.deleteNote(id)
    notes.value = notes.value.filter(n => n.id !== id)
  }

  async function togglePin(id: string) {
    const note = notes.value.find(n => n.id === id)
    if (!note) return
    await updateNote(id, { pinned: !note.pinned })
  }

  return {
    notes, loaded, loading, sortedNotes, pinnedNotes, unpinnedNotes,
    fetchAll, createNote, updateNote, deleteNote, togglePin, countWords,
  }
})
