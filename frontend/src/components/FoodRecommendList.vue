<template>
  <div class="food-list">
    <div v-if="foods.length === 0" class="empty-tip">暂无餐厅推荐</div>
    <div v-for="food in foods" :key="food.name" class="food-card">
      <div class="food-header">
        <span class="food-name">{{ food.name }}</span>
        <span class="food-category">{{ food.category }}</span>
        <DataSourceBadge :source="food.dataSource" kind="food" />
      </div>
      <div class="food-meta">
        <span class="rating">⭐ {{ food.rating.toFixed(1) }}</span>
        <span class="price">💰 {{ food.priceRange }}</span>
        <span class="distance">📍 {{ food.distanceKm }}km</span>
        <span class="status" :class="food.businessStatus === '营业中' ? 'open' : 'closed'">
          {{ food.businessStatus }}
        </span>
      </div>
      <div class="food-address">{{ food.address }}</div>
      <div v-if="food.openingHours" class="food-hours">🕐 {{ food.openingHours }}</div>
      <div class="food-reason">{{ food.recommendReason }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { FoodRecommendation } from '@/api/agent'
import DataSourceBadge from '@/components/DataSourceBadge.vue'

defineProps<{ foods: FoodRecommendation[] }>()
</script>

<style scoped>
.food-list { display: flex; flex-direction: column; gap: 10px; }
.empty-tip { color: #999; text-align: center; padding: 16px; }
.food-card {
  background: var(--cream);
  border: 1px solid var(--cream-300);
  border-radius: 10px;
  padding: 12px 14px;
  transition: box-shadow .2s;
}
.food-card:hover { box-shadow: 0 2px 10px rgba(0,0,0,.08); }
.food-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.food-name { font-weight: 600; font-size: 15px; color: var(--text); }
.food-category {
  font-size: 11px; background: var(--cream-200); color: var(--forest);
  padding: 2px 7px; border-radius: 4px;
}
.food-meta { display: flex; gap: 12px; font-size: 13px; color: var(--text-2); margin-bottom: 4px; flex-wrap: wrap; }
.status.open  { color: var(--forest-400); font-weight: 500; }
.status.closed{ color: #c62828; }
.food-address { font-size: 12px; color: var(--text-3); margin-bottom: 3px; }
.food-hours   { font-size: 12px; color: var(--text-3); margin-bottom: 4px; }
.food-reason  { font-size: 12px; color: var(--text-2); font-style: italic; background: var(--cream-200); padding: 4px 8px; border-radius: 4px; }
</style>
