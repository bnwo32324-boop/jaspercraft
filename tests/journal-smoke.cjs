'use strict';
// The isolated Paper lifecycle of apocalypse-smoke.cjs, running JournalProbe with the REAL Apocalypse, RPG, Disasters, Invasions and
// Journal jars loaded together (the Journal plugin reaches the others only through reflection, so only a real server shows that the
// doors fit). One JVM on one processor, one low-priority process at a time; everything stays in candidate/apocalypse-smoke-UUID.
//   JOURNAL_JARS='{"JasprRPG":"<jar>","JasprDisasters":"<jar>","JasprInvasions":"<jar>","JasprJournal":"<jar>"}' \
//   JASPR_APOCALYPSE_TEST_JAR=<JasprApocalypse.jar> JAVA17_HOME=<jdk 17> node tests/journal-smoke.cjs --paper
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const filename = path.join(__dirname, 'apocalypse-smoke.cjs');
let source = fs.readFileSync(filename, 'utf8').replaceAll('ApocalypseProbe', 'JournalProbe');
function swap(from, to) {
  if (source.split(from).length !== 2) throw new Error('apocalypse-smoke.cjs changed: anchor not unique: ' + from.slice(0, 70));
  source = source.replace(from, () => to);
}
// The production sources are not recompiled here (the jars under test are the subject), and every JVM gets one processor.
swap(`    runTool(javaTool(settings, 'javac'), [...compileArgs, '-cp', [api, auth].join(path.delimiter),
      '-d', pluginClasses, ...javaSources(SOURCE)], fixture, buildLog);
`, '');
swap(`const compileArgs = ['--release', '8', '-encoding', 'UTF-8', '-proc:none'];`, `const compileArgs = ['-J-XX:ActiveProcessorCount=1', '--release', '8', '-encoding', 'UTF-8', '-proc:none'];`);
swap(`'-XX:ActiveProcessorCount=2'`, `'-XX:ActiveProcessorCount=1'`);
swap(`    fs.copyFileSync(CANDIDATE, stagedJar);
`, `    fs.copyFileSync(CANDIDATE, stagedJar);
    const extra = JSON.parse(process.env.JOURNAL_JARS || '{}');
    for (const name of ['JasprRPG', 'JasprDisasters', 'JasprInvasions', 'JasprGear', 'JasprJournal']) {
      if (!extra[name] || !fs.statSync(extra[name]).isFile()) throw new Error('JOURNAL_JARS needs a jar for ' + name);
      fs.copyFileSync(extra[name], path.join(server, 'plugins', name + '.jar'));
    }
`);
// The probe depends on every plugin it inspects, so it enables after them.
swap(`depend: [AuthMe, JasprApocalypse]`, `depend: [AuthMe, JasprApocalypse]\\nsoftdepend: [JasprJournal, JasprRPG, JasprDisasters, JasprInvasions, JasprGear]`);
const runner = new Module(filename, module);
runner.filename = filename;
runner.paths = Module._nodeModulePaths(__dirname);
runner._compile(source, filename);
