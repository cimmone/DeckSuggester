import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// vitest runs with globals disabled, so testing-library's automatic
// afterEach cleanup is never registered. Wire it up explicitly so the DOM
// is reset between every test.
afterEach(() => {
  cleanup();
});
