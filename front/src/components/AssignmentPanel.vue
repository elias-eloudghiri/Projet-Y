<script setup lang="ts">
import { ref, watch } from 'vue'
import type { RoomState } from '@/api/rooms'
import AssignmentBoard from '@/components/AssignmentBoard.vue'

/**
 * Attribution board in a side panel, opened over the reading without leaving it (US-07). A native
 * modal dialog: focus kept inside, Escape and a click outside close it. Editable for the creator,
 * read-only for the other players; the parent applies the moves.
 */
const props = defineProps<{
  open: boolean
  state: RoomState
  meId: string | null
  editable: boolean
  /** Last refused move, shown above the board. */
  error: string | null
}>()

const emit = defineEmits<{
  close: []
  move: [characterId: string, playerId: string]
}>()

const dialog = ref<HTMLDialogElement | null>(null)

watch(
  () => [props.open, dialog.value] as const,
  ([open, element]) => {
    if (!element) {
      return
    }
    if (open && !element.open) {
      element.showModal()
    } else if (!open && element.open) {
      element.close()
    }
  },
  { immediate: true },
)

/** A click on the backdrop reaches the dialog itself, not its content. */
function clicked(event: MouseEvent) {
  if (event.target === dialog.value) {
    emit('close')
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="panel"
    aria-labelledby="assignment-panel-title"
    @close="emit('close')"
    @click="clicked"
  >
    <div class="content">
      <header class="head">
        <h2 id="assignment-panel-title">Personnages</h2>
        <button type="button" class="close" aria-label="Fermer" @click="emit('close')">✕</button>
      </header>
      <p class="hint">
        {{
          editable
            ? 'Glisse un personnage vers un joueur, ou utilise « Attribuer à ». La lecture continue, tout le monde voit le changement.'
            : 'Le créateur de la room répartit les personnages.'
        }}
      </p>
      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <AssignmentBoard
        :state="state"
        :me-id="meId"
        :editable="editable"
        @move="(characterId, playerId) => emit('move', characterId, playerId)"
      />
    </div>
  </dialog>
</template>

<style scoped>
.panel {
  position: fixed;
  inset: 0 0 0 auto;
  width: min(440px, 100vw);
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  margin: 0;
  padding: 0;
  color: var(--color-text);
  background: var(--color-surface);
  border: none;
  border-left: 1px solid var(--color-border);
}

.panel::backdrop {
  background: rgb(0 0 0 / 0.5);
}

.content {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1.25rem;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.head h2 {
  margin: 0;
  font-size: 1.2rem;
}

.close {
  padding: 0.3rem 0.55rem;
  font: inherit;
  color: var(--color-text-muted);
  background: none;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
}

.close:hover {
  color: var(--color-text);
  border-color: var(--color-border-hover);
}

.hint {
  color: var(--color-text-muted);
  font-size: 0.9rem;
}
</style>
