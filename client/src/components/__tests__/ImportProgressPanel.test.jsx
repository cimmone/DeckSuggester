import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ImportProgressPanel, importStatusText } from '../ImportProgressPanel';

const running = { id: 'a', label: 'My folder', status: 'RUNNING', phase: 'fetching', total: 57, completed: 14 };

describe('ImportProgressPanel', () => {
  it('shows a progress bar and running count for each concurrent import', () => {
    const second = { ...running, id: 'b', label: 'Other folder', total: 3, completed: 1 };
    render(<ImportProgressPanel imports={[running, second]} onDismiss={() => {}} />);
    expect(screen.getAllByRole('progressbar')).toHaveLength(2);
    expect(screen.getByText('14/57 Decks Imported')).toBeInTheDocument();
    expect(screen.getByText('1/3 Decks Imported')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: /My folder/ })).toHaveAttribute('aria-valuenow', '25');
  });

  it('describes discovery, saving and failure states', () => {
    expect(importStatusText({ status: 'RUNNING', phase: 'discovering' })).toBe('Finding decks…');
    expect(importStatusText({ ...running, phase: 'saving' })).toBe('Saving 57 decks…');
    expect(importStatusText({ status: 'FAILED', error: 'Boom' })).toBe('Boom');
  });
});
