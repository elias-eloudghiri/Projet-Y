<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { MIN_PLAYERS, storiesApi, type CatalogStory } from '@/api/stories'
import { ROOM_PASSWORD_MAX_LENGTH, ROOM_PASSWORD_MIN_LENGTH, roomsApi } from '@/api/rooms'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()

const story = ref<CatalogStory | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

const playerCount = ref(MIN_PLAYERS)
const password = ref('')
const confirmation = ref('')
const showPassword = ref(false)

/** Field errors are only shown once a submission has been attempted. */
const submitted = ref(false)
const submitting = ref(false)
const serverError = ref<string | null>(null)

const playerCounts = computed(() => {
  const max = story.value?.maxPlayers ?? MIN_PLAYERS
  return Array.from({ length: max - MIN_PLAYERS + 1 }, (_, i) => MIN_PLAYERS + i)
})

onMounted(async () => {
  try {
    story.value = await storiesApi.get(String(route.params.storyId))
    playerCount.value = story.value.maxPlayers
  } catch (e) {
    loadError.value =
      e instanceof ApiError ? e.message : "Impossible de charger l'histoire. Réessaie plus tard."
  } finally {
    loading.value = false
  }
})

const errors = computed(() => {
  const result: { playerCount?: string; password?: string; confirmation?: string } = {}
  const max = story.value?.maxPlayers ?? MIN_PLAYERS
  if (playerCount.value < MIN_PLAYERS || playerCount.value > max) {
    result.playerCount = `Le nombre de joueurs doit être compris entre ${MIN_PLAYERS} et ${max}.`
  }
  if (
    password.value.length < ROOM_PASSWORD_MIN_LENGTH ||
    password.value.length > ROOM_PASSWORD_MAX_LENGTH
  ) {
    result.password =
      `Le mot de passe de la room doit contenir entre ${ROOM_PASSWORD_MIN_LENGTH} et ` +
      `${ROOM_PASSWORD_MAX_LENGTH} caractères.`
  }
  if (confirmation.value !== password.value) {
    result.confirmation = 'Les deux mots de passe ne correspondent pas.'
  }
  return result
})

const isValid = computed(() => Object.keys(errors.value).length === 0)

async function submit() {
  submitted.value = true
  serverError.value = null
  if (!isValid.value || !story.value) {
    return
  }
  submitting.value = true
  try {
    const room = await roomsApi.create({
      storyId: story.value.id,
      playerCount: playerCount.value,
      password: password.value,
    })
    await router.push({ name: 'room', params: { code: room.code } })
  } catch (e) {
    serverError.value =
      e instanceof ApiError ? e.message : 'Impossible de créer la room. Réessaie plus tard.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card card-wide">
    <h1>Créer une room</h1>

    <p v-if="loading" class="muted">Chargement…</p>
    <template v-else-if="loadError">
      <p class="alert alert-error" role="alert">{{ loadError }}</p>
      <RouterLink :to="{ name: 'stories' }" class="back">Retour au catalogue</RouterLink>
    </template>

    <template v-else-if="story">
      <div class="recap">
        <div class="recap-head">
          <h2>{{ story.title }}</h2>
          <span class="muted">jusqu'à {{ story.maxPlayers }} joueurs</span>
        </div>
        <ul class="characters" aria-label="Personnages">
          <li
            v-for="(name, index) in story.characters"
            :key="name"
            :class="{ narrator: index === 0 }"
          >
            {{ name }}
          </li>
        </ul>
      </div>

      <form class="form" novalidate @submit.prevent="submit">
        <div class="field">
          <template v-if="playerCounts.length > 1">
            <label for="player-count">Nombre de joueurs</label>
            <select
              id="player-count"
              v-model.number="playerCount"
              :aria-invalid="submitted && !!errors.playerCount"
            >
              <option v-for="count in playerCounts" :key="count" :value="count">
                {{ count }} joueurs
              </option>
            </select>
            <span class="hint">La room refusera tout joueur au-delà de ce nombre.</span>
          </template>
          <template v-else>
            <span class="label">Nombre de joueurs</span>
            <span class="fixed-value">{{ playerCount }} joueurs</span>
          </template>
          <span v-if="submitted && errors.playerCount" class="field-error">{{
            errors.playerCount
          }}</span>
        </div>

        <div class="field">
          <label for="room-password">Mot de passe de la room</label>
          <div class="password-row">
            <input
              id="room-password"
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="new-password"
              :maxlength="ROOM_PASSWORD_MAX_LENGTH"
              :aria-invalid="submitted && !!errors.password"
              aria-describedby="room-password-hint room-password-error"
              required
            />
            <button
              type="button"
              class="btn btn-secondary"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? 'Masquer' : 'Afficher' }}
            </button>
          </div>
          <span id="room-password-hint" class="hint"
            >Entre {{ ROOM_PASSWORD_MIN_LENGTH }} et {{ ROOM_PASSWORD_MAX_LENGTH }} caractères, à
            transmettre à tes joueurs.</span
          >
          <span v-if="submitted && errors.password" id="room-password-error" class="field-error">{{
            errors.password
          }}</span>
        </div>

        <div class="field">
          <label for="room-password-confirmation">Confirmation du mot de passe</label>
          <input
            id="room-password-confirmation"
            v-model="confirmation"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            :maxlength="ROOM_PASSWORD_MAX_LENGTH"
            :aria-invalid="submitted && !!errors.confirmation"
            aria-describedby="room-password-confirmation-error"
            required
          />
          <span
            v-if="submitted && errors.confirmation"
            id="room-password-confirmation-error"
            class="field-error"
            >{{ errors.confirmation }}</span
          >
        </div>

        <p v-if="serverError" class="alert alert-error" role="alert">{{ serverError }}</p>
        <p v-else-if="submitted && !isValid" class="alert alert-error" role="alert">
          Corrige les champs signalés.
        </p>

        <div class="actions">
          <RouterLink :to="{ name: 'stories' }" class="btn btn-secondary">Annuler</RouterLink>
          <button type="submit" class="btn" :disabled="submitting">
            {{ submitting ? 'Création…' : 'Créer la room' }}
          </button>
        </div>
      </form>
    </template>
  </section>
</template>

<style scoped>
.card-wide {
  max-width: 560px;
}

.muted {
  color: var(--color-text-muted);
}

.back {
  display: inline-block;
  margin-top: 1rem;
}

.recap {
  margin: 1rem 0 1.5rem;
  padding: 1rem 1.25rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.recap-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.25rem 1rem;
}

.recap-head h2 {
  font-size: 1.15rem;
  overflow-wrap: anywhere;
}

.recap-head .muted {
  font-size: 0.9rem;
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
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.characters .narrator {
  border-color: var(--color-border-hover);
  font-style: italic;
}

.label {
  font-weight: 600;
  font-size: 0.9rem;
}

.fixed-value {
  font-weight: 600;
}

.password-row {
  display: flex;
  gap: 0.5rem;
}

.password-row input {
  flex: 1;
  min-width: 0;
}

.field select[aria-invalid='true'] {
  border-color: var(--color-danger);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
