<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ROOM_PASSWORD_MAX_LENGTH, roomsApi, type Room, type RoomParticipant } from '@/api/rooms'
import { ApiError } from '@/api/http'
import { followRoom } from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import PlayerName from '@/components/PlayerName.vue'

/**
 * Share link and waiting room. Anonymous visitors log in or play as guests first; non-participants
 * give the room password. Everything is reloaded from the API, so the page survives a refresh.
 */
const route = useRoute()
const auth = useAuthStore()
const code = String(route.params.code)

type Step = 'loading' | 'identify' | 'password' | 'room' | 'error'
const step = ref<Step>('loading')
const room = ref<Room | null>(null)
const error = ref<string | null>(null)

const guestError = ref<string | null>(null)
const joiningAsGuest = ref(false)

const password = ref('')
const passwordEl = ref<HTMLInputElement | null>(null)
const passwordError = ref<string | null>(null)
const submitting = ref(false)

const linkEl = ref<HTMLElement | null>(null)
const copyState = ref<'idle' | 'copied' | 'manual'>('idle')

/** Live updates of the waiting room, while the page is open. */
let stopFollowing: (() => void) | null = null

onMounted(enter)
onUnmounted(() => stopFollowing?.())

function fail(e: unknown) {
  error.value =
    e instanceof ApiError ? e.message : 'Impossible de charger la room. Réessaie plus tard.'
  step.value = 'error'
}

/** Participants get in directly; the others are asked for the password. */
async function enter() {
  if (!auth.isAuthenticated) {
    step.value = 'identify'
    return
  }
  try {
    await roomsApi.participants(code)
    await loadRoom()
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) {
      step.value = 'password'
      await focusPassword()
    } else if (e instanceof ApiError && e.status === 401) {
      // Session gone (e.g. expired guest token): identify again.
      auth.user = null
      step.value = 'identify'
    } else {
      fail(e)
    }
  }
}

async function loadRoom() {
  room.value = await roomsApi.get(code)
  step.value = 'room'
  stopFollowing ??= followRoom(room.value.code, {
    onEvent: (event) => {
      if (event.type === 'PARTICIPANTS') {
        applyParticipants(event.participants)
      }
    },
    // Joins missed while disconnected: reload the list (and who the current player is).
    onConnect: () => {
      roomsApi
        .participants(code)
        .then(applyParticipants)
        .catch(() => undefined)
    },
  })
}

/** Live list: the current player is recognized by its colour, unique in the room. */
function applyParticipants(participants: Omit<RoomParticipant, 'isMe'>[]) {
  if (!room.value) {
    return
  }
  const myColor = room.value.participants.find((p) => p.isMe)?.color
  room.value.participants = participants.map((p) => ({
    ...p,
    isMe: 'isMe' in p ? Boolean(p.isMe) : p.color === myColor,
  }))
}

async function playAsGuest() {
  guestError.value = null
  joiningAsGuest.value = true
  try {
    await auth.playAsGuest()
    await enter()
  } catch (e) {
    guestError.value =
      e instanceof ApiError ? e.message : 'Impossible de jouer en invité. Réessaie plus tard.'
  } finally {
    joiningAsGuest.value = false
  }
}

async function focusPassword() {
  await nextTick()
  passwordEl.value?.focus()
}

async function join() {
  if (!password.value) {
    passwordError.value = 'Saisis le mot de passe de la room.'
    await focusPassword()
    return
  }
  passwordError.value = null
  submitting.value = true
  try {
    await roomsApi.join(code, password.value)
  } catch (e) {
    submitting.value = false
    if (e instanceof ApiError && e.status === 404) {
      fail(e)
      return
    }
    passwordError.value =
      e instanceof ApiError ? e.message : 'Impossible de rejoindre la room. Réessaie plus tard.'
    await focusPassword()
    return
  }
  try {
    await loadRoom()
  } catch (e) {
    fail(e)
  } finally {
    submitting.value = false
  }
}

async function copyLink() {
  const url = room.value?.shareUrl ?? ''
  let ok = false
  try {
    await navigator.clipboard.writeText(url)
    ok = true
  } catch {
    // Clipboard API refused (permissions, non-secure context): select the text and try the legacy command.
    if (linkEl.value) {
      window.getSelection()?.selectAllChildren(linkEl.value)
      ok = document.execCommand('copy')
    }
  }
  copyState.value = ok ? 'copied' : 'manual'
  if (ok) {
    setTimeout(() => (copyState.value = 'idle'), 2000)
  }
}
</script>

