// Composite identity for boards and threads. Two sources can use the same board
// name and the same thread number, so a bare thread number is never an identity.

const SEP = '/';

function part(name, value) {
  const s = String(value ?? '');
  if (s === '' || s.includes(SEP)) {
    throw new Error(`Invalid ${name}: ${JSON.stringify(value)}`);
  }
  return s;
}

export function boardKey(source, board) {
  return part('source', source) + SEP + part('board', board);
}

export function threadKey(source, board, thread) {
  const n = Number(thread);
  if (!Number.isInteger(n) || n <= 0) {
    throw new Error(`Invalid thread: ${JSON.stringify(thread)}`);
  }
  return boardKey(source, board) + SEP + n;
}

export function parseThreadKey(key) {
  const p = String(key).split(SEP);
  if (p.length !== 3) throw new Error(`Invalid thread key: ${JSON.stringify(key)}`);
  threadKey(p[0], p[1], p[2]); // validates
  return { source: p[0], board: p[1], thread: Number(p[2]) };
}

export function boardKeyOfThread(key) {
  const { source, board } = parseThreadKey(key);
  return boardKey(source, board);
}
