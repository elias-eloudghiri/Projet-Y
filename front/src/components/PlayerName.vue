<script setup lang="ts">
import type { Role } from '@/api/auth'

/**
 * Player identity as shown everywhere (header, rooms, attribution board, reader indicator):
 * the colour and the pseudo replace a profile picture, with an "invité" badge for guests.
 */
const props = defineProps<{
  pseudo: string
  role?: Role
  /** Player colour in a room; omitted outside rooms. */
  color?: string
}>()
</script>

<template>
  <span class="player-name">
    <span v-if="props.color" class="dot" :style="{ background: props.color }" aria-hidden="true" />
    <span class="pseudo" :style="props.color ? { color: props.color } : undefined">{{
      props.pseudo
    }}</span>
    <span v-if="props.role === 'GUEST'" class="badge">invité</span>
  </span>
</template>

<style scoped>
.player-name {
  display: inline-flex;
  align-items: center;
  gap: 0.45em;
  max-width: 100%;
  vertical-align: middle;
}

.dot {
  flex: none;
  width: 0.7em;
  height: 0.7em;
  border-radius: 50%;
}

.pseudo {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-text);
}

.badge {
  flex: none;
  padding: 0.05em 0.5em;
  font-size: 0.75em;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0.02em;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border-hover);
  border-radius: 999px;
}
</style>
