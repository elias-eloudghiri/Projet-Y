<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { storiesApi, type CatalogStory } from '@/api/stories'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const stories = ref<CatalogStory[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    stories.value = await storiesApi.list()
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : 'Impossible de charger le catalogue. Réessaie plus tard.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <h1>Catalogue</h1>
    <p v-if="auth.isGuest" class="muted intro">
      Pour créer une room à partir d'une histoire, il faut un compte.
    </p>

    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="muted">Chargement…</p>
    <p v-else-if="stories.length === 0" class="muted">Aucune histoire pour l'instant.</p>

    <ul v-else class="story-list">
      <li v-for="story in stories" :key="story.id" class="story">
        <div class="story-head">
          <h2>{{ story.title }}</h2>
          <span class="players">jusqu'à {{ story.maxPlayers }} joueurs</span>
        </div>
        <p class="description">{{ story.description }}</p>
        <ul class="characters" aria-label="Personnages">
          <li
            v-for="(name, index) in story.characters"
            :key="name"
            :class="{ narrator: index === 0 }"
          >
            {{ name }}
          </li>
        </ul>
        <div v-if="auth.hasAccount" class="story-actions">
          <RouterLink :to="{ name: 'room-new', params: { storyId: story.id } }" class="btn">
            Créer une room
          </RouterLink>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.page {
  margin: 2rem 0 4rem;
}

.page h1 {
  font-size: 1.6rem;
  margin-bottom: 1.5rem;
}

.intro {
  margin: -0.75rem 0 1.5rem;
}

.muted {
  color: var(--color-text-muted);
}

.story-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0;
  list-style: none;
}

.story {
  padding: 1.25rem 1.5rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.story-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.5rem 1rem;
}

.story-head h2 {
  font-size: 1.2rem;
  overflow-wrap: anywhere;
}

.players {
  flex: none;
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.description {
  margin-top: 0.5rem;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.characters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.75rem;
  padding: 0;
  list-style: none;
}

.characters li {
  padding: 0.1rem 0.6rem;
  font-size: 0.85rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.characters .narrator {
  border-color: var(--color-border-hover);
  font-style: italic;
}

.story-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1rem;
}
</style>
