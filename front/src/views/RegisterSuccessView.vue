<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const pseudoEl = ref<HTMLElement | null>(null)
const copyState = ref<'idle' | 'copied' | 'manual'>('idle')
const acknowledged = ref(false)

async function copy() {
  const pseudo = auth.user?.pseudo ?? ''
  let ok = false
  try {
    await navigator.clipboard.writeText(pseudo)
    ok = true
  } catch {
    // Clipboard API refused (permissions, non-secure context): select the text and try the legacy command.
    if (pseudoEl.value) {
      window.getSelection()?.selectAllChildren(pseudoEl.value)
      ok = document.execCommand('copy')
    }
  }
  copyState.value = ok ? 'copied' : 'manual'
  if (ok) {
    setTimeout(() => (copyState.value = 'idle'), 2000)
  }
}
</script>

<template>
  <section class="card">
    <h1>Compte créé</h1>
    <p class="lead">Voici ton pseudo. Il sert à la fois d'identifiant de connexion et de nom affiché.</p>

    <div class="pseudo-box">
      <span ref="pseudoEl" class="pseudo" data-testid="pseudo">{{ auth.user?.pseudo }}</span>
      <button type="button" class="btn btn-secondary" @click="copy">
        {{ copyState === 'copied' ? 'Copié !' : 'Copier' }}
      </button>
    </div>
    <p v-if="copyState === 'manual'" class="manual-hint">
      Copie automatique impossible : le pseudo est sélectionné, fais Ctrl+C (ou ⌘+C).
    </p>

    <div class="alert alert-warning" role="alert">
      <strong>Note ce pseudo maintenant.</strong> Il n'y a ni email ni récupération de compte : sans ce
      pseudo et ton mot de passe, ton compte sera définitivement irrécupérable.
    </div>

    <label class="ack">
      <input v-model="acknowledged" type="checkbox" />
      J'ai noté mon pseudo en lieu sûr.
    </label>

    <button type="button" class="btn continue" :disabled="!acknowledged" @click="router.push({ name: 'home' })">
      Continuer
    </button>
  </section>
</template>

<style scoped>
.pseudo-box {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding: 1.25rem;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.pseudo {
  font-family: var(--font-mono);
  font-size: clamp(1.4rem, 6vw, 2rem);
  font-weight: 700;
  color: var(--color-heading);
  word-break: break-all;
  user-select: all;
}

.manual-hint {
  margin: -0.75rem 0 1.5rem;
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.ack {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin: 1.25rem 0;
  cursor: pointer;
}

.ack input {
  width: 1.1rem;
  height: 1.1rem;
  accent-color: var(--color-accent);
}

.continue {
  width: 100%;
}
</style>
