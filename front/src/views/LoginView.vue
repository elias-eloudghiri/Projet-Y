<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const pseudo = ref('')
const password = ref('')
const error = ref<string | null>(null)
const submitting = ref(false)

async function submit() {
  if (!pseudo.value.trim() || !password.value) {
    error.value = 'Renseigne ton pseudo et ton mot de passe.'
    return
  }
  error.value = null
  submitting.value = true
  try {
    await auth.login(pseudo.value, password.value)
    const redirect = route.query.redirect
    // Only internal paths, to avoid open redirects.
    const target = typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
    await router.push(target)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Connexion impossible. Réessaie plus tard.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card">
    <h1>Se connecter</h1>
    <p class="lead">Utilise le pseudo généré à ton inscription (ex. lerat-000999).</p>

    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="pseudo">Pseudo</label>
        <input
          id="pseudo"
          v-model="pseudo"
          type="text"
          autocomplete="username"
          autocapitalize="none"
          spellcheck="false"
          maxlength="64"
          required
        />
      </div>

      <div class="field">
        <label for="password">Mot de passe</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          maxlength="128"
          required
        />
      </div>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

      <button type="submit" class="btn" :disabled="submitting">
        {{ submitting ? 'Connexion…' : 'Se connecter' }}
      </button>
    </form>

    <p class="switch-link">Pas encore de compte ? <RouterLink to="/inscription">Créer un compte</RouterLink></p>
  </section>
</template>
