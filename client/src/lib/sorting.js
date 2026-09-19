import {
  cardSubType, creaturePower, creatureToughness, primaryCardType, releaseDate,
} from './cardFormatting';

// Parses a Magic power/toughness into a comparable number. These are not always
// plain integers: "*", "1+*", "X", "½", "1½" etc. all appear. A variable value
// of exactly X, Y or Z sorts as 0. A half fraction is normalized to a decimal
// ("1½" -> "1.5", "½" -> "0.5") before extracting the leading numeric portion
// ("1+*" -> 1); values with no numeric portion ("*") and cards with no
// power/toughness at all sort last.
function parsePowerToughness(value) {
  if (value === null || value === undefined || value === '') return Number.POSITIVE_INFINITY;
  const text = String(value).trim();
  if (/^[xyz]$/i.test(text)) return 0;
  const normalized = text.replace(/(\d*)½/, (_match, digits) => (digits ? `${digits}.5` : '0.5'));
  const match = normalized.match(/-?\d*\.?\d+/);
  return match && match[0] !== '' ? Number(match[0]) : Number.POSITIVE_INFINITY;
}

// Parses a release date (ISO "YYYY-MM-DD") into a timestamp. Missing dates sort
// last regardless of direction's primary comparison being reversed by caller.
function parseReleaseDate(value) {
  if (!value) return Number.POSITIVE_INFINITY;
  const time = Date.parse(value);
  return Number.isNaN(time) ? Number.POSITIVE_INFINITY : time;
}

// Sort definitions: each returns a comparable primary key for a card. Ties are
// always broken by sub-type and then by name.
export const SORT_KEYS = {
  name: { label: 'Name', numeric: false, key: (card) => (card.name || '').toLowerCase() },
  manaValue: { label: 'Mana Value', numeric: true, key: (card) => Number(card.manaValue) || 0 },
  cardType: { label: 'Card Type', numeric: false, key: (card) => primaryCardType(card).toLowerCase() },
  power: { label: 'Power', numeric: true, key: (card) => parsePowerToughness(creaturePower(card)) },
  toughness: {
    label: 'Toughness', numeric: true,
    key: (card) => parsePowerToughness(creatureToughness(card)),
  },
  releaseDate: {
    label: 'Release Date', numeric: true,
    key: (card) => parseReleaseDate(releaseDate(card)),
  },
};

export function compareCards(a, b, sortKey, direction) {
  const definition = SORT_KEYS[sortKey] || SORT_KEYS.name;
  const av = definition.key(a);
  const bv = definition.key(b);
  let primary;
  if (definition.numeric) {
    // Use comparison rather than subtraction so equal values (including two
    // +Infinity "missing" values, where Infinity - Infinity is NaN) tie to 0
    // and fall through to the sub-type / name tie-breakers.
    primary = av === bv ? 0 : av < bv ? -1 : 1;
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
