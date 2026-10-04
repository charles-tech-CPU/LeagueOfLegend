<template>
  <span v-if="isKnownCountry(code)" class="country">
    <span class="fi flag" :class="`fi-${code.toLowerCase()}`" :title="countryName(code)" role="img" :aria-label="countryName(code)"></span>
    <span v-if="showCode" class="country-code">{{ code }}</span>
  </span>
  <span v-else-if="code" class="country-code">{{ code }}</span>
</template>

<script setup>
import { countryName, isKnownCountry } from '../countries'

defineProps({
  code: { type: String, default: null },
  showCode: { type: Boolean, default: true }
})
</script>

<style scoped>
.country {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.flag {
  width: 1.35em;
  line-height: 1em;
  border-radius: 3px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12);
}
.country-code {
  font-size: 0.8em;
  font-weight: 700;
  color: var(--text-muted);
}
</style>
