import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { VueDraggable } from 'vue-draggable-plus'
import AssignmentBoard from '../AssignmentBoard.vue'
import type { RoomState } from '@/api/rooms'

const state: RoomState = {
  version: 3,
  status: 'WAITING',
  playerCount: 3,
  players: [
    {
      id: 'p-creator',
      pseudo: 'lerat-000001',
      color: '#60A5FA',
      isGuest: false,
      isCreator: true,
      connected: true,
    },
    {
      id: 'p-guest',
      pseudo: 'lechat-000002',
      color: '#F472B6',
      isGuest: true,
      isCreator: false,
      connected: false,
    },
    {
      id: 'p-third',
      pseudo: 'lours-000003',
      color: '#FBBF24',
      isGuest: false,
      isCreator: false,
      connected: true,
    },
  ],
  characters: [
    { id: 'c-narrator', name: 'Narrateur', isNarrator: true, playerId: 'p-guest' },
    { id: 'c-alice', name: 'Alice', isNarrator: false, playerId: 'p-creator' },
    { id: 'c-bob', name: 'Bob', isNarrator: false, playerId: 'p-creator' },
  ],
}

function mountBoard(editable: boolean, meId = 'p-creator') {
  return mount(AssignmentBoard, { props: { state, meId, editable } })
}

describe('AssignmentBoard', () => {
  it('shows each player with its colour, pseudo and characters, narrator included', () => {
    const players = mountBoard(true).findAll('[data-test="player"]')

    expect(players).toHaveLength(3)
    const [creator, guest, third] = players
    expect(creator!.text()).toContain('lerat-000001')
    expect(creator!.text()).toContain('créateur')
    expect(creator!.findAll('[data-test="character"]').map((c) => c.find('.name').text())).toEqual([
      'Alice',
      'Bob',
    ])
    expect(creator!.attributes('style')).toContain('--player-color: #60A5FA')
    expect(guest!.text()).toContain('invité')
    expect(guest!.text()).toContain('Narrateur')
    expect(guest!.text()).toContain('narrateur')
    expect(third!.text()).toContain('Aucun personnage')
  })

  it('marks disconnected players, but never the current one', () => {
    const players = mountBoard(false, 'p-guest').findAll('[data-test="player"]')
    expect(players[1]!.find('[data-test="offline"]').exists()).toBe(false)

    const others = mountBoard(false, 'p-creator').findAll('[data-test="player"]')
    expect(others[1]!.find('[data-test="offline"]').exists()).toBe(true)
    expect(others[2]!.find('[data-test="offline"]').exists()).toBe(false)
  })

  it('is read-only for the other players', () => {
    const wrapper = mountBoard(false, 'p-guest')

    expect(wrapper.findAll('select')).toHaveLength(0)
    const lists = wrapper.findAllComponents(VueDraggable)
    expect(lists).toHaveLength(3)
    for (const list of lists) {
      expect(list.props('disabled')).toBe(true)
    }
  })

  it('lets the creator move a character from the keyboard menu', async () => {
    const wrapper = mountBoard(true)
    const lists = wrapper.findAllComponents(VueDraggable)
    for (const list of lists) {
      expect(list.props('disabled')).toBe(false)
    }

    const alice = wrapper.findAll('[data-test="character"]').find((c) => c.text().includes('Alice'))
    await alice!.find('select').setValue('p-third')

    expect(wrapper.emitted('move')).toEqual([['c-alice', 'p-third']])
  })

  it('does not emit a move when the same player is chosen again', async () => {
    const wrapper = mountBoard(true)
    const narrator = wrapper
      .findAll('[data-test="character"]')
      .find((c) => c.text().includes('Narrateur'))

    await narrator!.find('select').setValue('p-guest')

    expect(wrapper.emitted('move')).toBeUndefined()
  })
})
