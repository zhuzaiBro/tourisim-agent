import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userContentApi } from '@/api/userContent'
import { useAuthStore } from '@/stores/auth'

export interface FavoriteItem {
  id: string
  content: string
  cities: string[]
  question: string
  savedAt: Date
  sessionTitle: string
}

export const useFavoritesStore = defineStore('favorites', () => {
  const items = ref<FavoriteItem[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function fetchAll() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      items.value = []
      loaded.value = true
      return
    }
    loading.value = true
    try {
      const rows = await userContentApi.listFavorites()
      items.value = rows.map(r => ({
        id: String(r.id),
        content: r.content,
        cities: r.cities ?? [],
        question: r.question ?? '',
        sessionTitle: r.sessionTitle ?? '',
        savedAt: new Date(r.savedAt),
      }))
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function add(item: Omit<FavoriteItem, 'id' | 'savedAt'>) {
    const dto = await userContentApi.addFavorite({
      content: item.content,
      cities: item.cities,
      question: item.question,
      sessionTitle: item.sessionTitle,
    })
    items.value.unshift({
      id: String(dto.id),
      content: dto.content,
      cities: dto.cities ?? [],
      question: dto.question ?? '',
      sessionTitle: dto.sessionTitle ?? '',
      savedAt: new Date(dto.savedAt),
    })
  }

  async function remove(id: string) {
    await userContentApi.deleteFavorite(Number(id))
    const idx = items.value.findIndex(i => i.id === id)
    if (idx !== -1) items.value.splice(idx, 1)
  }

  function isSaved(content: string): boolean {
    return items.value.some(i => i.content === content)
  }

  return { items, loaded, loading, fetchAll, add, remove, isSaved }
})
