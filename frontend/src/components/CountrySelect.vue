<template>
  <select v-model="model" class="country-select" :aria-label="ariaLabel">
    <option value="">{{ placeholder }}</option>
    <optgroup label="Fréquents">
      <option v-for="c in COMMON_COUNTRIES" :key="`common-${c.code}`" :value="c.code">{{ c.name }}</option>
    </optgroup>
    <optgroup label="Tous les pays">
      <option v-for="c in COUNTRIES" :key="c.code" :value="c.code">{{ c.name }}</option>
    </optgroup>
  </select>
</template>

<script setup>
import { COMMON_COUNTRIES, COUNTRIES } from '../countries'

// Chaine vide = pas de nationalite renseignee.
const model = defineModel({ type: String, default: '' })

defineProps({
  placeholder: { type: String, default: 'Nationalité' },
  ariaLabel: { type: String, default: 'Nationalité' }
})
</script>

<style scoped>
/* Certains noms de pays sont tres longs : on borne la largeur du select. */
.country-select {
  max-width: 230px;
}
</style>
