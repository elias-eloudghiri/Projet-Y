<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { roomsApi, type Room } from '@/api/rooms'
import { ApiError } from '@/api/http'
import PlayerName from '@/components/PlayerName.vue'

/** Waiting room: everything is reloaded from the API, so the page survives a refresh. */
const route = useRoute()

const room = ref<Room | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const linkEl = ref<HTMLElement | null>(null)
const copyState = ref<'idle' | 'copied' | 'manual'>('idle')

onMounted(async () => {
  try {
    room.value = await roomsApi.get(String(route.params.code))
  } catch (e) {
    error.value =
      e instanceof ApiError ? e.message : 'Impossible de charger la room. Réessaie plus tard.'
  } finally {
    loading.value = false
  }
})

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
    <p v-if="loading" class="muted">Chargement…</p>
    <template v-else-if="error">
      <h1>Room introuvable</h1>
      <p class="alert alert-error" role="alert">{{ error }}</p>
      <RouterLink :to="{ name: 'stories' }" class="back">Retour au catalogue</RouterLink>
    </template>

    <template v-else-if="room">
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
          <li v-for="participant in room.participants" :key="participant.pseudo">
            <PlayerName :pseudo="participant.pseudo" :color="participant.color" />
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

.role {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}
</style>
