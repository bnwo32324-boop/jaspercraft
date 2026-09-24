'use strict';
// Reuse the existing isolated Paper lifecycle, but load the equipment-specific probe.
// All files and worlds stay inside a fresh candidate/apocalypse-smoke-UUID fixture.
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const filename = path.join(__dirname, 'apocalypse-smoke.cjs');
const source = fs.readFileSync(filename, 'utf8').replaceAll('ApocalypseProbe', 'EquipmentProbe');
const runner = new Module(filename, module);
runner.filename = filename;
runner.paths = Module._nodeModulePaths(__dirname);
runner._compile(source, filename);
