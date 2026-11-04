import { describe, it, expect } from 'vitest';
import { groupRecommendations } from '../recommendationGroups';

const card = (id) => ({ scryfallId: id, name: id });
const rec = (comboId, results, newCards) => ({ comboId, results, newCards });

describe('groupRecommendations', () => {
  it('collapses combos with identical features and stacks unique cards', () => {
    const items = [
      rec('1', ['Infinite mana', 'Infinite draw'], [card('a')]),
      rec('2', ['Infinite draw', 'Infinite mana'], [card('b')]),
      rec('3', ['Infinite mana'], [card('c')]),
    ];
    const groups = groupRecommendations(items, 'legal');
    expect(groups).toHaveLength(2);
    expect(groups[0].members.map((m) => m.comboId)).toEqual(['1', '2']);
    expect(groups[0].cards.map((c) => c.scryfallId)).toEqual(['a', 'b']);
  });

  it('only collapses single-missing-card combos in partial mode', () => {
    const items = [
      rec('1', ['X'], [card('a'), card('b')]),
      rec('2', ['X'], [card('c'), card('d')]),
      rec('3', ['X'], [card('e')]),
      rec('4', ['X'], [card('f')]),
    ];
    const groups = groupRecommendations(items, 'partial');
    expect(groups.map((g) => g.members.map((m) => m.comboId))).toEqual([['1'], ['2'], ['3', '4']]);
  });

  it('collapses fully owned duplicates and never groups combos without features', () => {
    const owned = groupRecommendations([rec('1', ['X'], []), rec('2', ['X'], [])], 'owned');
    expect(owned).toHaveLength(1);
    const blank = groupRecommendations([rec('1', [], []), rec('2', [], [])], 'owned');
    expect(blank).toHaveLength(2);
  });
});
