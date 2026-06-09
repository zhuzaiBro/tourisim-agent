import axios from 'axios'
import { apiUrl } from '@/utils/apiBase'

const api = axios.create({
  baseURL: apiUrl('/api'),
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('voyage_token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// ──────────────────────────── Types ────────────────────────────

export interface ItineraryRequest {
  cityCode: string
  /** 用户自定义目的地中文名（可选） */
  cityName?: string
  startDate: string
  endDate: string
  preferences?: string[]
  /** 忌口：不吃辣、清真、素食 等 */
  dietaryRestrictions?: string[]
  /** 口味偏好：本地特色、辣味、清淡 等 */
  tastePreferences?: string[]
  budget?: 'low' | 'medium' | 'high'
  transportMode?: 'walking' | 'driving' | 'transit'
  /** 住宿类型：hotel / homestay / hostel / any */
  accommodationType?: 'hotel' | 'homestay' | 'hostel' | 'any'
  adults?: number
  children?: number
}

export interface WeatherInfo {
  date: string
  condition: string          // sunny / cloudy / rainy etc.
  conditionText: string      // 晴 / 多云 / 小雨
  tempHigh: number
  tempLow: number
  windDir: string
  windScale: string
  humidity: number
  uvIndex: string
  outdoorFriendly: boolean
  dataSource: string
}

export interface PoiInfo {
  id: string
  name: string
  category: string
  address: string
  lat: number
  lng: number
  rating: number
  openingHours: string
  ticketPrice: string
  visitDurationMinutes: number
  indoorVenue: boolean
  tags: string[]
  description: string
  dataSource: string
}

export interface RouteLeg {
  fromName: string
  toName: string
  distanceKm: number
  durationMinutes: number
  transportSuggestion: string
  instruction: string
}

export interface RouteInfo {
  optimizedPois: PoiInfo[]
  legs: RouteLeg[]
  totalDistanceKm: number
  totalDurationMinutes: number
  optimizationMethod: string
  dataSource: string
}

export interface TimeSlotActivity {
  timeSlot: string
  activity: string
  type: string
  poi?: PoiInfo
  durationMinutes: number
  transportFromPrev: string
  transportMinutes: number
  estimatedCost: number
  notes: string
}

export interface AccommodationRecommendation {
  name: string
  category: string
  starLevel?: string
  rating: number
  priceRange: string
  distanceKm: number
  district?: string
  address: string
  phone?: string
  checkInTip?: string
  recommendReason: string
  lat: number
  lng: number
  dataSource: string
  primaryPick?: boolean
}

export interface FoodRecommendation {
  name: string
  category: string
  rating: number
  priceRange: string
  distanceKm: number
  address: string
  businessStatus: string
  openingHours: string
  phone?: string
  recommendReason: string
  mealType: string
  lat: number
  lng: number
  dataSource: string
}

export interface DayPlan {
  date: string
  dayNumber: number
  dayOfWeek: string
  weather: WeatherInfo
  mainPlanTitle: string
  mainActivities: TimeSlotActivity[]
  alternatePlanTitle: string
  alternateActivities: TimeSlotActivity[]
  route: RouteInfo
  alternateRoute?: RouteInfo
  foods: FoodRecommendation[]
  tips: string[]
  narrative: string
  budget: Record<string, string>
}

export interface ToolCallLog {
  toolName: string
  provider: string
  startTime: string
  durationMs: number
  success: boolean
  usedFallback: boolean
  errorMessage?: string
}

export interface ItineraryResponse {
  itineraryId: string
  requestId: string
  cityCode: string
  cityName: string
  startDate: string
  endDate: string
  totalDays: number
  preferences: string[]
  budget: string
  transportMode: string
  tripSummary: string
  days: DayPlan[]
  accommodations?: AccommodationRecommendation[]
  primaryAccommodation?: AccommodationRecommendation
  accommodationTips?: string[]
  totalBudget: Record<string, string>
  toolCallLogs: ToolCallLog[]
  generatedAt: string
  hasRealWeatherData: boolean
  hasRealPoiData: boolean
  hasRealFoodData: boolean
  hasRealAccommodationData: boolean
}

export interface ItinerarySummary {
  id: string
  cityCode: string
  cityName: string
  startDate: string
  endDate: string
  totalDays: number
  tripSummary: string
  createdAt: string
}

export interface ItineraryListResponse {
  items: ItinerarySummary[]
  page: number
  size: number
  total: number
  totalPages: number
}

// ──────────────────────────── API ────────────────────────────

export const agentApi = {
  generateItinerary(req: ItineraryRequest): Promise<ItineraryResponse> {
    return api.post('/agent/itinerary', req).then(r => r.data)
  },

  getItinerary(id: string): Promise<ItineraryResponse> {
    return api.get(`/agent/itinerary/${id}`).then(r => r.data)
  },

  listItineraries(page = 0, size = 20): Promise<ItineraryListResponse> {
    return api.get('/agent/itineraries', { params: { page, size } }).then(r => r.data)
  },

  getStatus(): Promise<unknown> {
    return api.get('/agent/status').then(r => r.data)
  },
}
