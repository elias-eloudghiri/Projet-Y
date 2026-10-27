import { request } from './http'

/** Same limits as the back (RoomService). */
export const ROOM_PASSWORD_MIN_LENGTH = 4
export const ROOM_PASSWORD_MAX_LENGTH = 64

export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export interface RoomParticipant {
  pseudo: string
  /** Given by the back from its palette. */
  color: string
  isGuest: boolean
  isCreator: boolean
  /** The current player. */
  isMe: boolean
}

export interface JoinResult {
  /** The current player's participation. */
  participant: RoomParticipant
  participants: RoomParticipant[]
}

export interface Room {
  /** Public identifier, also used in the share link. */
  code: string
  shareUrl: string
  story: { id: string; title: string; maxPlayers: number }
  playerCount: number
  status: RoomStatus
  participants: RoomParticipant[]
}

export interface NewRoom {
  storyId: string
  playerCount: number
  password: string
}

export const roomsApi = {
  create: (room: NewRoom) => request<Room>('POST', '/api/rooms', room),
  get: (code: string) => request<Room>('GET', `/api/rooms/${encodeURIComponent(code)}`),
  /** 403 ROOM_FORBIDDEN when the current player is not in the room yet. */
  participants: (code: string) =>
    request<RoomParticipant[]>('GET', `/api/rooms/${encodeURIComponent(code)}/participants`),
  /** Idempotent: a participant gets back their seat and colour. */
  join: (code: string, password: string) =>
    request<JoinResult>('POST', `/api/rooms/${encodeURIComponent(code)}/join`, { password }),
}
