<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  adminStoriesApi,
  CHARACTER_NAME_MAX_LENGTH,
  DESCRIPTION_MAX_LENGTH,
  formatSize,
  MAX_CHARACTERS,
  NARRATOR_NAME,
  MAX_PLAYERS_OPTIONS,
  TITLE_MAX_LENGTH,
} from '@/api/stories'
import { ApiError } from '@/api/http'

const router = useRouter()

const title = ref('')
const description = ref('')
const maxPlayers = ref<number>(2)
/** Characters besides the narrator, who is always present and cannot be removed. */
const characters = ref<{ id: number; name: string }[]>([{ id: 0, name: '' }])
let nextId = 1
const pdf = ref<File | null>(null)
const pdfMaxSize = ref<number | null>(null)

/** Field errors are only shown once a submission has been attempted. */
const submitted = ref(false)
const submitting = ref(false)
const serverError = ref<string | null>(null)

onMounted(async () => {
  try {
    pdfMaxSize.value = (await adminStoriesApi.limits()).pdfMaxSize
  } catch {
    // The back checks the size anyway.
  }
})

function addCharacter() {
  characters.value.push({ id: nextId++, name: '' })
}

function removeCharacter(id: number) {
  characters.value = characters.value.filter((c) => c.id !== id)
}

function onPdfChange(event: Event) {
  pdf.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

const errors = computed(() => {
  const result: {
    title?: string
    description?: string
    characters: Record<number, string>
    charactersCount?: string
    pdf?: string
  } = { characters: {} }

  const t = title.value.trim()
  if (!t) result.title = 'Le titre est obligatoire.'
  else if (t.length > TITLE_MAX_LENGTH)
    result.title = `Le titre doit contenir au plus ${TITLE_MAX_LENGTH} caractères.`

  const d = description.value.trim()
  if (!d) result.description = 'La description est obligatoire.'
  else if (d.length > DESCRIPTION_MAX_LENGTH)
    result.description = `La description doit contenir au plus ${DESCRIPTION_MAX_LENGTH} caractères.`

  const seen = new Set([NARRATOR_NAME.toLowerCase()])
  characters.value.forEach((c, index) => {
    const name = c.name.trim()
    if (!name) {
      result.characters[c.id] = `Le nom du personnage n° ${index + 1} est vide.`
    } else if (name.length > CHARACTER_NAME_MAX_LENGTH) {
      result.characters[c.id] = `Au plus ${CHARACTER_NAME_MAX_LENGTH} caractères.`
    } else if (name.toLowerCase() === NARRATOR_NAME.toLowerCase()) {
      result.characters[c.id] = 'Le narrateur est déjà présent.'
    } else if (seen.has(name.toLowerCase())) {
      result.characters[c.id] = `Le personnage « ${name} » apparaît plusieurs fois.`
    } else {
      seen.add(name.toLowerCase())
    }
  })
  if (characters.value.length + 1 < maxPlayers.value) {
    result.charactersCount =
      `Il faut au moins ${maxPlayers.value} personnages, narrateur compris, pour ` +
      `${maxPlayers.value} joueurs : chaque joueur doit en incarner au moins un.`
  }

  if (!pdf.value) {
    result.pdf = "Le PDF de l'histoire est obligatoire."
  } else if (pdf.value.type !== 'application/pdf') {
    result.pdf = `Le fichier « ${pdf.value.name} » n'est pas un PDF.`
  } else if (pdfMaxSize.value !== null && pdf.value.size > pdfMaxSize.value) {
    result.pdf = `Le PDF dépasse la taille maximale de ${formatSize(pdfMaxSize.value)}.`
  }
  return result
})

const isValid = computed(() => {
  const e = errors.value
  return (
    !e.title &&
    !e.description &&
    !e.charactersCount &&
    !e.pdf &&
    Object.keys(e.characters).length === 0
  )
})

async function submit() {
  submitted.value = true
  serverError.value = null
  if (!isValid.value || !pdf.value) {
    return
  }
  submitting.value = true
  try {
    const story = await adminStoriesApi.create(
      {
        title: title.value.trim(),
        description: description.value.trim(),
        maxPlayers: maxPlayers.value,
        characters: characters.value.map((c) => c.name.trim()),
      },
      pdf.value,
    )
    await router.push({ name: 'admin-stories', query: { created: story.title } })
  } catch (e) {
    serverError.value =
      e instanceof ApiError ? e.message : "Impossible d'ajouter l'histoire. Réessaie plus tard."
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="card card-wide">
    <h1>Ajouter une histoire</h1>
    <p class="lead">
      Le PDF contient le texte et les images de l'histoire. Les personnages saisis ici servent au
      tableau d'attribution.
    </p>

    <form class="form" novalidate @submit.prevent="submit">
      <div class="field">
        <label for="title">Titre</label>
        <input
          id="title"
          v-model="title"
          type="text"
          :maxlength="TITLE_MAX_LENGTH"
          :aria-invalid="submitted && !!errors.title"
          aria-describedby="title-error"
          required
        />
        <span v-if="submitted && errors.title" id="title-error" class="field-error">{{
          errors.title
        }}</span>
      </div>

      <div class="field">
        <label for="description">Description</label>
        <textarea
          id="description"
          v-model="description"
          rows="4"
          :maxlength="DESCRIPTION_MAX_LENGTH"
          :aria-invalid="submitted && !!errors.description"
          aria-describedby="description-error"
          required
        />
        <span class="hint">{{ description.length }} / {{ DESCRIPTION_MAX_LENGTH }}</span>
        <span v-if="submitted && errors.description" id="description-error" class="field-error">{{
          errors.description
        }}</span>
      </div>

      <div class="field">
        <label for="max-players">Nombre maximum de joueurs</label>
        <select id="max-players" v-model.number="maxPlayers">
          <option v-for="count in MAX_PLAYERS_OPTIONS" :key="count" :value="count">
            {{ count }} joueurs
          </option>
        </select>
        <span class="hint">Le créateur d'une room choisira entre 2 joueurs et ce maximum.</span>
      </div>

      <fieldset class="field characters">
        <legend>Personnages</legend>
        <div class="character-row">
          <input :value="NARRATOR_NAME" type="text" disabled aria-label="Narrateur" />
          <span class="badge">par défaut</span>
        </div>
        <div v-for="(character, index) in characters" :key="character.id" class="character-item">
          <div class="character-row">
            <input
              v-model="character.name"
              type="text"
              :maxlength="CHARACTER_NAME_MAX_LENGTH"
              :aria-label="`Personnage n° ${index + 1}`"
              :aria-invalid="submitted && !!errors.characters[character.id]"
              placeholder="Nom du personnage"
            />
            <button
              type="button"
              class="btn btn-secondary btn-icon"
              :aria-label="`Supprimer le personnage n° ${index + 1}`"
              @click="removeCharacter(character.id)"
            >
              ✕
            </button>
          </div>
          <span v-if="submitted && errors.characters[character.id]" class="field-error">{{
            errors.characters[character.id]
          }}</span>
        </div>
        <button
          type="button"
          class="btn btn-secondary add"
          :disabled="characters.length >= MAX_CHARACTERS"
          @click="addCharacter"
        >
          Ajouter un personnage
        </button>
        <span v-if="submitted && errors.charactersCount" class="field-error">{{
          errors.charactersCount
        }}</span>
      </fieldset>

      <div class="field">
        <label for="pdf">PDF de l'histoire</label>
        <input
          id="pdf"
          type="file"
          accept="application/pdf,.pdf"
          :aria-invalid="submitted && !!errors.pdf"
          aria-describedby="pdf-error"
          required
          @change="onPdfChange"
        />
        <span v-if="pdfMaxSize !== null" class="hint"
          >Taille maximale : {{ formatSize(pdfMaxSize) }}.</span
        >
        <span v-if="submitted && errors.pdf" id="pdf-error" class="field-error">{{
          errors.pdf
        }}</span>
      </div>

      <p v-if="serverError" class="alert alert-error" role="alert">{{ serverError }}</p>
      <p v-else-if="submitted && !isValid" class="alert alert-error" role="alert">
        Corrige les champs signalés.
      </p>

      <div class="actions">
        <RouterLink :to="{ name: 'admin-stories' }" class="btn btn-secondary">Annuler</RouterLink>
        <button type="submit" class="btn" :disabled="submitting">
          {{ submitting ? 'Envoi…' : "Ajouter l'histoire" }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.card-wide {
  max-width: 640px;
}

.characters {
  gap: 0.5rem;
  padding: 0;
  border: none;
}

.characters legend {
  margin-bottom: 0.35rem;
  font-weight: 600;
  font-size: 0.9rem;
}

.character-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.character-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.character-row input {
  flex: 1;
  min-width: 0;
}

.badge {
  flex: none;
  padding: 0.05em 0.6em;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border-hover);
  border-radius: 999px;
}

.btn-icon {
  flex: none;
  padding: 0.55rem 0.8rem;
}

.add {
  align-self: flex-start;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
