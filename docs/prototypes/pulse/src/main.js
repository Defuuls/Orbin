import { start } from './shell.js';

function safeLocalStorage() {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}

// `__pulse` is a test hook: it lets the browser tests inject faults and read state.
window.__pulse = start(document.getElementById('app'), { backend: safeLocalStorage() });
