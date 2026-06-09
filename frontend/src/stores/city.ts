import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { cityApi } from '@/api'
import type { City } from '@/types'
import { ElMessage } from 'element-plus'
import {
  type CustomDestination,
  toCustomCityCode,
  isCustomCityCode,
  getCityDisplayName,
} from '@/utils/destination'

const CUSTOM_STORAGE_KEY = 'voyage_custom_destinations'

function loadCustomFromStorage(): CustomDestination[] {
  try {
    const raw = localStorage.getItem(CUSTOM_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as CustomDestination[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveCustomToStorage(list: CustomDestination[]) {
  localStorage.setItem(CUSTOM_STORAGE_KEY, JSON.stringify(list))
}

export const useCityStore = defineStore('city', () => {
  const cities = ref<City[]>([])
  const selectedCities = ref<string[]>([])
  const customDestinations = ref<CustomDestination[]>(loadCustomFromStorage())
  const loading = ref(false)

  const selectedCityObjects = computed(() =>
    cities.value.filter(c => selectedCities.value.includes(c.code))
  )

  const enabledCities = computed(() =>
    cities.value.filter(c => c.knowledgeIngested)
  )

  const MOCK_CITIES: City[] = [
    { code: 'qingdao', nameCn: '青岛', nameEn: 'Qingdao', province: '山东省', description: '红瓦绿树、碧海蓝天', knowledgeIngested: true },
    { code: 'beijing', nameCn: '北京', nameEn: 'Beijing', province: '北京市', description: '千年古都，历史文化名城', knowledgeIngested: false },
    { code: 'shanghai', nameCn: '上海', nameEn: 'Shanghai', province: '上海市', description: '国际大都市，东方明珠', knowledgeIngested: false },
  ]

  function displayName(code: string): string {
    return getCityDisplayName(code, cities.value, customDestinations.value)
  }

  function addCustomDestination(name: string): string | null {
    const trimmed = name.trim()
    if (!trimmed) {
      ElMessage.warning('请输入目的地名称')
      return null
    }
    if (trimmed.length > 20) {
      ElMessage.warning('目的地名称不超过 20 字')
      return null
    }

    const existingRecommended = cities.value.find(c => c.nameCn === trimmed)
    if (existingRecommended) {
      if (!selectedCities.value.includes(existingRecommended.code)) {
        if (selectedCities.value.length >= 3) {
          ElMessage.warning('最多联游 3 个城市')
          return null
        }
        selectedCities.value = [...selectedCities.value, existingRecommended.code]
      }
      return existingRecommended.code
    }

    const code = toCustomCityCode(trimmed)
    if (!customDestinations.value.some(c => c.code === code)) {
      customDestinations.value = [...customDestinations.value, { code, nameCn: trimmed }]
      saveCustomToStorage(customDestinations.value)
    }

    if (!selectedCities.value.includes(code)) {
      if (selectedCities.value.length >= 3) {
        ElMessage.warning('最多联游 3 个城市')
        return null
      }
      selectedCities.value = [...selectedCities.value, code]
    }
    return code
  }

  function removeCustomDestination(code: string) {
    customDestinations.value = customDestinations.value.filter(c => c.code !== code)
    saveCustomToStorage(customDestinations.value)
    selectedCities.value = selectedCities.value.filter(c => c !== code)
    if (selectedCities.value.length === 0 && cities.value.length > 0) {
      selectedCities.value = [cities.value[0].code]
    }
  }

  async function fetchCities() {
    loading.value = true
    try {
      cities.value = await cityApi.getEnabledCities()
      if (cities.value.length > 0 && selectedCities.value.length === 0) {
        selectedCities.value = [cities.value[0].code]
      }
    } catch (e) {
      if (import.meta.env.DEV) {
        console.warn('后端未启动，DEV 模式使用 Mock 城市数据')
        cities.value = MOCK_CITIES
        if (selectedCities.value.length === 0) {
          selectedCities.value = ['qingdao']
        }
      } else {
        ElMessage.error('无法加载城市列表，请检查网络或稍后重试')
        cities.value = []
      }
    } finally {
      loading.value = false
    }
  }

  function selectCity(code: string) {
    if (!selectedCities.value.includes(code)) {
      selectedCities.value = [code]
    }
  }

  function toggleCity(code: string) {
    const idx = selectedCities.value.indexOf(code)
    if (idx === -1) {
      selectedCities.value.push(code)
    } else if (selectedCities.value.length > 1) {
      selectedCities.value.splice(idx, 1)
    }
  }

  async function initDefaultAndIngest() {
    try {
      await cityApi.initDefaultCities()
      ElMessage.success('默认城市初始化成功，正在摄入知识库...')
      await fetchCities()
    } catch (e) {
      ElMessage.error('初始化失败')
    }
  }

  return {
    cities,
    selectedCities,
    customDestinations,
    selectedCityObjects,
    enabledCities,
    loading,
    displayName,
    isCustomCityCode,
    addCustomDestination,
    removeCustomDestination,
    fetchCities,
    selectCity,
    toggleCity,
    initDefaultAndIngest,
  }
})
