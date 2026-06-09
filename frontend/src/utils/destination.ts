import type { City } from '@/types'

export const CUSTOM_PREFIX = 'custom:'

export interface CustomDestination {
  code: string
  nameCn: string
}

export function toCustomCityCode(name: string): string {
  return CUSTOM_PREFIX + name.trim()
}

export function isCustomCityCode(code: string): boolean {
  return code.startsWith(CUSTOM_PREFIX)
}

export function parseCustomCityName(code: string): string {
  return isCustomCityCode(code) ? code.slice(CUSTOM_PREFIX.length).trim() : code
}

export function getCityDisplayName(
  code: string,
  cities: City[] = [],
  custom: CustomDestination[] = [],
): string {
  if (isCustomCityCode(code)) return parseCustomCityName(code)
  return cities.find(c => c.code === code)?.nameCn
    ?? custom.find(c => c.code === code)?.nameCn
    ?? code
}
