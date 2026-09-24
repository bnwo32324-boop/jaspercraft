'use strict';

// Reuse the isolated Paper lifecycle with a narrowly scoped recipe probe. The
// fixture uses a random OS-assigned port and never touches the live server/world.
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const filename = path.join(__dirname, 'apocalypse-smoke.cjs');
const source = fs.readFileSync(filename, 'utf8').replaceAll('ApocalypseProbe', 'RecipeProbe');
const runner = new Module(filename, module);
runner.filename = filename;
runner.paths = Module._nodeModulePaths(__dirname);
runner._compile(source, filename);
