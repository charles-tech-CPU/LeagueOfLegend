import { ref } from 'vue'

/**
 * Tri de tableau cliquable (en-tetes "sortable") : memorise la colonne et le
 * sens de tri, et trie une liste a partir de la cle renvoyee par sortKey(item, field).
 */
export function useSort(initialField, sortKey) {
  const sortBy = ref(initialField)
  const sortDir = ref('asc')

  function toggleSort(field) {
    if (sortBy.value === field) {
      sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortBy.value = field
      sortDir.value = 'asc'
    }
  }

  function sortArrow(field) {
    if (sortBy.value !== field) return ''
    return sortDir.value === 'asc' ? '▲' : '▼'
  }

  function sortList(list) {
    return [...list].sort((a, b) => {
      const cmp = sortKey(a, sortBy.value).localeCompare(sortKey(b, sortBy.value))
      return sortDir.value === 'asc' ? cmp : -cmp
    })
  }

  return { sortBy, toggleSort, sortArrow, sortList }
}
