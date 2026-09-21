import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RecommendationsDialog } from '../RecommendationsDialog';

const deck = { id: 'deck-1', name: 'Test' };

function makeFetch(items) {
  return async (url) => ({
    ok: true,
    status: 200,
    json: async () => (url.includes('/recommendations') ? items : []),
  });
}

describe('RecommendationsDialog', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders the combo explanation once per combo, not once per card', async () => {
    const items = [{
      comboId: 'c1',
      description: 'Infinite mana',
      fitness: 1,
      ownedPieces: 1,
      requiredPieces: 2,
      newCards: [
        { scryfallId: 'a', name: 'Card A', imageUrl: null, typeLine: 'Artifact', manaValue: 1, note: 'Artifact' },
        { scryfallId: 'b', name: 'Card B', imageUrl: null, typeLine: 'Creature', manaValue: 2, note: 'Creature' },
      ],
      explanation: 'You already own 1 of 2 pieces.',
    }];
    render(<RecommendationsDialog deck={deck} securedFetch={makeFetch(items)} onClose={() => {}} />);
    await waitFor(() => expect(screen.getByText('Infinite mana')).toBeInTheDocument());
    // The explanation is rendered exactly once for the combo.
    expect(screen.getAllByText('You already own 1 of 2 pieces.')).toHaveLength(1);
  });

  it('adds the suggested card to the deck when the image button is clicked', async () => {
    const items = [{
      comboId: 'c1',
      description: 'Combo',
      fitness: 1,
      ownedPieces: 0,
      requiredPieces: 2,
      newCards: [{ scryfallId: 'a', name: 'Sol Ring', imageUrl: null, typeLine: 'Artifact', manaValue: 1, note: '' }],
      explanation: 'explanation',
    }];
    const onAddCard = vi.fn().mockResolvedValue(undefined);
    render(<RecommendationsDialog deck={deck} securedFetch={makeFetch(items)} onClose={() => {}} onAddCard={onAddCard} />);
    await waitFor(() => expect(screen.getByText('Sol Ring')).toBeInTheDocument());
    fireEvent.click(screen.getByTitle('Add Sol Ring to the deck'));
    await waitFor(() => expect(onAddCard).toHaveBeenCalledWith('Sol Ring'));
  });

  it('switches the filter mode via the radio buttons', async () => {
    let lastUrl = null;
    const fetcher = async (url) => { lastUrl = url; return { ok: true, status: 200, json: async () => [] }; };
    render(<RecommendationsDialog deck={deck} securedFetch={fetcher} onClose={() => {}} />);
    await waitFor(() => expect(lastUrl).toContain('filter=legal'));
    fireEvent.click(screen.getByLabelText('Partially in deck'));
    await waitFor(() => expect(lastUrl).toContain('filter=partial'));
  });
});
