<template>
  <div class="city-selector">
    <div class="section-hint">推荐城市</div>

    <div class="city-list" v-if="cityStore.cities.length > 0">
      <button
        v-for="city in cityStore.cities"
        :key="city.code"
        class="city-row"
        :class="{
          'selected': cityStore.selectedCities.includes(city.code),
          'not-ingested': !city.knowledgeIngested
        }"
        @click="handleToggle(city.code)"
      >
        <span class="city-dot" :class="{ ingested: city.knowledgeIngested }"></span>
        <span class="city-name">{{ city.nameCn }}</span>
        <span class="city-prov">{{ city.province }}</span>
        <span class="city-check" v-if="cityStore.selectedCities.includes(city.code)">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1.5 5l2.5 2.5L8.5 2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
      </button>
    </div>

    <div v-else-if="cityStore.loading" class="city-skeleton">
      <div class="sk-row" v-for="i in 3" :key="i"></div>
    </div>

    <div v-else class="city-empty">
      <span>暂无推荐城市</span>
      <button class="init-btn" @click="handleInit">初始化青岛</button>
    </div>

    <!-- 自定义目的地 -->
    <div class="custom-section">
      <div class="section-hint">自定义目的地</div>
      <div class="custom-input-row">
        <span class="input-prefix">$</span>
        <input
          v-model="customInput"
          class="custom-input"
          type="text"
          placeholder="输入城市，如 丽江、厦门"
          maxlength="20"
          @keydown.enter.prevent="handleAddCustom"
        />
        <button class="add-btn" :disabled="!customInput.trim()" @click="handleAddCustom">添加</button>
      </div>

      <div v-if="cityStore.customDestinations.length" class="custom-list">
        <button
          v-for="dest in cityStore.customDestinations"
          :key="dest.code"
          class="city-row custom-row"
          :class="{ selected: cityStore.selectedCities.includes(dest.code) }"
          @click="handleToggle(dest.code)"
        >
          <span class="city-dot custom-dot"></span>
          <span class="city-name">{{ dest.nameCn }}</span>
          <span class="city-tag">自定义</span>
          <button
            class="remove-btn"
            title="移除"
            @click.stop="cityStore.removeCustomDestination(dest.code)"
          >×</button>
          <span class="city-check" v-if="cityStore.selectedCities.includes(dest.code)">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1.5 5l2.5 2.5L8.5 2.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </span>
        </button>
      </div>
    </div>

    <Transition name="badge-fade">
      <div v-if="cityStore.selectedCities.length > 1" class="multi-badge">
        <span class="badge-pulse"></span>
        <span>多城市联游</span>
        <span class="badge-count">{{ cityStore.selectedCities.length }}城</span>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useCityStore } from '@/stores/city'
import { ingestApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const cityStore = useCityStore()
const customInput = ref('')

function handleToggle(code: string) {
  const idx = cityStore.selectedCities.indexOf(code)
  if (idx === -1) {
    if (cityStore.selectedCities.length >= 3) {
      ElMessage({ message: '最多联游 3 个城市', type: 'warning', duration: 2000 })
      return
    }
    cityStore.selectedCities = [...cityStore.selectedCities, code]
  } else {
    if (cityStore.selectedCities.length === 1) return
    cityStore.selectedCities = cityStore.selectedCities.filter(c => c !== code)
  }
}

function handleAddCustom() {
  const code = cityStore.addCustomDestination(customInput.value)
  if (code) {
    customInput.value = ''
    ElMessage.success(`已添加「${cityStore.displayName(code)}」`)
  }
}

async function handleInit() {
  try {
    await ElMessageBox.confirm(
      '将初始化青岛为默认城市并开始摄入知识库（约需 1-2 分钟）',
      '初始化确认',
      { confirmButtonText: '开始', cancelButtonText: '取消', type: 'info' }
    )
    await cityStore.initDefaultAndIngest()
    await ingestApi.ingestQingdao()
    ElMessage.success('青岛知识库摄入已启动，请稍候刷新')
  } catch { /* cancelled */ }
}
</script>

<style scoped>
.city-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.section-hint {
  font-size: 10px;
  color: rgba(250, 246, 240, 0.45);
  letter-spacing: 0.04em;
  margin-bottom: 2px;
}

.city-list,
.custom-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.custom-section {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(137, 57, 77, 0.35);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.custom-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(137, 57, 77, 0.45);
  border-radius: var(--radius);
}

.input-prefix {
  color: var(--term-green);
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.custom-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  font-size: 12px;
  color: var(--text-on-theme);
  font-family: inherit;
}

.custom-input::placeholder {
  color: rgba(250, 246, 240, 0.35);
}

.add-btn {
  flex-shrink: 0;
  padding: 3px 8px;
  font-size: 11px;
  border-radius: var(--radius);
  border: 1px solid rgba(110, 207, 111, 0.45);
  background: rgba(110, 207, 111, 0.12);
  color: var(--term-green);
  cursor: pointer;
  font-family: inherit;
}

.add-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.add-btn:hover:not(:disabled) {
  background: rgba(110, 207, 111, 0.2);
}

.city-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius);
  cursor: pointer;
  border: 1px solid transparent;
  background: transparent;
  transition: background 0.15s, border-color 0.15s;
  width: 100%;
  text-align: left;
  font-family: inherit;
}

.city-row:hover {
  background: rgba(255,255,255,0.07);
}

.city-row.selected {
  background: rgba(110, 207, 111, 0.12);
  border-color: rgba(110, 207, 111, 0.3);
}

.city-row.not-ingested {
  opacity: 0.55;
}

.custom-row .city-tag {
  font-size: 9px;
  color: var(--term-amber);
  border: 1px solid rgba(232, 184, 109, 0.35);
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.custom-dot {
  background: var(--term-amber) !important;
  box-shadow: 0 0 5px rgba(232, 184, 109, 0.35);
}

.remove-btn {
  width: 18px;
  height: 18px;
  border: none;
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.45);
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
  margin-left: auto;
}

.remove-btn:hover {
  background: rgba(220, 80, 80, 0.25);
  color: #f87171;
}

.city-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,0.2);
  flex-shrink: 0;
}

.city-dot.ingested {
  background: #4ade80;
  box-shadow: 0 0 5px rgba(74,222,128,0.4);
}

.city-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,0.85);
  letter-spacing: 0.01em;
}

.city-row.selected .city-name {
  color: #fff;
}

.city-prov {
  font-size: 10px;
  color: rgba(255,255,255,0.28);
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

.city-check {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(110, 207, 111, 0.75);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.city-skeleton {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sk-row {
  height: 32px;
  border-radius: var(--radius);
  background: linear-gradient(90deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.09) 50%, rgba(255,255,255,0.05) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.city-empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.city-empty span {
  font-size: 12px;
  color: rgba(255,255,255,0.3);
}

.init-btn {
  align-self: flex-start;
  background: rgba(110, 207, 111, 0.15);
  color: var(--term-green);
  border: 1px solid rgba(110, 207, 111, 0.35);
  border-radius: var(--radius);
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.init-btn:hover {
  background: rgba(110, 207, 111, 0.25);
}

.multi-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: var(--radius);
  background: rgba(110, 207, 111, 0.1);
  border: 1px solid rgba(110, 207, 111, 0.2);
  font-size: 11px;
  color: var(--term-green);
  letter-spacing: 0.04em;
}

.badge-pulse {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--term-green);
  animation: pulse 2s ease-in-out infinite;
}

.badge-count {
  margin-left: auto;
  font-size: 10px;
  opacity: 0.7;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.badge-fade-enter-active,
.badge-fade-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.badge-fade-enter-from,
.badge-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
