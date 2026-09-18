import { cardSubType, creaturePower, creatureToughness, primaryCardType, releaseDate } from './cardFormatting';

// Sort definitions: each returns a comparable primary key for a card. Ties are
// always broken by sub-type and then by name.
export const SORT_KEYS = {
  name: { label: 'Name', numeric: false, key: (card) => (card.name || '').toLowerCase() },
  manaValue: { label: 'Mana Value', numeric: true, key: (card) => Number(card.manaValue) || 0 },
  cardType: { label: 'Card Type', numeric: false, key: (card) => primaryCardType(card).toLowerCase() },
  power: {
    label: 'Power', numeric: true,
    key: (card) => {
      const value = creaturePower(card);
      return value === null ? Number.POSITIVE_INFINITY : Number(value);
    },
  },
  toughness: {
    label: 'Toughness', numeric: true,
    key: (card) => {
      const value = creatureToughness(card);
      return value === null ? Number.POSITIVE_INFINITY : Number(value);
    },
  },
  releaseDate: { label: 'Release Date', numeric: false, key: (card) => releaseDate(card) },
};

export function compareCards(a, b, sortKey, direction) {
  const definition = SORT_KEYS[sortKey] || SORT_KEYS.name;
  const av = definition.key(a);
  const bv = definition.key(b);
  let primary;
  if (definition.numeric) {
    primary = av - bv;
  } else {
    primary = String(av).localeCompare(String(bv));
  }
  if (primary !== 0) return direction === 'desc' ? -primary : primary;
  // Ties: sub-type, then name (always ascending for a stable secondary order).
  const subtype = cardSubType(a).toLowerCase().localeCompare(cardSubType(b).toLowerCase());
  if (subtype !== 0) return subtype;
  return (a.name || '').toLowerCase().localeCompare((b.name || '').toLowerCase());
}

export function sortCards(cards, sortKey, direction) {
  return [...cards].sort((a, b) => compareCards(a, b, sortKey, direction));
}
