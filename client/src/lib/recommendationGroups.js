// Collapses recommendations that produce the same features, so the dialog can
// show one entry with the interchangeable cards stacked instead of near-duplicates.

function resultsKey(rec) {
  const results = [...new Set(rec.results || [])].sort();
  return results.length ? results.join('|') : null;
}

// Combos are only interchangeable when they produce exactly the same features.
// For "partial", only combos missing a single card collapse; a combo missing
// several cards can't be swapped for one card, so it stays on its own.
function groupKey(rec, filter) {
  const key = resultsKey(rec);
  if (key === null) return null;
  if (filter === 'partial' && rec.newCards.length !== 1) return null;
  return key;
}

export function cardId(card) {
  return card.scryfallId || card.name;
}

export function groupRecommendations(items, filter) {
  const groups = [];
  const byKey = new Map();
  for (const rec of items) {
    const key = groupKey(rec, filter);
    const existing = key === null ? null : byKey.get(key);
    if (existing) {
      existing.members.push(rec);
      continue;
    }
    const group = { key: key ?? `solo-${rec.comboId}`, members: [rec] };
    groups.push(group);
    if (key !== null) byKey.set(key, group);
  }
  return groups.map((group) => ({ ...group, cards: uniqueCards(group.members) }));
}

function uniqueCards(members) {
  const seen = new Map();
  for (const rec of members) {
    for (const card of rec.newCards) {
      if (!seen.has(cardId(card))) seen.set(cardId(card), card);
    }
  }
  return [...seen.values()];
}
