// Libelles des splits et des formats de phase (voir CompetitionSplit / StageFormat cote backend).

export const SPLITS = [
  { key: 'WINTER', label: 'Winter' },
  { key: 'SPRING', label: 'Spring' },
  { key: 'SUMMER', label: 'Summer' }
]

export function splitLabel(key) {
  return SPLITS.find(s => s.key === key)?.label ?? ''
}

export const STAGE_FORMATS = [
  { key: 'ROUND_ROBIN', label: 'Poule (aller simple)', hint: 'Chaque équipe affronte une fois chacune des autres, dans une ou plusieurs poules.' },
  { key: 'DOUBLE_ROUND_ROBIN', label: 'Poule (aller-retour)', hint: 'Chaque équipe affronte deux fois chacune des autres (aller puis retour).' },
  { key: 'SWISS', label: 'Ronde suisse', hint: "À chaque ronde, les équipes au même bilan s'affrontent. Nombre pair d'équipes ; les rondes suivantes se génèrent au fil des résultats." },
  { key: 'SINGLE_ELIMINATION', label: 'Élimination simple', hint: "Bracket à élimination directe dans l'ordre des têtes de série ; les meilleures sont exemptées si le nombre n'est pas une puissance de 2." },
  { key: 'DOUBLE_ELIMINATION', label: 'Double élimination', hint: 'Bracket des vainqueurs, bracket des perdants et grande finale (4, 8, 16 ou 32 équipes).' }
]

export function formatLabel(key) {
  return STAGE_FORMATS.find(f => f.key === key)?.label ?? 'Autre'
}

export const BEST_OFS = ['BO1', 'BO2', 'BO3', 'BO5']
