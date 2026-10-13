<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <section class="hero">
    <h1>Lisez ensemble, chacun son rôle.</h1>
    <p v-if="auth.isAuthenticated" class="lead">
      Connecté en tant que <strong class="pseudo">{{ auth.user?.pseudo }}</strong>.
    </p>
    <template v-else>
      <p class="lead">Créez un compte pour ouvrir une room et inviter vos joueurs.</p>
      <div class="actions">
        <RouterLink to="/inscription" class="btn">Créer un compte</RouterLink>
        <RouterLink to="/connexion" class="btn btn-secondary">Se connecter</RouterLink>
      </div>
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

.pseudo {
  font-family: var(--font-mono);
  color: var(--color-text);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
</style>
