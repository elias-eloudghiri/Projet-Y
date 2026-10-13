<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'
import PlayerName from '@/components/PlayerName.vue'

const auth = useAuthStore()

const error = ref<string | null>(null)
const joining = ref(false)

async function playAsGuest() {
  error.value = null
  joining.value = true
  try {
    await auth.playAsGuest()
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : 'Impossible de jouer en invité. Réessaie plus tard.'
  } finally {
    joining.value = false
  }
}
</script>

<template>
  <section class="hero">
    <h1>Lisez ensemble, chacun son rôle.</h1>
    <template v-if="auth.user">
      <p class="lead">
        Connecté en tant que <PlayerName :pseudo="auth.user.pseudo" :role="auth.user.role" />
      </p>
      <p v-if="auth.isGuest" class="note">
        Tu peux rejoindre une room avec le mot de passe que t'a donné son créateur. Ton pseudo
        invité reste valable 24 h sur ce navigateur ; pour créer ta propre room, il faut un compte.
      </p>
    </template>
    <template v-else>
      <p class="lead">
        Créez un compte pour ouvrir une room et inviter vos joueurs, ou rejoignez-en une en invité.
      </p>
      <div class="actions">
        <RouterLink to="/inscription" class="btn">Créer un compte</RouterLink>
        <RouterLink to="/connexion" class="btn btn-secondary">Se connecter</RouterLink>
        <button type="button" class="btn btn-secondary" :disabled="joining" @click="playAsGuest">
          {{ joining ? 'Connexion…' : 'Jouer en invité' }}
        </button>
      </div>
      <p v-if="error" class="alert alert-error error" role="alert">{{ error }}</p>
    </template>
  </section>
</template>

<style scoped>
.hero {
  margin: 4rem auto;
  max-width: 640px;
  text-align: center;
}

.hero h1 {
  font-size: 2rem;
  margin-bottom: 1rem;
}

.lead {
  color: var(--color-text-muted);
  margin-bottom: 1.5rem;
}

.note {
  color: var(--color-text-muted);
  font-size: 0.95rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}

.error {
  margin-top: 1.25rem;
}
</style>
