import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick } from 'vue'
import type { RoomState } from '@/api/rooms'
import type { RoomLiveHandlers } from '@/api/realtime'
import { ApiError } from '@/api/http'

const api = vi.hoisted(() => ({ board: vi.fn(), assign: vi.fn() }))
const live = vi.hoisted(() => ({ handlers: null as RoomLiveHandlers | null }))

vi.mock('@/api/rooms', () => ({ roomsApi: api }))
vi.mock('@/api/realtime', () => ({
  followRoom: (_code: string, handlers: RoomLiveHandlers) => {
    live.handlers = handlers
    return () => undefined
  },
}))

import { useRoomState } from '../useRoomState'

const CREATOR = '#60A5FA'
const GUEST = '#F472B6'

function roomState(version: number, narratorPlayer: string): RoomState {
  return {
    version,
    status: 'PLAYING',
    playerCount: 2,
    players: [
      {
        id: 'p-creator',
        pseudo: 'lerat-000001',
        color: CREATOR,
        isGuest: false,
        isCreator: true,
        connected: true,
      },
      {
        id: 'p-guest',
        pseudo: 'lechat-000002',
        color: GUEST,
        isGuest: true,
        isCreator: false,
        connected: true,
      },
    ],
    characters: [
      { id: 'c-narrator', name: 'Narrateur', isNarrator: true, playerId: narratorPlayer },
      { id: 'c-alice', name: 'Alice', isNarrator: false, playerId: 'p-creator' },
    ],
  }
}

async function loaded(meId = 'p-guest') {
  api.board.mockResolvedValue({ meId, state: roomState(5, 'p-guest') })
  const room = useRoomState('ABCDEFGH')
  await room.load()
  room.follow()
  return room
}

describe('useRoomState: colour of a character', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    live.handlers = null
  })

  it('follows character → player → colour', async () => {
    const room = await loaded()

    expect(room.characterColor('c-narrator')).toBe(GUEST)
    expect(room.characterColor('c-alice')).toBe(CREATOR)
    expect(room.playerOf('c-narrator')?.pseudo).toBe('lechat-000002')
    expect(room.characterColor('c-unknown')).toBeUndefined()
  })

  it('recolours a displayed name when a move is broadcast, without touching anything else', async () => {
    const room = await loaded()
    // What a template does: the colour is read at display time.
    const narratorColor = computed(() => room.characterColor('c-narrator'))
    const reader = computed(() => room.playerOf('c-narrator')?.pseudo)
    expect(narratorColor.value).toBe(GUEST)

    live.handlers!.onEvent({ type: 'STATE', state: roomState(6, 'p-creator') })
    await nextTick()

    expect(narratorColor.value).toBe(CREATOR)
    expect(reader.value).toBe('lerat-000001')
    expect(room.characterColor('c-alice')).toBe(CREATOR)
    expect(room.state.value?.status).toBe('PLAYING')
  })

  it('ignores a broadcast older than the displayed state', async () => {
    const room = await loaded()
    live.handlers!.onEvent({ type: 'STATE', state: roomState(7, 'p-creator') })

    live.handlers!.onEvent({ type: 'STATE', state: roomState(6, 'p-guest') })

    expect(room.characterColor('c-narrator')).toBe(CREATOR)
  })

  it("shows the creator's move at once and restores the colour if the server refuses it", async () => {
    const room = await loaded('p-creator')
    expect(room.isCreator.value).toBe(true)
    let refuse!: (e: unknown) => void
    api.assign.mockReturnValue(new Promise((_resolve, reject) => (refuse = reject)))

    const moving = room.move('c-narrator', 'p-creator')
    expect(room.characterColor('c-narrator')).toBe(CREATOR)

    refuse(new ApiError(403, 'ROOM_NOT_CREATOR', 'Seul le créateur de la room peut faire ça.'))
    await moving
    expect(room.characterColor('c-narrator')).toBe(GUEST)
    expect(room.moveError.value).toContain('Déplacement annulé')
  })

  it('tells a guest apart from the creator', async () => {
    const room = await loaded('p-guest')

    expect(room.isCreator.value).toBe(false)
  })
})
