export type PromotionPiece = 'q' | 'r' | 'b' | 'n';

const SQUARE = /^[a-h][1-8]$/;

/**
 * Parses a UCI move string (e.g. e2e4, e7e8q) into squares and optional promotion.
 */
export function parseUciMove(
  uci: string
): { from: string; to: string; promotion?: PromotionPiece } | null {
  const s = uci.trim().toLowerCase();
  if (s.length < 4) return null;
  const from = s.slice(0, 2);
  const to = s.slice(2, 4);
  if (!SQUARE.test(from) || !SQUARE.test(to)) return null;

  let promotion: PromotionPiece | undefined;
  if (s.length >= 5) {
    const p = s[4];
    if (p === 'q' || p === 'r' || p === 'b' || p === 'n') promotion = p;
  }
  return { from, to, promotion };
}
