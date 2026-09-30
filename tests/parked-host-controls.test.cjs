'use strict';
// Owner, 2026-09-29: the "Go to Jaspr.chat" cat (top left) and the Discord button (bottom right) are hidden for now.
// Their markup, styles and handlers stay in place, idle; removing the hidden attribute brings each one back.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.join(__dirname, '..', 'site', 'index.html'), 'utf8');

test('cat link and Discord button are parked: present, hidden, and forced hidden over the playing styles', () => {
  assert.match(html, /<nav class="game-nav"[^>]* hidden data-parked="[^"]+">[\s\S]*?id="back-chat"/);
  assert.match(html, /<aside class="discord-promo"[^>]* hidden data-parked="[^"]+">[\s\S]*?discord\.gg/);
  assert.match(html, /<style>[^<]*\[data-parked\]\[hidden\]\{display:none!important\}<\/style>/);
});
