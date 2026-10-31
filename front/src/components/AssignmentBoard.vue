<script setup lang="ts">
import { ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import type { BoardCharacter, BoardPlayer, RoomState } from '@/api/rooms'
import PlayerName from '@/components/PlayerName.vue'

/**
 * Attribution board: one card per player with its characters. When editable (creator), characters
 * are moved by drag and drop or with the "Attribuer à" menu of each character, usable from the
 * keyboard. The drag and drop goes through SortableJS in fallback mode: pointer events instead of
 * the HTML5 API, so that mouse and touch behave the same. The parent applies the move; the board only shows
 * the state it is given.
 */
const props = defineProps<{
  state: RoomState
  /** The current player, highlighted and never shown as disconnected. */
  meId: string | null
  editable: boolean
}>()

const emit = defineEmits<{
  move: [characterId: string, playerId: string]
}>()

interface Column {
  player: BoardPlayer
  characters: BoardCharacter[]
}

/** Local lists, modified by the drag and drop library, rebuilt from every new state. */
const columns = ref<Column[]>([])

watch(
  () => props.state,
  (state) => {
    columns.value = state.players.map((player) => ({
      player,
      characters: state.characters.filter((c) => c.playerId === player.id),
    }))
  },
  { immediate: true },
)

function isOffline(player: BoardPlayer) {
  return !player.connected && player.id !== props.meId
}

/** A character dropped on a player's list. */
function dropped(playerId: string, event: { item: HTMLElement }) {
  const characterId = event.item.dataset.characterId
  if (characterId) {
    emit('move', characterId, playerId)
  }
}

function chosen(character: BoardCharacter, event: Event) {
  const playerId = (event.target as HTMLSelectElement).value
  if (playerId !== character.playerId) {
    emit('move', character.id, playerId)
  }
}
</script>

<template>
  <div class="board" :class="{ editable }">
    <section
      v-for="column in columns"
      :key="column.player.id"
      class="player"
      :class="{ me: column.player.id === meId, offline: isOffline(column.player) }"
      :style="{ '--player-color': column.player.color }"
      :aria-label="`Personnages de ${column.player.pseudo}`"
      data-test="player"
    >
      <header class="player-head">
        <PlayerName
          :pseudo="column.player.pseudo"
          :color="column.player.color"
          :role="column.player.isGuest ? 'GUEST' : undefined"
        />
        <span v-if="column.player.id === meId" class="tag me-tag">toi</span>
        <span v-if="column.player.isCreator" class="tag">créateur</span>
        <span v-if="isOffline(column.player)" class="tag offline-tag" data-test="offline">
          déconnecté
        </span>
      </header>

      <div class="drop">
        <VueDraggable
          v-model="column.characters"
          tag="ul"
          class="characters"
          group="characters"
          :sort="false"
          :disabled="!editable"
          :animation="150"
          :delay="150"
          :delay-on-touch-only="true"
          :force-fallback="true"
          filter=".assign"
          :prevent-on-filter="false"
          ghost-class="ghost"
          @add="dropped(column.player.id, $event)"
        >
          <li
            v-for="character in column.characters"
            :key="character.id"
            class="character"
            :data-character-id="character.id"
            data-test="character"
          >
            <span class="name">{{ character.name }}</span>
            <span v-if="character.isNarrator" class="narrator">narrateur</span>
            <label v-if="editable" class="assign">
              <span aria-hidden="true">Attribuer à</span>
              <select
                :value="character.playerId"
                :aria-label="`Attribuer ${character.name} à`"
                @change="chosen(character, $event)"
              >
                <option v-for="p in state.players" :key="p.id" :value="p.id">
                  {{ p.pseudo }}
                </option>
              </select>
            </label>
          </li>
        </VueDraggable>
        <p v-if="column.characters.length === 0" class="empty">
          {{ editable ? 'Aucun personnage : glisse-en un ici' : 'Aucun personnage' }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.board {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.9rem;
}

.player {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  padding: 0.8rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-top: 3px solid var(--player-color);
  border-radius: 8px;
}

.player.me {
  border-color: var(--color-accent);
  border-top-color: var(--player-color);
}

.player.offline {
  opacity: 0.6;
}

.player-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 0.6rem;
}

.tag {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.me-tag {
  color: var(--color-heading);
  font-weight: 600;
}

.offline-tag {
  color: var(--color-warning);
  font-weight: 600;
}

/* The list fills the card: a character can be dropped anywhere below the player's name. */
.drop {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
}

.characters {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.4rem;
  /* Keeps an empty list a visible drop target. */
  min-height: 2.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.character {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 0.5rem;
  padding: 0.5rem 0.65rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--player-color);
  border-radius: 6px;
}

.editable .character {
  cursor: grab;
  touch-action: manipulation;
}

.editable .character:hover {
  border-color: var(--color-border-hover);
  border-left-color: var(--player-color);
}

.character.ghost {
  opacity: 0.4;
}

.name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
  color: var(--color-text);
}

.narrator {
  padding: 0.05em 0.5em;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border-hover);
  border-radius: 999px;
}

.assign {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-left: auto;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  cursor: default;
}

.assign select {
  max-width: 9.5rem;
  padding: 0.2rem 0.3rem;
  font: inherit;
  color: var(--color-text);
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

.assign select:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

.empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 0.5rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  text-align: center;
  border: 1px dashed var(--color-border-hover);
  border-radius: 6px;
  pointer-events: none;
}

/* Mobile: the cards become a vertical list. */
@media (max-width: 640px) {
  .board {
    grid-template-columns: 1fr;
  }
}
</style>
