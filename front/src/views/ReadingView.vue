<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { roomsApi, type Room } from '@/api/rooms'
import { ApiError } from '@/api/http'
import { useRoomState } from '@/composables/useRoomState'
import AssignmentBoard from '@/components/AssignmentBoard.vue'

/**
 * Temporary reading page, reached by every player when the creator starts the reading. The
 * reading screen itself comes with US-08; until then it recalls who plays which character.
 */
const route = useRoute()
const router = useRouter()
const code = String(route.params.code).toUpperCase()
const live = useRoomState(code)
const { meId, state } = live

const room = ref<Room | null>(null)
const error = ref<string | null>(null)
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
      <p class="eyebrow">Lecture en cours</p>
      <h1>{{ room.story.title }}</h1>
      <p class="lead">
        La lecture a commencé. L'écran de lecture arrive bientôt : en attendant, voici qui lit quel
        personnage.
      </p>
      <AssignmentBoard :state="state" :me-id="meId" :editable="false" />
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

.eyebrow {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.card h1 {
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .card-reading {
    padding: 1.25rem;
  }
}
</style>