<template>
  <section class="card card-wide">
    <p v-if="step === 'loading'" class="muted">Chargement…</p>
    <template v-else-if="step === 'error'">
      <h1>Room introuvable</h1>
      <p class="alert alert-error" role="alert">{{ error }}</p>
      <RouterLink :to="{ name: 'home' }" class="back">Retour à l'accueil</RouterLink>
    </template>

    <template v-else-if="step === 'identify'">
      <p class="eyebrow">Rejoindre une room</p>
      <h1>Qui es-tu ?</h1>
      <p class="lead">
        Connecte-toi à ton compte ou joue en invité, puis saisis le mot de passe de la room.
      </p>
      <div class="choices">
        <RouterLink :to="{ name: 'login', query: { redirect: route.fullPath } }" class="btn">
          Se connecter
        </RouterLink>
        <button
          type="button"
          class="btn btn-secondary"
          :disabled="joiningAsGuest"
          @click="playAsGuest"
        >
          {{ joiningAsGuest ? 'Connexion…' : 'Jouer en invité' }}
        </button>
      </div>
      <p v-if="guestError" class="alert alert-error guest-error" role="alert">{{ guestError }}</p>
    </template>

    <template v-else-if="step === 'password'">
      <p class="eyebrow">Rejoindre une room</p>
      <h1>
        Room <span class="code-value">{{ code.toUpperCase() }}</span>
      </h1>
      <p class="lead">Saisis le mot de passe que t'a donné le créateur de la room.</p>
      <form class="form" novalidate @submit.prevent="join">
        <div class="field">
          <label for="room-password">Mot de passe de la room</label>
          <input
            id="room-password"
            ref="passwordEl"
            v-model="password"
            type="password"
            autocomplete="off"
            :maxlength="ROOM_PASSWORD_MAX_LENGTH"
            :aria-invalid="!!passwordError"
            aria-describedby="room-password-error"
            required
          />
          <span v-if="passwordError" id="room-password-error" class="field-error" role="alert">{{
            passwordError
          }}</span>
        </div>
        <button type="submit" class="btn" :disabled="submitting">
          {{ submitting ? 'Connexion…' : 'Rejoindre' }}
        </button>
      </form>
      <p v-if="auth.user" class="hint as">
        Tu rejoindras en tant que <PlayerName :pseudo="auth.user.pseudo" :role="auth.user.role" />
      </p>
    </template>

    <template v-else-if="step === 'room' && room">
      <p class="eyebrow">Salle d'attente</p>
      <h1>{{ room.story.title }}</h1>
      <p class="code">
        Code <span class="code-value">{{ room.code }}</span>
      </p>

      <div class="share">
        <span class="label">Lien de partage</span>
        <div class="share-box">
          <span ref="linkEl" class="share-url">{{ room.shareUrl }}</span>
          <button type="button" class="btn btn-secondary" @click="copyLink">
            {{ copyState === 'copied' ? 'Copié !' : 'Copier' }}
          </button>
        </div>
        <p v-if="copyState === 'manual'" class="hint">
          Copie automatique impossible : le lien est sélectionné, fais Ctrl+C (ou ⌘+C).
        </p>
        <p class="hint">Envoie ce lien et le mot de passe de la room à tes joueurs.</p>
      </div>

      <div class="players">
        <div class="players-head">
          <h2>Joueurs</h2>
          <span class="count">{{ room.participants.length }} / {{ room.playerCount }} joueurs</span>
        </div>
        <ul class="participants">
          <li
            v-for="participant in room.participants"
            :key="participant.pseudo"
            :class="{ me: participant.isMe }"
            :aria-current="participant.isMe ? 'true' : undefined"
          >
            <PlayerName
              :pseudo="participant.pseudo"
              :color="participant.color"
              :role="participant.isGuest ? 'GUEST' : undefined"
            />
            <span v-if="participant.isMe" class="you">toi</span>
            <span v-if="participant.isCreator" class="role">créateur</span>
          </li>
        </ul>
      </div>
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

.eyebrow {
  color: var(--color-text-muted);
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.card h1 {
  overflow-wrap: anywhere;
}

.code {
  color: var(--color-text-muted);
}

.code-value {
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 1.1rem;
  letter-spacing: 0.08em;
  color: var(--color-heading);
}

.share {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 1.5rem;
}

.label {
  font-weight: 600;
  font-size: 0.9rem;
}

.share-box {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.6rem 0.6rem 0.9rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.share-url {
  flex: 1;
  min-width: 0;
  font-family: var(--font-mono);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
  user-select: all;
}

.hint {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.players {
  margin-top: 1.75rem;
}

.players-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
}

.players-head h2 {
  font-size: 1.1rem;
}

.count {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.participants {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.75rem;
  padding: 0;
  list-style: none;
}

.participants li {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.9rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.participants li.me {
  border-color: var(--color-accent);
  box-shadow: inset 3px 0 0 var(--color-accent);
}

.you {
  color: var(--color-heading);
  font-size: 0.85rem;
  font-weight: 600;
}

.role {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.choices {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.guest-error {
  margin-top: 1rem;
}

.as {
  margin-top: 1.25rem;
}
</style>
