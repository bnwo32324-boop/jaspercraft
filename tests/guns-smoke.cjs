'use strict';
// The isolated Paper lifecycle of apocalypse-smoke.cjs, running the focused guns probe (automatic reload, alternating Portal Gun).
// All files and worlds stay inside a fresh candidate/apocalypse-smoke-UUID fixture.
//   JASPR_APOCALYPSE_TEST_JAR=<JasprApocalypse.jar> node tests/guns-smoke.cjs --paper
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const filename = path.join(__dirname, 'apocalypse-smoke.cjs');
const source = fs.readFileSync(filename, 'utf8').replaceAll('ApocalypseProbe', 'GunsProbe');
const runner = new Module(filename, module);
runner.filename = filename;
runner.paths = Module._nodeModulePaths(__dirname);
runner._compile(source, filename);
