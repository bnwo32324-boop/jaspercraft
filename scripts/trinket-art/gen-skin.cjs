'use strict';
// Writes Skin.java into every plugin that skins trinkets from Skin.java.template (the copies must stay identical).
const fs = require('node:fs'), path = require('node:path');
const root = path.resolve(__dirname, '..', '..');
const TARGETS = {
  'server/custom-plugins/JasprDungeon/src/chat/jaspr/dungeon': 'chat.jaspr.dungeon',
  'server/custom-plugins/JasprRuins/src/chat/jaspr/ruins': 'chat.jaspr.ruins',
  'server/custom-plugins/JasprNether/src/chat/jaspr/nether': 'chat.jaspr.nether',
  'server/custom-plugins/JasprBackrooms/src/chat/jaspr/backrooms': 'chat.jaspr.backrooms'
};
const template = fs.readFileSync(path.join(__dirname, 'Skin.java.template'), 'utf8');
function render(pkg) { return template.replace('@PACKAGE@', pkg); }
if (require.main === module) {
  for (const [dir, pkg] of Object.entries(TARGETS)) { fs.writeFileSync(path.join(root, dir, 'Skin.java'), render(pkg)); console.log('wrote ' + dir + '/Skin.java'); }
}
module.exports = {TARGETS, render};
