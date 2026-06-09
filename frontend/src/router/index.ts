import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },
    { path: '/app', component: () => import('@/views/ChatView.vue'), meta: { requiresAuth: true } },
    { path: '/itinerary', component: () => import('@/views/ItineraryView.vue') },
    { path: '/itinerary/history', component: () => import('@/views/ItineraryHistoryView.vue'), meta: { requiresAuth: true } },
    { path: '/planbook', component: () => import('@/views/PlanBookView.vue'), meta: { requiresAuth: true } },
    { path: '/admin/login', component: () => import('@/views/AdminLoginView.vue'), meta: { adminGuest: true } },
    { path: '/admin', component: () => import('@/views/AdminView.vue'), meta: { requiresAdmin: true } },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('voyage_token')
  const userRaw = localStorage.getItem('voyage_user')
  const isLoggedIn = !!token
  const isAdmin = (() => {
    try { return userRaw ? JSON.parse(userRaw).role === 'ADMIN' : false } catch { return false }
  })()

  if (to.meta.requiresAuth && !isLoggedIn) return '/login'
  if (to.meta.requiresAdmin && !isAdmin) return '/admin/login'
  if (to.meta.guest && isLoggedIn) return '/app'
  // If already logged in as admin, skip admin login page
  if (to.meta.adminGuest && isAdmin) return '/admin'
})

export default router
