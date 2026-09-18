const KNOWN_TYPES = new Set([
  'Artifact', 'Battle', 'Conspiracy', 'Creature', 'Dungeon', 'Enchantment',
  'Instant', 'Kindred', 'Land', 'Phenomenon', 'Plane', 'Planeswalker',
  'Scheme', 'Sorcery', 'Vanguard',
]);

export function formatManaValue(value) {
  return Number(value).toString();
}

export function cardColors(card) {
  return card.colors?.length ? card.colors : ['Colorless'];
}

export function cardTypes(card) {
  const words = (card.typeLine || '').split(/[—-]/, 1)[0].trim().split(/\s+/);
  const found = words.filter((word) => KNOWN_TYPES.has(word));
  return found.length ? found : [card.typeLine ? 'Other' : 'Unknown'];
}

// Everything to the right of the em/en dash on a Magic type line is the card's
// sub-type(s) (e.g. "Legendary Creature — Elf Wizard" -> "Elf Wizard").
export function cardSubType(card) {
  const parts = (card.typeLine || '').split(/[—]/);
  return parts.length > 1 ? parts[1].trim() : '';
}

export function primaryCardType(card) {
  return cardTypes(card)[0] || '';
}

// Power / toughness are only meaningful for creatures. When absent we fall back
// to the card name so non-creatures sort predictably alongside creatures.
export function creaturePower(card) {
  const value = card.power;
  return value === undefined || value === null || value === '' ? null : value;
}

export function creatureToughness(card) {
  const value = card.toughness;
  return value === undefined || value === null || value === '' ? null : value;
}

export function releaseDate(card) {
  return card.releasedAt || card.releaseDate || '';
}
