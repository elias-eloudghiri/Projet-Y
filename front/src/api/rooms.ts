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

/** A player of the shared room state: same for everyone, no `isMe` (see `RoomBoard.meId`). */
export interface BoardPlayer {
  /** Participant id, used to target a player; neither a user id nor a guest id. */
  id: string
  pseudo: string
  color: string
  isGuest: boolean
  isCreator: boolean
  /** Whether the player has the room open. */
  connected: boolean
}

export interface BoardCharacter {
  id: string
  name: string
  isNarrator: boolean
  /** Always set: a character has exactly one player. */
  playerId: string
}

/** Shared state of a room, pushed live to every player (see `realtime.ts`). */
export interface RoomState {
  /** A state older than the displayed one is dropped; presence changes keep the same version. */
  version: number
  status: RoomStatus
  playerCount: number
  /** In arrival order, the creator first. */
  players: BoardPlayer[]
  /** In story order, the narrator first. */
  characters: BoardCharacter[]
}

export interface RoomBoard {
  /** The current player's id in `state.players`. */
  meId: string
  state: RoomState
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
  /** Full state; 404 for non-participants. */
  board: (code: string) =>
    request<RoomBoard>('GET', `/api/rooms/${encodeURIComponent(code)}/board`),
  /** Creator only. Returns the new state, also pushed to every player. */
  assign: (code: string, characterId: string, participantId: string) =>
    request<RoomState>(
      'PUT',
      `/api/rooms/${encodeURIComponent(code)}/assignments/${encodeURIComponent(characterId)}`,
      { participantId },
    ),
  /** Creator only, once every player is in and has a character. */
  start: (code: string) =>
    request<RoomState>('POST', `/api/rooms/${encodeURIComponent(code)}/start`),
}

/** Whether a player has no character, which prevents the start. */
export function hasNoCharacter(state: RoomState, playerId: string): boolean {
  return !state.characters.some((c) => c.playerId === playerId)
}
