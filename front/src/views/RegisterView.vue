<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/api/auth'
import { ApiError } from '@/api/http'

const auth = useAuthStore()
const router = useRouter()

const password = ref('')
const confirmation = ref('')
const error = ref<string | null>(null)
const submitting = ref(false)

const validationError = computed(() => {
  if (password.value.length < PASSWORD_MIN_LENGTH) {
    return `Le mot de passe doit contenir au moins ${PASSWORD_MIN_LENGTH} caractères.`
  }
  if (password.value.length > PASSWORD_MAX_LENGTH) {
    return `Le mot de passe doit contenir au plus ${PASSWORD_MAX_LENGTH} caractères.`
  }
  if (password.value !== confirmation.value) {
    return 'Les deux mots de passe ne correspondent pas.'
  }
  return null
})

async function submit() {
  error.value = validationError.value
  if (error.value) {
    return
  }
  submitting.value = true
  try {
    await auth.register(password.value)
    await router.push({ name: 'register-success' })
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Impossible de créer le compte. Réessaie plus tard.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card">
    <h1>Créer un compte</h1>
    <p class="lead">
      Choisis seulement un mot de passe : ton pseudo sera généré automatiquement et te servira
      d'identifiant de connexion.
    </p>

    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="password">Mot de passe</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="new-password"
          :maxlength="PASSWORD_MAX_LENGTH"
          required
        />
        <span class="hint">Au moins {{ PASSWORD_MIN_LENGTH }} caractères.</span>
      </div>

      <div class="field">
        <label for="confirmation">Confirmer le mot de passe</label>
        <input
          id="confirmation"
          v-model="confirmation"
          type="password"
          autocomplete="new-password"
          :maxlength="PASSWORD_MAX_LENGTH"
          required
        />
      </div>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

      <button type="submit" class="btn" :disabled="submitting">
        {{ submitting ? 'Création…' : 'Créer mon compte' }}
      </button>
    </form>

    <p class="switch-link">Déjà un compte ? <RouterLink to="/connexion">Se connecter</RouterLink></p>
  </section>
</template>
