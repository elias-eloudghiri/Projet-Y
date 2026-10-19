<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { adminStoriesApi, formatSize, type Story } from '@/api/stories'
import { ApiError } from '@/api/http'

const route = useRoute()

const stories = ref<Story[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
/** Title of the story just imported, passed by the creation form. */
const created = typeof route.query.created === 'string' ? route.query.created : null

const dateFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })

onMounted(async () => {
  try {
    stories.value = await adminStoriesApi.list()
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : 'Impossible de charger les histoires. Réessaie plus tard.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h1>Histoires</h1>
      <RouterLink :to="{ name: 'admin-story-new' }" class="btn">Ajouter une histoire</RouterLink>
    </div>

    <p v-if="created" class="alert alert-success" role="status">
      L'histoire « {{ created }} » a été ajoutée au catalogue.
    </p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="muted">Chargement…</p>
    <p v-else-if="stories.length === 0" class="muted">Aucune histoire pour l'instant.</p>

    <ul v-else class="story-list">
      <li v-for="story in stories" :key="story.id" class="story">
        <div class="story-head">
          <h2>{{ story.title }}</h2>
          <span class="players">{{ story.playerCount }} joueurs</span>
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
        <p class="meta">
          PDF {{ formatSize(story.pdfSize) }} · ajoutée le
          {{ dateFormat.format(new Date(story.createdAt)) }}
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.page {
  margin: 2rem 0 4rem;
}

.page-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  font-size: 1.6rem;
}

.alert {
  margin-bottom: 1rem;
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

.meta {
  margin-top: 0.75rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}
</style>
