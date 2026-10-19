import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reserved to accounts: guests are sent to the login page. */
    requiresAccount?: boolean
    /** Reserved to administrators; the API checks the role as well. */
    requiresAdmin?: boolean
    /** Login / registration: hidden once logged in with an account (a guest may still use them). */
    noAccountOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/inscription',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { noAccountOnly: true },
    },
    {
      path: '/inscription/confirmation',
      name: 'register-success',
      component: () => import('../views/RegisterSuccessView.vue'),
      meta: { requiresAccount: true },
    },
    {
      path: '/connexion',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { noAccountOnly: true },
    },
    {
      path: '/admin/histoires',
      name: 'admin-stories',
      component: () => import('../views/admin/AdminStoriesView.vue'),
      meta: { requiresAccount: true, requiresAdmin: true },
    },
    {
      path: '/admin/histoires/nouvelle',
      name: 'admin-story-new',
      component: () => import('../views/admin/AdminStoryCreateView.vue'),
      meta: { requiresAccount: true, requiresAdmin: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.load()

  if (to.meta.requiresAccount && !auth.hasAccount) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'home' }
  }
  if (to.meta.noAccountOnly && auth.hasAccount) {
    return { name: 'home' }
  }
})

export default router
