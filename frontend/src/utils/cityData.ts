// City geo coordinates for map + weather
export interface CityGeo {
  lat: number
  lng: number
  nameCn: string
  nameEn: string
}

export const CITY_GEO: Record<string, CityGeo> = {
  qingdao:  { lat: 36.067, lng: 120.382, nameCn: '青岛', nameEn: 'Qingdao' },
  beijing:  { lat: 39.904, lng: 116.407, nameCn: '北京', nameEn: 'Beijing' },
  shanghai: { lat: 31.230, lng: 121.474, nameCn: '上海', nameEn: 'Shanghai' },
  hangzhou: { lat: 30.274, lng: 120.155, nameCn: '杭州', nameEn: 'Hangzhou' },
  chengdu:  { lat: 30.572, lng: 104.066, nameCn: '成都', nameEn: 'Chengdu' },
  xiamen:   { lat: 24.479, lng: 118.089, nameCn: '厦门', nameEn: 'Xiamen' },
  guilin:   { lat: 25.274, lng: 110.290, nameCn: '桂林', nameEn: 'Guilin' },
  zhangjiajie: { lat: 29.117, lng: 110.479, nameCn: '张家界', nameEn: 'Zhangjiajie' },
}

// WMO weather code → emoji + description
export function wmoToWeather(code: number): { icon: string; desc: string } {
  if (code === 0)                         return { icon: '☀️',  desc: '晴' }
  if (code <= 3)                          return { icon: '⛅',  desc: '多云' }
  if (code <= 48)                         return { icon: '🌫',  desc: '雾' }
  if (code <= 57)                         return { icon: '🌦',  desc: '毛毛雨' }
  if (code <= 67)                         return { icon: '🌧',  desc: '雨' }
  if (code <= 77)                         return { icon: '🌨',  desc: '雪' }
  if (code <= 82)                         return { icon: '🌦',  desc: '阵雨' }
  if (code <= 86)                         return { icon: '🌨',  desc: '阵雪' }
  if (code === 95)                        return { icon: '⛈',   desc: '雷雨' }
  return { icon: '⛈', desc: '雷暴' }
}
