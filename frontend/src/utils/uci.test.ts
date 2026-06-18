import { describe, it, expect } from 'vitest';
import { parseUciMove } from './uci';

describe('parseUciMove', () => {
  it('parses simple moves', () => {
    expect(parseUciMove('e2e4')).toEqual({ from: 'e2', to: 'e4' });
    expect(parseUciMove('  E2E4  ')).toEqual({ from: 'e2', to: 'e4' });
  });

  it('parses promotion', () => {
    expect(parseUciMove('e7e8q')).toEqual({ from: 'e7', to: 'e8', promotion: 'q' });
  });

  it('returns null for garbage', () => {
    expect(parseUciMove('')).toBeNull();
    expect(parseUciMove('abc')).toBeNull();
  });
});
