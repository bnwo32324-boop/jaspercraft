'use strict';

// Code-native Portal Gun model. The barrel points toward -Z and the grip toward -Y,
// matching JasperCraft's held-item transform contract for every firearm model.
// The model and its own painted texture are generated together by portal-gun-art.cjs
// (2026-10-05: the old model stretched six vanilla block textures over plain boxes).
const art = require('./portal-gun-art.cjs');

function model() { return art.model(); }

if (require.main === module) {
  require('child_process').execFileSync(process.execPath, [require('node:path').join(__dirname, 'portal-gun-art.cjs')], {stdio: 'inherit'});
}
module.exports = {model, texture: art.textureImage, TEXTURE: art.TEXTURE};
