import { request } from './http'

/** Same limits as the back (RoomService). */
export const ROOM_PASSWORD_MIN_LENGTH = 4
export const ROOM_PASSWORD_MAX_LENGTH = 64

export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export interface RoomParticipant {
  pseudo: string
  /** Given by the back from its palette. */
  color: string
  isCreator: boolean
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
}
