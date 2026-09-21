import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SignatureCard } from '../SignatureCard';

describe('SignatureCard', () => {
  it('renders the commander metadata next to the image', () => {
    const card = {
      scryfallId: 'abc', name: 'Atraxa, Praetors\' Voice',
      typeLine: 'Legendary Creature — Phyrexian Angel', manaValue: 4,
      power: '4', toughness: '4', releasedAt: '2016-11-04',
    };
    render(<SignatureCard card={card} />);
    expect(screen.getByText("Atraxa, Praetors' Voice")).toBeInTheDocument();
    expect(screen.getByText('Legendary Creature — Phyrexian Angel')).toBeInTheDocument();
    expect(screen.getAllByText('4')).toHaveLength(3);
    expect(screen.getByText('2016-11-04')).toBeInTheDocument();
  });

  it('shows a rotation control for double-faced cards that flips on click', () => {
    const card = {
      scryfallId: 'dfc-1', name: 'Growing Rites of Itlimoc',
      typeLine: 'Legendary Enchantment // Legendary Land', manaValue: 3,
      backImageUrl: 'https://cards.scryfall.io/normal/back.jpg',
    };
    render(<SignatureCard card={card} />);
    const flipButton = screen.getByRole('button', { name: /Show back face/i });
    expect(flipButton).toBeInTheDocument();
    // The image container toggles the flipped class on click.
    const container = flipButton.parentElement;
    expect(container.classList.contains('flipped')).toBe(false);
    fireEvent.click(flipButton);
    expect(container.classList.contains('flipped')).toBe(true);
    fireEvent.click(flipButton);
    expect(container.classList.contains('flipped')).toBe(false);
  });

  it('does not render a flip control for single-face cards', () => {
    const card = { scryfallId: 'normal', name: 'Sol Ring', typeLine: 'Artifact', manaValue: 1 };
    render(<SignatureCard card={card} />);
    expect(screen.queryByRole('button', { name: /Show back face/i })).toBeNull();
  });
});
