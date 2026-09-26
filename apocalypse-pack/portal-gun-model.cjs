'use strict';

// Code-native Portal Gun model. The barrel points toward -Z and the grip toward -Y,
// matching JasperCraft's held-item transform contract for every firearm model.
const textures = {
  particle: 'blocks/quartz_block_side',
  shell: 'blocks/quartz_block_side',
  dark: 'blocks/coal_block',
  metal: 'blocks/iron_block',
  cyan: 'blocks/diamond_block',
  amber: 'blocks/gold_block',
  red: 'blocks/redstone_block',
};
const transform = (rotation, translation, scale) => ({rotation, translation, scale: [scale, scale, scale]});
const faces = ['north', 'south', 'east', 'west', 'up', 'down'];
function box(comment, from, to, texture) {
  return {__comment: comment, from, to, faces: Object.fromEntries(faces.map(face =>
    [face, {uv: [0, 0, 16, 16], texture: '#'+texture}]))};
}
function model() {
  const elements = [
    box('black central receiver', [6.25, 8.75, 2], [9.75, 12, 15], 'dark'),
    box('white upper shell', [5.1, 11.4, 1.5], [10.9, 14, 13.5], 'shell'),
    box('white left shell', [4.6, 9.4, 3], [6.3, 12.7, 13], 'shell'),
    box('white right shell', [9.7, 9.4, 3], [11.4, 12.7, 13], 'shell'),
    box('rear white housing', [5.7, 8.6, 12], [10.3, 13.4, 17], 'shell'),
    box('rear black cap', [6.4, 9.3, 16.8], [9.6, 12.7, 18], 'dark'),
    box('grip below receiver', [6.7, 2.5, 10.5], [9.3, 9, 14], 'dark'),
    box('grip heel', [6.2, 2, 10], [9.8, 3.2, 14.7], 'metal'),
    box('trigger', [7.45, 7, 7.8], [8.55, 9.3, 9.2], 'red'),
    box('muzzle core', [6.8, 9.4, -3], [9.2, 11.8, 4], 'dark'),
    box('muzzle bore facing negative Z', [7.25, 9.85, -3.05], [8.75, 11.35, -2.95], 'cyan'),
    box('upper aperture claw', [7.1, 11.8, -5], [8.9, 14.5, 1.5], 'shell'),
    box('lower aperture claw', [7.1, 6.8, -5], [8.9, 9.4, 1.5], 'shell'),
    box('left aperture claw', [4.2, 9.5, -5], [6.8, 11.7, 1.5], 'shell'),
    box('right aperture claw', [9.2, 9.5, -5], [11.8, 11.7, 1.5], 'shell'),
    box('cyan emitter rail', [4.45, 10, -5.4], [5.5, 11.2, 4.5], 'cyan'),
    box('amber emitter rail', [10.5, 10, -5.4], [11.55, 11.2, 4.5], 'amber'),
    box('cyan power chamber', [5.35, 12.7, 5], [7.15, 14.4, 10], 'cyan'),
    box('amber power chamber', [8.85, 12.7, 5], [10.65, 14.4, 10], 'amber'),
    box('front sight above bore', [7.55, 14, -1], [8.45, 15.4, 0.2], 'metal'),
  ];
  return {
    __comment: 'Portal Gun / stable band 1160 / white shell, black core, cyan and amber emitters',
    ambientocclusion: true,
    textures,
    display: {
      thirdperson_righthand: transform([90, 0, 0], [0, 1, 0], 0.6),
      thirdperson_lefthand: transform([90, 0, 0], [0, 1, 0], 0.6),
      firstperson_righthand: transform([6, 0, -3], [1, 0, -1], 0.6),
      firstperson_lefthand: transform([6, 0, 3], [1, 0, -1], 0.6),
      gui: transform([25, 140, -20], [0, 0, 0], 0.48),
      ground: transform([0, 0, 90], [0, 2, 0], 0.35),
      fixed: transform([0, 90, -35], [0, 0, 0], 0.45),
    },
    elements: require('./zfight.cjs').separate(elements),
  };
}
if (require.main === module) {
  const fs = require('node:fs');
  const path = require('node:path');
  const target = path.join(__dirname, 'assets', 'minecraft', 'models', 'item', 'apocalypse_portal_gun.json');
  fs.writeFileSync(target, JSON.stringify(model(), null, 2)+'\n');
  console.log(target);
}
module.exports = {model};
