// Isolated Mo' Bends browser fixture only. Never shipped with the game.
// Same idea as the BetterCombat rig's no-desktop-pointer.js: the client's own pointer-lock event path runs,
// but the OS pointer lock is never requested from an unattended test tab.
(() => {
  let locked = null;
  Object.defineProperty(Document.prototype, 'pointerLockElement', { configurable: true, get() { return locked; } });
  Element.prototype.requestPointerLock = function () {
    locked = this;
    queueMicrotask(() => document.dispatchEvent(new Event('pointerlockchange')));
    return Promise.resolve();
  };
  Document.prototype.exitPointerLock = function () {
    locked = null;
    queueMicrotask(() => document.dispatchEvent(new Event('pointerlockchange')));
  };
})();
