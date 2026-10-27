import { Client } from '@stomp/stompjs'
import { authApi } from './auth'
import type { RoomParticipant } from './rooms'

/** Same for every player: no `isMe`, the front recognizes its player by its colour. */
export type RoomEvent = {
  type: 'PARTICIPANTS'
  participants: Omit<RoomParticipant, 'isMe'>[]
}

export interface RoomLiveHandlers {
  onEvent: (event: RoomEvent) => void
  /** Called on each (re)connection: events sent while disconnected are lost, reload the state. */
  onConnect: () => void
}

/** Same origin as the page: the auth cookies go with the WebSocket handshake. */
function endpoint(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/ws`
}

/**
 * Follows a room over STOMP until the returned function is called. Reconnects by itself after a
 * network loss; access is checked by the back (participants only).
 */
export function followRoom(code: string, handlers: RoomLiveHandlers): () => void {
  const client = new Client({
    brokerURL: endpoint(),
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // The handshake is refused once the access token has expired: an API call refreshes it first.
    beforeConnect: async () => {
      await authApi.me().catch(() => undefined)
    },
    onConnect: () => {
      client.subscribe(`/topic/rooms/${code}`, (message) => {
        handlers.onEvent(JSON.parse(message.body) as RoomEvent)
      })
      handlers.onConnect()
    },
    // Refused subscription (no longer a participant…): retrying would not help.
    onStompError: () => {
      void client.deactivate()
    },
  })
  client.activate()
  return () => {
    void client.deactivate()
  }
}
