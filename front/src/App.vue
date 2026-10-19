<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PlayerName from '@/components/PlayerName.vue'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout()
  await router.push({ name: 'home' })
}
</script>

<template>
  <header class="topbar">
    <RouterLink to="/" class="brand">Lecture coopérative</RouterLink>
    <nav v-if="auth.user" class="nav">
      <PlayerName :pseudo="auth.user.pseudo" :role="auth.user.role" />
      <RouterLink v-if="auth.isAdmin" to="/admin/histoires">Administration</RouterLink>
      <RouterLink v-if="auth.isGuest" to="/inscription">Créer un compte</RouterLink>
      <button type="button" class="btn btn-secondary" @click="logout">
        {{ auth.isGuest ? 'Quitter' : 'Se déconnecter' }}
      </button>
    </nav>
    <nav v-else class="nav">
      <RouterLink to="/connexion">Se connecter</RouterLink>
      <RouterLink to="/inscription" class="btn">Créer un compte</RouterLink>
    </nav>
  </header>

  <main>
    <RouterView />
  </main>
</template>

<style scoped>
.topbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1rem 0;
  border-bottom: 1px solid var(--color-border);
}

.brand {
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--color-heading);
  text-decoration: none;
}

.nav {
  display: flex;
  align-items: center;
  gap: 1rem;
  min-width: 0;
}
</style>
