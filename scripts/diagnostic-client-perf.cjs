'use strict';

// Disposable read-only client benchmark. It serves a selected existing bundle
// from memory to headless Chromium and reports frame gaps plus adapter status.
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { execFile } = require('node:child_process');

const root = path.resolve(__dirname, '..');
function removeProbeProfile(profile) {
  for (let attempt = 0; attempt < 8; attempt++) {
    try {
      fs.rmSync(profile, { recursive: true, force: true });
      return;
    } catch (error) {
      if (error.code !== 'EPERM' && error.code !== 'EBUSY') throw error;
    }
  }
  // Chromium can leave a transient lock behind on Windows. The profile is
  // disposable, so preserve the measurement even if cleanup is deferred.
  process.stderr.write('probe profile cleanup deferred: ' + profile + '\n');
}
const browser = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(fs.existsSync);
if (!browser) throw new Error('No Chromium browser found');

const bundle = process.argv[2] || 'site/classes.js';
const classes = fs.readFileSync(path.resolve(root, bundle));
const assets = fs.readFileSync(path.join(root, 'site/assets.epk'));
const page = `<!doctype html><meta charset="utf-8"><style>html,body,#game_frame{margin:0;width:100%;height:100%;overflow:hidden;background:#000}#report{position:fixed;z-index:9;top:0;left:0;color:#fff;background:#000c;font:12px monospace;white-space:pre}</style><div id="game_frame"></div><output id="report">starting</output><script>
window.__perf={started:performance.now(),last:performance.now(),frames:0,maxGap:0,gaps50:0,gaps100:0,gaps250:0,gaps1000:0};
function sample(now){var p=window.__perf,g=now-p.last;p.frames++;if(g>p.maxGap)p.maxGap=g;if(g>50)p.gaps50++;if(g>100)p.gaps100++;if(g>250)p.gaps250++;if(g>1000)p.gaps1000++;p.last=now;requestAnimationFrame(sample)}requestAnimationFrame(sample);
function sendKey(type,code,key){var ev=new KeyboardEvent(type,{bubbles:true,cancelable:true,code:code,key:key,keyCode:code==='KeyW'?87:32,which:code==='KeyW'?87:32});window.dispatchEvent(ev);document.dispatchEvent(ev);}
function report(){var p=window.__perf,p2={elapsedMs:performance.now()-p.started,frames:p.frames,maxGapMs:p.maxGap,gapsOver50:p.gaps50,gapsOver100:p.gaps100,gapsOver250:p.gaps250,gapsOver1000:p.gaps1000,canvas:document.querySelectorAll('canvas').length};for(var k of ['JasprShadersDiagnostics','JasprDynamicLightsDiagnostics','JasprWaypointDiagnostics','JasprGoreDiagnostics'])try{p2[k]=window[k]?window[k].status():null}catch(e){p2[k]='error:'+e.message}document.querySelector('#report').textContent=JSON.stringify(p2)}
window.addEventListener('load',function(){setTimeout(function(){try{window.eaglercraftXOpts={demoMode:false,container:'game_frame',assetsURI:'/assets.epk',localStorageNamespace:'_eaglercraft_1122_tailscale_perf_probe',worldsDB:'perf_probe_worlds',resourcePacksDB:'perf_probe_packs',servers:[{addr:'ws://127.0.0.1:25565/',name:'perf probe'}],relays:[],joinServer:'ws://127.0.0.1:25565/'};main()}catch(e){document.querySelector('#report').textContent='main error: '+e.stack}},0);setTimeout(function(){sendKey('keydown','KeyW','w')},2200);setTimeout(function(){sendKey('keyup','KeyW','w')},7200);setTimeout(report,10500)});
</script><script src="/classes.js"></script>`;

const server = http.createServer((req, res) => {
  const key = (req.url || '').split('?')[0];
  if (key === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(page); return; }
  if (key === '/classes.js') { res.setHeader('Content-Type', 'application/javascript; charset=utf-8'); res.setHeader('Cache-Control', 'no-store'); res.end(classes); return; }
  if (key === '/assets.epk') { res.setHeader('Content-Type', 'application/octet-stream'); res.setHeader('Cache-Control', 'no-store'); res.end(assets); return; }
  res.writeHead(404); res.end();
});

server.listen(0, '127.0.0.1', () => {
  const port = server.address().port;
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-perf-probe-'));
  const args = [
    '--headless=new', '--enable-unsafe-swiftshader', '--use-angle=swiftshader',
    '--disable-background-networking', '--no-first-run', '--no-default-browser-check',
    '--user-data-dir=' + profile, '--virtual-time-budget=12000', '--dump-dom',
    'http://127.0.0.1:' + port + '/'
  ];
  execFile(browser, args, { windowsHide: true, maxBuffer: 2_000_000, timeout: 30_000 }, (error, stdout, stderr) => {
    server.close();
    removeProbeProfile(profile);
    if (error) { process.stderr.write(stderr || error.stack || String(error)); process.exitCode = 1; return; }
    const match = stdout.match(/<output id="report">([\s\S]*?)<\/output>/);
    process.stdout.write((match ? match[1] : stdout.slice(-4000)) + '\n');
  });
});
