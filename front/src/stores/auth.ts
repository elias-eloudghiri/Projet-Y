import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type CurrentUser } from '@/api/auth'
import { ApiError } from '@/api/http'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  let loaded: Promise<void> | null = null

  const isAuthenticated = computed(() => user.value !== null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** Restores the session from the cookies (refreshing them if needed). Runs once. */
  function load(): Promise<void> {
    loaded ??= authApi
      .me()
      .then((me) => {
        user.value = me
      })
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) {
          console.error(e)
        }
        user.value = null
      })
    return loaded
  }

  async function register(password: string) {
    user.value = await authApi.register(password)
    return user.value
  }

  async function login(pseudo: string, password: string) {
    user.value = await authApi.login(pseudo, password)
    return user.value
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      user.value = null
    }
  }

  return { user, isAuthenticated, isAdmin, load, register, login, logout }
})
