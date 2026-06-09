import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ItineraryResponse } from '@/api/agent'
import { userContentApi } from '@/api/userContent'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

export interface PlanBookItem {
  id: string
  savedAt: string
  customTitle?: string
  cityName: string
  cityCode: string
  startDate: string
  endDate: string
  totalDays: number
  tripSummary: string
  itinerary: ItineraryResponse
}

export const usePlanBookStore = defineStore('planBook', () => {
  const items = ref<PlanBookItem[]>([])
  const savedIds = ref<Set<string>>(new Set())
  const loaded = ref(false)
  const loading = ref(false)

  async function fetchAll() {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      items.value = []
      savedIds.value = new Set()
      loaded.value = true
      return
    }
    loading.value = true
    try {
      const rows = await userContentApi.listPlanBooks()
      items.value = rows.map(r => ({
        id: r.id,
        savedAt: r.savedAt,
        customTitle: r.customTitle,
        cityName: r.cityName,
        cityCode: r.cityCode,
        startDate: r.startDate,
        endDate: r.endDate,
        totalDays: r.totalDays,
        tripSummary: r.tripSummary,
        itinerary: r.itinerary,
      }))
      savedIds.value = new Set(items.value.map(i => i.id))
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function save(itinerary: ItineraryResponse): Promise<boolean> {
    const auth = useAuthStore()
    if (!auth.isLoggedIn) {
      ElMessage.warning('请先登录后再加入规划册')
      return false
    }
    if (savedIds.value.has(itinerary.itineraryId)) return false
    const dto = await userContentApi.savePlanBook(itinerary.itineraryId)
    items.value.unshift({
      id: dto.id,
      savedAt: dto.savedAt,
      customTitle: dto.customTitle,
      cityName: itinerary.cityName,
      cityCode: itinerary.cityCode,
      startDate: itinerary.startDate,
      endDate: itinerary.endDate,
      totalDays: itinerary.totalDays,
      tripSummary: itinerary.tripSummary,
      itinerary: dto.itinerary ?? itinerary,
    })
    savedIds.value.add(itinerary.itineraryId)
    return true
  }

  async function remove(id: string) {
    await userContentApi.removePlanBook(id)
    const idx = items.value.findIndex(i => i.id === id)
    if (idx >= 0) items.value.splice(idx, 1)
    savedIds.value.delete(id)
  }

  function isSaved(id: string): boolean {
    return savedIds.value.has(id)
  }

  function updateTitle(id: string, title: string) {
    const item = items.value.find(i => i.id === id)
    if (item) item.customTitle = title
  }

  return { items, loaded, loading, fetchAll, save, remove, isSaved, updateTitle }
})
