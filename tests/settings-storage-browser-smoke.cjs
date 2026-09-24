'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),http=require('node:http'),os=require('node:os'),path=require('node:path');
const {execFile}=require('node:child_process');
const root=path.resolve(__dirname,'..');
const browsers=[
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
];
const browser=browsers.find(fs.existsSync);

test('a real Chromium Storage write is intercepted and uploaded', {skip:!browser}, async()=>{
  const client=fs.readFileSync(path.join(root,'site/jaspr-client.js'));
  let port;
  const server=http.createServer((req,res)=>{
    res.setHeader('Cache-Control','no-store');
    if(req.url==='/jaspr-client.js'){
      res.setHeader('Content-Type','application/javascript; charset=utf-8');res.end(client);return;
    }
    if(req.url==='/frame'){
      res.setHeader('Content-Type','text/html; charset=utf-8');
      res.end(`<!doctype html><body id="game_frame"><script>
window.fetch=function(url,options){
  if(url==='/api/jaspercraft/settings')parent.postMessage(options.body,'*');
  return Promise.resolve({ok:true,status:200,json:function(){return Promise.resolve({ok:true,revision:9});}});
};
window.WebSocket=class { addEventListener(){} };
window.main=function(){localStorage.setItem('_eaglercraft_1122_tailscale_ui2.g','chromium-direct-save');};
</script><script src="/jaspr-client.js"></script><script>
try{JasperCraftClient.start({gameName:'jasper',serverAddress:'ws://127.0.0.1:${port}/jaspercraft/socket',join:true});}
catch(error){parent.postMessage('ERROR:'+error.message,'*');}
</script></body>`);return;
    }
    res.setHeader('Content-Type','text/html; charset=utf-8');
    res.end(`<!doctype html><output id="result">waiting</output><iframe src="/frame"></iframe><script>
addEventListener('message',function(event){
  try{var body=JSON.parse(event.data);if(body.changes)result.textContent=body.changes['_eaglercraft_1122_tailscale_ui2.g']||'missing';}
  catch(_){result.textContent=String(event.data);}
});
</script>`);
  });
  await new Promise((resolve,reject)=>server.listen(0,'127.0.0.1',error=>error?reject(error):resolve()));
  port=server.address().port;
  const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'jaspr-settings-browser-'));
  try{
    const output=await new Promise((resolve,reject)=>execFile(browser,[
      '--headless=new','--disable-gpu','--disable-background-networking','--no-first-run',
      '--user-data-dir='+temporary,'--virtual-time-budget=1500','--dump-dom','http://127.0.0.1:'+port+'/'
    ],{windowsHide:true,maxBuffer:2_000_000,timeout:20_000},(error,stdout)=>error?reject(error):resolve(stdout)));
    assert.match(output,/<output id="result">chromium-direct-save<\/output>/);
  } finally {
    await new Promise(resolve=>server.close(resolve));
    const temporaryRoot=path.resolve(os.tmpdir())+path.sep;
    assert.ok(path.resolve(temporary).startsWith(temporaryRoot));
    fs.rmSync(temporary,{recursive:true,force:true});
  }
});
