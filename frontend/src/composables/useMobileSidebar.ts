import { ref, onMounted, onUnmounted } from 'vue'

const MOBILE_BREAKPOINT = 768

export function useMobileSidebar() {
  const sidebarOpen = ref(false)
  const isMobile = ref(false)

  function updateViewport() {
    isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
    if (!isMobile.value) sidebarOpen.value = false
  }

  function toggleSidebar() {
    sidebarOpen.value = !sidebarOpen.value
  }

  function closeSidebar() {
    sidebarOpen.value = false
  }

  onMounted(() => {
    updateViewport()
    window.addEventListener('resize', updateViewport)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateViewport)
  })

  return { sidebarOpen, isMobile, toggleSidebar, closeSidebar }
}
