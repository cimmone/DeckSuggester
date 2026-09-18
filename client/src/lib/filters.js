import { cardColors, cardTypes, formatManaValue } from './cardFormatting';

export const METRICS = {
  cardColors: { label: 'Card colors', empty: 'No color data' },
  deckColors: { label: 'Commander identity', empty: 'No deck identity data' },
  manaValues: { label: 'Mana values', empty: 'No mana value data' },
  cardTypes: { label: 'Card types', empty: 'No card type data' },
};

export const COLOR_PALETTE = {
  White: '#f1e4bd', Blue: '#58a6d8', Black: '#665f70', Red: '#df684d',
  Green: '#5c9b78', Colorless: '#a9b2b7',
};

export function metricValues(deck, metric) {
  const cards = (deck.cards || []).filter((card) => card.includedInDeck !== false);
  switch (metric) {
    case 'manaValues':
      return new Set(cards.map((card) => formatManaValue(card.manaValue)));
    case 'cardColors':
      return new Set(cards.flatMap(cardColors));
    case 'deckColors': {
      // The server already resolves this to the commander's identity when one
      // is designated, falling back to the deck's included cards otherwise.
      const identity = deck.colorIdentity || [];
      return new Set(identity.length ? identity : ['Colorless']);
    }
    case 'cardTypes':
      return new Set(cards.flatMap(cardTypes));
    default:
      return new Set();
  }
}

export function anyValueMatches(actual, selected, metric) {
  if ((metric === 'cardColors' || metric === 'deckColors')
      && selected !== 'Colorless' && actual.size === 1 && actual.has('Colorless')) return true;
  return actual.has(selected);
}

export function onlyValuesMatch(actual, selected, metric) {
  if (metric === 'cardColors' || metric === 'deckColors') {
    if (actual.size === 1 && actual.has('Colorless')) return selected.size > 0;
    const allowed = new Set(selected);
    if ([...selected].some((value) => value !== 'Colorless')) allowed.add('Colorless');
    const required = [...selected].filter((value) => value !== 'Colorless');
    return [...actual].every((value) => allowed.has(value))
      && required.every((value) => actual.has(value));
  }
  return actual.size === selected.size && [...actual].every((value) => selected.has(value));
}

export function deckMatchesFilters(deck, filters, only) {
  if (!only) {
    return filters.every((filter) => anyValueMatches(
      metricValues(deck, filter.metric), filter.value, filter.metric));
  }
  const grouped = new Map();
  filters.forEach((filter) => {
    if (!grouped.has(filter.metric)) grouped.set(filter.metric, new Set());
    grouped.get(filter.metric).add(filter.value);
  });
  return [...grouped].every(([metric, selected]) => (
    onlyValuesMatch(metricValues(deck, metric), selected, metric)
  ));
}
