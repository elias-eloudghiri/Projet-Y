import { request } from './http'

/** Same limits as the back (StoryService). */
export const NARRATOR_NAME = 'Narrateur'
export const TITLE_MAX_LENGTH = 120
export const DESCRIPTION_MAX_LENGTH = 2000
export const CHARACTER_NAME_MAX_LENGTH = 60
/** Characters besides the narrator. */
export const MAX_CHARACTERS = 50
export const MIN_PLAYERS = 2
/** Choices for the maximum number of players of a story. */
export const MAX_PLAYERS_OPTIONS = [2, 3, 4] as const

/** A story of the catalogue, as players see it (GET /api/stories). */
export interface CatalogStory {
  id: string
  title: string
  description: string
  maxPlayers: number
  /** Narrator first. */
  characters: string[]
}

export interface Story {
  id: string
  title: string
  description: string
  maxPlayers: number
  /** Narrator first. */
  characters: string[]
  pdfSize: number
  createdAt: string
}

export interface NewStory {
  title: string
  description: string
  maxPlayers: number
  /** Characters besides the narrator, who is added by the back. */
  characters: string[]
}

export interface UploadLimits {
  pdfMaxSize: number
}

export const storiesApi = {
  list: () => request<CatalogStory[]>('GET', '/api/stories'),
  get: (id: string) => request<CatalogStory>('GET', `/api/stories/${encodeURIComponent(id)}`),
}

export const adminStoriesApi = {
  list: () => request<Story[]>('GET', '/api/admin/stories'),
  limits: () => request<UploadLimits>('GET', '/api/admin/stories/limits'),
  create: (story: NewStory, pdf: File) => {
    const form = new FormData()
    form.append('story', new Blob([JSON.stringify(story)], { type: 'application/json' }))
    form.append('pdf', pdf)
    return request<Story>('POST', '/api/admin/stories', form)
  },
}

/** Same wording as the back error messages. */
export function formatSize(bytes: number): string {
  const mega = 1024 * 1024
  if (bytes >= mega) {
    return bytes % mega === 0
      ? `${bytes / mega} Mo`
      : `${(bytes / mega).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`
  }
  return `${Math.max(1, Math.floor(bytes / 1024))} Ko`
}
