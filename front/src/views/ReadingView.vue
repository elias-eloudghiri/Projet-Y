<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { roomsApi, type Room } from '@/api/rooms'
import { ApiError } from '@/api/http'
import { useRoomState } from '@/composables/useRoomState'
import AssignmentPanel from '@/components/AssignmentPanel.vue'
import PlayerName from '@/components/PlayerName.vue'

/**
 * Temporary reading page, reached by every player when the creator starts the reading. The
 * reading screen itself comes with US-08; until then it lists the cast, each name in the colour of
 * its player. The attribution panel opens over the page: the creator changes the attribution
 * without leaving the reading (US-07).
 */
const route = useRoute()
const router = useRouter()
const code = String(route.params.code).toUpperCase()
const live = useRoomState(code)
const { meId, state, isCreator, moveError } = live

const room = ref<Room | null>(null)
const error = ref<string | null>(null)
const panelOpen = ref(false)
let stopFollowing: (() => void) | null = null

onMounted(async () => {
  try {
    room.value = await roomsApi.get(code)
    await live.load()
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : 'Impossible de charger la room. Réessaie plus tard.'
    return
  }
  if (state.value?.status === 'WAITING') {
    // Not started yet (link opened too early): back to the waiting room.
    await router.replace({ name: 'room', params: { code } })
    return
  }
  stopFollowing = live.follow()
})
onUnmounted(() => stopFollowing?.())
</script>

<template>
  <section class="card card-reading">
    <template v-if="error">
      <h1>Room introuvable</h1>
      <p class="alert alert-error" role="alert">{{ error }}</p>
      <RouterLink :to="{ name: 'home' }" class="back">Retour à l'accueil</RouterLink>
    </template>
    <p v-else-if="!room || !state" class="muted">Chargement…</p>
    <template v-else>
      <div class="head">
        <div>
          <p class="eyebrow">Lecture en cours</p>
          <h1>{{ room.story.title }}</h1>
        </div>
        <button
          type="button"
          class="btn btn-secondary"
          aria-haspopup="dialog"
          data-test="open-assignment"
          @click="panelOpen = true"
        >
          {{ isCreator ? 'Modifier les personnages' : 'Voir les personnages' }}
        </button>
      </div>
      <p class="lead">
        La lecture a commencé. L'écran de lecture arrive bientôt : en attendant, voici qui lit quel
        personnage.
      </p>
      <ul class="cast" aria-label="Distribution">
        <li v-for="character in state.characters" :key="character.id" data-test="cast">
          <!-- Colour read from the attribution at display time, as the reading screen will. -->
          <span class="name" :style="{ color: live.characterColor(character.id) }">
            {{ character.name }}
          </span>
          <PlayerName
            v-if="live.playerOf(character.id)"
            :pseudo="live.playerOf(character.id)!.pseudo"
          />
        </li>
      </ul>
      <AssignmentPanel
        :open="panelOpen"
        :state="state"
        :me-id="meId"
        :editable="isCreator"
        :error="moveError"
        @close="panelOpen = false"
        @move="(characterId, playerId) => live.move(characterId, playerId)"
      />
    </template>
  </section>
</template>

<style scoped>
.card-reading {
  max-width: 1000px;
}

.muted {
  color: var(--color-text-muted);
}

.back {
  display: inline-block;
  margin-top: 1rem;
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem 1rem;
}

.eyebrow {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.card h1 {
  overflow-wrap: anywhere;
}

.cast {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1.25rem 0 0;
  padding: 0;
  list-style: none;
}

.cast li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.3rem 0.75rem;
}

.cast .name {
  font-weight: 700;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .card-reading {
    padding: 1.25rem;
  }
}
</style>
