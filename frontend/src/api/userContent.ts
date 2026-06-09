import api from '@/api/index'
import type { ItineraryResponse } from '@/api/agent'

export interface FavoriteDto {
  id: number
  content: string
  cities: string[]
  question: string
  sessionTitle: string
  savedAt: string
}

export interface NoteDto {
  id: string
  title: string
  content: string
  tags: string[]
  pinned: boolean
  sourceMessage?: string
  createdAt: string
  updatedAt: string
  wordCount: number
}

export interface PlanBookDto {
  id: string
  customTitle?: string
  savedAt: string
  cityName: string
  cityCode: string
  startDate: string
  endDate: string
  totalDays: number
  tripSummary: string
  itinerary: ItineraryResponse
}

export const userContentApi = {
  listFavorites(): Promise<FavoriteDto[]> {
    return api.get('/user/favorites').then(r => r.data)
  },

  addFavorite(data: {
    content: string
    cities: string[]
    question: string
    sessionTitle: string
  }): Promise<FavoriteDto> {
    return api.post('/user/favorites', data).then(r => r.data)
  },

  deleteFavorite(id: number): Promise<void> {
    return api.delete(`/user/favorites/${id}`).then(() => undefined)
  },

  listNotes(): Promise<NoteDto[]> {
    return api.get('/user/notes').then(r => r.data)
  },

  createNote(data: {
    title?: string
    content?: string
    tags?: string[]
    pinned?: boolean
    sourceMessage?: string
  }): Promise<NoteDto> {
    return api.post('/user/notes', data).then(r => r.data)
  },

  updateNote(id: string, data: Partial<{
    title: string
    content: string
    tags: string[]
    pinned: boolean
  }>): Promise<NoteDto> {
    return api.put(`/user/notes/${id}`, data).then(r => r.data)
  },

  deleteNote(id: string): Promise<void> {
    return api.delete(`/user/notes/${id}`).then(() => undefined)
  },

  listPlanBooks(): Promise<PlanBookDto[]> {
    return api.get('/user/plan-books').then(r => r.data)
  },

  savePlanBook(itineraryId: string, customTitle?: string): Promise<PlanBookDto> {
    return api.post('/user/plan-books', { itineraryId, customTitle }).then(r => r.data)
  },

  removePlanBook(itineraryId: string): Promise<void> {
    return api.delete(`/user/plan-books/${itineraryId}`).then(() => undefined)
  },

  isPlanBookSaved(itineraryId: string): Promise<boolean> {
    return api.get(`/user/plan-books/${itineraryId}/exists`).then(r => r.data.saved)
  },
}

export interface AttractionDto {
  id: number
  cityCode: string
  name: string
  category: string
  categoryLabel: string
  description: string
  address: string
  ticketPrice: string
  openingHours: string
  rating: number
  lat: number
  lng: number
  dataSource: string
}

export const attractionApi = {
  listByCity(cityCode: string): Promise<AttractionDto[]> {
    return api.get(`/cities/${cityCode}/attractions`).then(r => r.data)
  },
}
