import { computed, reactive, ref, shallowRef } from 'vue'
import { roomsApi, type BoardPlayer, type RoomState } from '@/api/rooms'
import { ApiError } from '@/api/http'
import { followRoom } from '@/api/realtime'

/**
 * Shared state of a room (players, attribution, status), kept in sync with the server, which is the
 * source of truth. A state older than the displayed one is dropped (out of order delivery between
 * the API and the WebSocket). Moves are shown at once and undone if the server refuses them.
 */
export function useRoomState(code: string) {
  const meId = ref<string | null>(null)
  /** Last state received from the server. */
  const confirmed = shallowRef<RoomState | null>(null)
  /** Moves sent and not answered yet: character id → player id. */
  const pending = reactive(new Map<string, string>())
  const moveError = ref<string | null>(null)

  /** What the page shows: the server state with the pending moves on top. */
  const state = computed<RoomState | null>(() => {
    const current = confirmed.value
    if (!current || pending.size === 0) {
      return current
    }
    return {
      ...current,
      characters: current.characters.map((c) => {
        const playerId = pending.get(c.id)
        return playerId ? { ...c, playerId } : c
      }),
    }
  })

  /** Player of each character, as shown (pending moves included): character id → player. */
  const playersByCharacter = computed(() => {
    const players = new Map<string, BoardPlayer>()
    const current = state.value
    if (!current) {
      return players
    }
    const byId = new Map(current.players.map((p) => [p.id, p]))
    for (const c of current.characters) {
      const player = byId.get(c.playerId)
      if (player) {
        players.set(c.id, player)
      }
    }
    return players
  })

  /** Whether the current player created the room: they alone change the attribution. */
  const isCreator = computed(
    () => state.value?.players.find((p) => p.id === meId.value)?.isCreator ?? false,
  )

  /**
   * Player of a character (character → player), for the current reader indicator (US-09).
   * Reactive when called in a template or a computed.
   */
  function playerOf(characterId: string): BoardPlayer | undefined {
    return playersByCharacter.value.get(characterId)
  }

  /**
   * Colour of a character's name (character → player → colour), undefined for an unknown
   * character. To be called at display time, never stored with the rendered text: a move then
   * recolours every name by itself (US-07, US-08).
   */
  function characterColor(characterId: string): string | undefined {
    return playerOf(characterId)?.color
  }

  /** Same version accepted: presence changes are not versioned. */
  function apply(next: RoomState) {
    if (confirmed.value && next.version < confirmed.value.version) {
      return
    }
    confirmed.value = next
  }

  /** Full state, on arrival and after each reconnection (broadcasts missed meanwhile are lost). */
  async function load() {
    const board = await roomsApi.board(code)
    meId.value = board.meId
    apply(board.state)
  }

  async function move(characterId: string, playerId: string) {
    moveError.value = null
    pending.set(characterId, playerId)
    try {
      apply(await roomsApi.assign(code, characterId, playerId))
    } catch (e) {
      moveError.value =
        e instanceof ApiError
          ? `Déplacement annulé : ${e.message}`
          : 'Déplacement annulé : le serveur est injoignable. Réessaie.'
    } finally {
      // A later move of the same character is still waiting for its own answer.
      if (pending.get(characterId) === playerId) {
        pending.delete(characterId)
      }
    }
  }

  /** Live updates until the returned function is called. */
  function follow(): () => void {
    return followRoom(code, {
      onEvent: (event) => {
        if (event.type === 'STATE') {
          apply(event.state)
        }
      },
      onConnect: () => {
        load().catch(() => undefined)
      },
    })
  }

  return {
    meId,
    state,
    isCreator,
    moveError,
    playerOf,
    characterColor,
    apply,
    load,
    move,
    follow,
  }
}
