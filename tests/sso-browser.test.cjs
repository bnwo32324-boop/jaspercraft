const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const siteRoot = process.env.JASPR_TEST_SITE ? path.resolve(process.env.JASPR_TEST_SITE) : path.join(root,'site');
const profile = require(path.join(siteRoot,'jaspr-profile.js'));
const source = name => fs.readFileSync(path.join(siteRoot, name), 'utf8');
const utf = value => { const b = Buffer.from(value); const n = Buffer.alloc(2); n.writeUInt16BE(b.length); return Buffer.concat([n,b]); };
const tag = (type, name, payload) => Buffer.concat([Buffer.from([type]), utf(name), payload]);
const str = (name, value) => tag(8, name, utf(value));
function fakeStorage(seed = {}) {
  const values = new Map(Object.entries(seed));
  return {
    get length() { return values.size; },
    key: index => [...values.keys()][index] ?? null,
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(String(key), String(value)),
    removeItem: key => values.delete(String(key)),
    values,
  };
}

test('profile replaces only root username, retaining custom skins/capes and nested fields byte for byte', () => {
  const opaque = tag(7, 'pixels', Buffer.concat([Buffer.from([0,0,0,8]), Buffer.from([0,255,17,32,0,12,7,99])]));
  const nested = tag(10, 'custom', Buffer.concat([str('username','not-the-account'), opaque, Buffer.from([0])]));
  const before = Buffer.concat([Buffer.from([10,0,0]), opaque, str('username','OldName'), nested, Buffer.from([0])]);
  const after = Buffer.concat([Buffer.from([10,0,0]), opaque, str('username','jasper'), nested, Buffer.from([0])]);
  assert.deepEqual(Buffer.from(profile.withUsername(before, 'jasper')), after);
  assert.deepEqual(Buffer.from(profile.withUsername(after, 'jasper')), after);
});

test('compressed profile preparation preserves settings keys; corrupt data is never overwritten', async () => {
  const values = new Map([[profile.key,zlib.gzipSync(profile.newProfile()).toString('base64')], [profile.namespace+'.g','unchanged-game-settings']]);
  const storage = {getItem:k=>values.get(k)||null, setItem:(k,v)=>values.set(k,v)};
  await profile.prepare('JasprFriend_123', storage);
  assert.equal(values.get(profile.namespace+'.g'),'unchanged-game-settings');
  assert.ok(zlib.gunzipSync(Buffer.from(values.get(profile.key),'base64')).includes(Buffer.from('JasprFriend_123')));
  values.set(profile.key,'broken-profile');
  await assert.rejects(profile.prepare('jasper',storage));
  assert.equal(values.get(profile.key),'broken-profile');
  assert.throws(()=>profile.withUsername(profile.newProfile(),'../jasper'));
  assert.throws(()=>profile.withUsername(Buffer.from([10,0,0,8,0,8]),'jasper'));
  const duplicate=Buffer.concat([Buffer.from([10,0,0]),str('username','a'),str('username','b'),Buffer.from([0])]);
  assert.throws(()=>profile.withUsername(duplicate,'jasper'));
});

test('account snapshots include every game namespace setting and isolate switched accounts', () => {
  const game = profile.namespace + '.g';
  const hotbar = profile.namespace + '.hotbar';
  const stale = profile.namespace + '.stale';
  const local = fakeStorage({ [game]:'local-options', [hotbar]:'local-hotbar', unrelated:'keep-me' });
  assert.deepEqual(profile.initialSettings('jasper', local), { [game]:'local-options', [hotbar]:'local-hotbar' });
  local.setItem(profile.ownerKey, 'another_player');
  assert.deepEqual(profile.initialSettings('jasper', local), {});
  local.setItem(stale, 'remove-me');
  profile.applySettings('jasper', local, { [game]:'account-options', [hotbar]:'account-hotbar' });
  assert.equal(local.getItem(game), 'account-options');
  assert.equal(local.getItem(hotbar), 'account-hotbar');
  assert.equal(local.getItem(stale), null);
  assert.equal(local.getItem('unrelated'), 'keep-me');
  assert.equal(local.getItem(profile.ownerKey), 'jasper');
  assert.throws(() => profile.applySettings('jasper', local, { 'jaspr.session':'never' }), /invalid game settings/);
  assert.equal(local.getItem(game), 'account-options', 'invalid snapshots are rejected before changing local storage');
});

test('stable sockets stay native and both callback and direct game writes upload account settings', async () => {
  class NativeSocket { constructor(url) { this.url=url; } }
  const requests=[];
  const localStorage=fakeStorage({[profile.namespace+'.g']:'stored-options'});
  const ctx = {
    URL, Proxy, Reflect, Math, setTimeout, clearTimeout, Promise, WebSocket:NativeSocket, parent:{},
    location:new URL('https://jaspr.chat/jaspercraft/client.html'), localStorage,
    addEventListener:()=>{},
    fetch:async(url,options)=>{
      requests.push({url,options});
      return {ok:true,status:200,json:async()=>({ok:true,revision:2})};
    },
    main:()=>{}
  };
  ctx.window=ctx; vm.runInNewContext(source('jaspr-client.js'),ctx);
  ctx.JasperCraftClient.start({gameName:'jasper',serverAddress:'wss://jaspr.chat/jaspercraft/socket',join:true});
  const socket = new ctx.WebSocket('wss://jaspr.chat/jaspercraft/socket');
  assert.ok(socket instanceof NativeSocket);
  assert.equal(socket.url,'wss://jaspr.chat/jaspercraft/socket');
  assert.equal(ctx.eaglercraftXOpts.joinServer,'wss://jaspr.chat/jaspercraft/socket');
  assert.equal(ctx.eaglercraftXOpts.localStorageNamespace,profile.namespace);
  assert.equal(ctx.eaglercraftXOpts.worldsDB,'eaglercraft_1122_tailscale_worlds');
  assert.equal(ctx.eaglercraftXOpts.resourcePacksDB,'eaglercraft_1122_tailscale_resourcepacks');
  assert.equal(ctx.eaglercraftXOpts.localStorageLoaded(profile.namespace+'.g'),'stored-options');
  ctx.eaglercraftXOpts.localStorageSaved('g','quiet-and-no-auto-jump');
  await new Promise(resolve=>setTimeout(resolve,450));
  const settingsCall=requests.find(call=>call.url==='/api/jaspercraft/settings');
  assert.ok(settingsCall);
  assert.equal(settingsCall.options.credentials,'same-origin');
  assert.deepEqual(JSON.parse(settingsCall.options.body),{
    gameName:'jasper',
    changes:{[profile.namespace+'.g']:'quiet-and-no-auto-jump'}
  });
  requests.length=0;
  localStorage.setItem(profile.namespace+'.g','direct-compiled-game-save');
  await new Promise(resolve=>setTimeout(resolve,450));
  const directSettingsCall=requests.find(call=>call.url==='/api/jaspercraft/settings');
  assert.ok(directSettingsCall,'compiled localStorage writes must not depend on the optional TeaVM callback');
  assert.deepEqual(JSON.parse(directSettingsCall.options.body),{
    gameName:'jasper',
    changes:{[profile.namespace+'.g']:'direct-compiled-game-save'}
  });
  assert.equal(new ctx.WebSocket('wss://relay.deev.is/').url,'wss://relay.deev.is/');
});

function browser(responder, options={}) {
  const elements={}, calls=[], starts=[], listeners={}, assigned=[], historyReplaced=[];
  class Element {
    constructor(id) {
      this.id=id;this.events={};this.dataset={};this.value='';this.hidden=false;this.children=[];this.attrs={};this.disabled=false;this.required=false;
      this.isConnected=true;this.focusCalls=0;
      this.classes=new Set();this.classList={toggle:(name,on)=>on?this.classes.add(name):this.classes.delete(name),add:(...names)=>names.forEach(name=>this.classes.add(name)),remove:(...names)=>names.forEach(name=>this.classes.delete(name))};
      this._textContent='';
    }
    get textContent(){return this._textContent;}
    set textContent(value){this._textContent=String(value);if(this.id==='game-host'&&value==='')this.children=[];}
    addEventListener(name,fn) { this.events[name]=fn; }
    setAttribute(name,value) { this.attrs[name]=value; }
    focus() { this.focusCalls++; }
    querySelector() { return this.children[0]||null; }
    appendChild(child) { child.owner=this; this.children.push(child); setImmediate(()=>child.events.load?.()); }
    remove() { if(this.owner)this.owner.children=this.owner.children.filter(x=>x!==this); }
    click(event={}) { this.events.click?.({button:0, ...event}); }
  }
  for (const match of source('index.html').matchAll(/id="([^"]+)"/g)) elements[match[1]]=new Element(match[1]);
  const location=new URL(options.href||'https://jaspr.chat/jaspercraft/');location.assign=value=>assigned.push(value);
  const bodyClasses=new Set();
  const localStorage=fakeStorage(options.storage||{});
  const ctx={URL,AbortController,setTimeout,clearTimeout,console,location,localStorage,
    history:{replaceState:(_state,_title,value)=>historyReplaced.push(value)},
    JasprProfile:{
      key:profile.key,
      initialSettings:(name,storage)=>profile.initialSettings(name,storage),
      applySettings:(name,storage,values)=>profile.applySettings(name,storage,values),
      prepare:async(name)=>{ctx.prepared=name;localStorage.setItem(profile.key,'prepared-'+name);}
    },
    document:{hidden:false,addEventListener:(name,fn)=>listeners['document:'+name]=fn,getElementById:id=>elements[id],body:{classList:{add:(...names)=>names.forEach(name=>bodyClasses.add(name)),remove:(...names)=>names.forEach(name=>bodyClasses.delete(name))}},createElement:()=>{
      const frame=new Element('frame');frame.contentWindow={focus:()=>{frame.contentFocusCalls=(frame.contentFocusCalls||0)+1;},JasperCraftClient:{start:s=>starts.push(s)}};return frame;
    }},
    fetch:async(url,options)=>{
      calls.push({url,options});
      const result=await responder(url,options);
      let status=result[0],payload=result[1];
      if(url==='/api/jaspercraft/settings/sync'&&status>=200&&status<300&&(!payload||!payload.values))
        payload={ok:true,initialized:true,revision:1,values:{}};
      if(url==='/api/jaspercraft/settings'&&status>=200&&status<300&&(!payload||!payload.revision))
        payload={ok:true,revision:2,updated:1};
      return {ok:status>=200&&status<300,status,json:async()=>payload};
    },
    addEventListener:(name,fn)=>listeners[name]=fn};
  ctx.window=ctx;vm.runInNewContext(source('jaspr-sso.js'),ctx);
  return {ctx,elements,calls,starts,listeners,assigned,historyReplaced,bodyClasses,flush:async()=>{for(let i=0;i<16;i++)await new Promise(setImmediate);}};
}
const me={authenticated:true,user:{username:'Owner',displayName:'The owner',kind:'account'},profileSetupRequired:false,gameName:'jasper',chatUrl:'/chat'};

test('signed-in bootstrap enters the world automatically and the cat link unloads without logout',async()=>{
  const b=browser(async url=>url.endsWith('/auth')?[200,{authProviders:{google:true,discord:true}}]
    :url.endsWith('/me')?[200,me]:[200,{gameName:'jasper',serverAddress:'wss://jaspr.chat/jaspercraft/socket'}]);
  await b.flush();
  assert.equal(b.starts.length,1);assert.equal(b.ctx.prepared,'jasper');assert.equal(b.starts[0].join,true);
  assert.equal(b.elements.lobby.hidden,true);assert.equal(b.elements['game-host'].hidden,false);
  for(const {url,options} of b.calls){assert.equal(options.credentials,'same-origin');if(url.endsWith('/connect'))assert.equal(options.headers['X-Jaspergers-Client'],'web-v1');}
  assert.equal(b.calls.filter(c=>c.url==='/api/jaspercraft/connect').length,1);
  assert.equal(b.calls.filter(c=>c.url==='/api/jaspercraft/settings/sync').length,1);
  assert.equal(b.calls.filter(c=>c.url==='/api/jaspercraft/settings').length,1);
  assert.ok(b.calls.findIndex(c=>c.url==='/api/jaspercraft/settings/sync')<b.calls.findIndex(c=>c.url==='/api/jaspercraft/connect'));
  assert.equal(JSON.parse(b.calls.find(c=>c.url==='/api/jaspercraft/settings/sync').options.body).gameName,'jasper');
  assert.ok(b.elements['game-host'].children[0].focusCalls>0);
  assert.ok(b.elements['game-host'].children[0].contentFocusCalls>0);
  assert.equal(b.elements['back-chat'].href,'https://jaspr.chat/chat');
  b.elements['back-chat'].click();assert.equal(b.elements['game-host'].children.length,0);
  assert.ok(!b.calls.some(c=>c.url.includes('/auth/')));
});

test('signed-out fallback fits one viewport, reuses Jaspr provider art, and has no second play page',()=>{
  const html=source('index.html'),css=source('jaspr-sso.css');
  assert.match(html,/class="game-nav"[\s\S]*?id="back-chat"[\s\S]*?jaspercraft-cat-face\.png/);
  assert.match(html,/aria-label="Go to Jaspr\.chat"/);
  assert.match(html,/id="lobby" hidden/);
  assert.match(html,/id="google-signin"[\s\S]*?\/auth\/google-g\.png/);
  assert.match(html,/id="discord-signin"[\s\S]*?\/auth\/discord-clyde\.svg/);
  assert.match(html,/Continue with Google/);
  assert.match(html,/Continue with Discord/);
  assert.match(html,/create your account automatically/i);
  assert.match(html,/username — no email/i);
  assert.match(html,/id="lobby-banner"[^>]*data-src=/);
  assert.doesNotMatch(html,/id="lobby-banner"[^>]*\ssrc=/);
  assert.match(html,/id="guest"[\s\S]*?Play as guest/);
  assert.doesNotMatch(html,/Play JasperCraft|Choose skin|id="play"|id="edit-character"/);
  assert.match(css,/\.game-nav\{position:fixed/);
  assert.match(css,/html,body\{width:100%;height:100%;overflow:hidden\}/);
  assert.match(css,/#game-host\{position:fixed;inset:0;height:100dvh/);
  assert.match(css,/@media\(max-width:760px\)[\s\S]*overflow-y:auto/);
  assert.match(css,/@media\(max-width:500px\)[\s\S]*\.oauth-buttons\{grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
  assert.doesNotMatch(css,/calc\(100dvh\s*-\s*64px\)/);
  const icon=fs.readFileSync(path.join(siteRoot,'jaspercraft-cat-face.png'));
  assert.deepEqual([...icon.subarray(0,8)],[137,80,78,71,13,10,26,10]);
  assert.equal(icon.readUInt32BE(16),256);assert.equal(icon.readUInt32BE(20),256);
  assert.ok(icon.length<100_000);
});

test('JasperCraft launcher requests the current client revision for the shader picker',()=>{
  const html=source('index.html'),launcher=source('jaspr-sso.js');
  assert.match(html,/jaspr-sso\.js\?build=20260915-shader-picker-dedup1/);
  assert.match(launcher,/frame\.src = "client\.html\?build=20260915-shader-picker-dedup1";/);
});

test('registration uses existing auth endpoint, same-origin cookies and client header; passwords are cleared',async()=>{
  let authenticated=false;
  const b=browser(async(url,opts)=>{
    if(url==='/api/jaspercraft/auth')return [200,{authProviders:{google:true,discord:true}}];
    if(url==='/api/auth/register'){assert.equal(opts.headers['X-Jaspergers-Client'],'web-v1');assert.equal(JSON.parse(opts.body).displayName,'Friend');authenticated=true;return [200,{ok:true}];}
    if(url==='/api/jaspercraft/me')return authenticated?[200,me]:[401,{error:{code:'IDENTITY_REQUIRED'}}];
    return [200,{gameName:'jasper',serverAddress:'wss://jaspr.chat/jaspercraft/socket'}];
  });
  await b.flush();assert.equal(b.elements['auth-panel'].hidden,false);
  b.elements['register-tab'].click();b.elements.username.value='friend';b.elements['display-name'].value='Friend';
  b.elements.password.value=b.elements.confirm.value='test-only-password';
  b.elements.username.events.input();
  const savedDraft=JSON.parse(b.ctx.localStorage.getItem('jaspercraft.auth.draft.v1'));
  assert.deepEqual(JSON.parse(JSON.stringify(savedDraft)),{mode:'register',username:'friend',displayName:'Friend'});
  assert.equal(Object.hasOwn(savedDraft,'password'),false);
  b.elements['auth-form'].events.submit({preventDefault(){}});await b.flush();
  assert.equal(b.elements.password.value,'');assert.equal(b.elements.confirm.value,'');
  assert.equal(b.ctx.localStorage.getItem('jaspercraft.auth.draft.v1'),null);
  assert.equal(b.starts.length,1);assert.equal(b.elements.lobby.hidden,true);assert.equal(b.calls.filter(c=>c.url==='/api/auth/register').length,1);
});

test('permanent Jaspr guest identity launches immediately and external providers return to JasperCraft',async()=>{
  let guest=false;
  const b=browser(async url=>{
    if(url==='/api/jaspercraft/auth')return [200,{authProviders:{google:true,discord:true}}];
    if(url==='/api/auth/guest'){guest=true;return [201,{ok:true}];}
    if(url==='/api/jaspercraft/me')return guest?[200,{...me,user:{username:'anon_0501',displayName:'anon_0501',kind:'guest'},gameName:'anon_0501'}]:[401,{error:{code:'IDENTITY_REQUIRED'}}];
    return [200,{gameName:'anon_0501',serverAddress:'wss://jaspr.chat/jaspercraft/socket'}];
  });
  await b.flush();b.elements.guest.click();await b.flush();
  assert.equal(b.starts.length,1);assert.equal(b.starts[0].gameName,'anon_0501');
  assert.equal(b.calls.filter(c=>c.url==='/api/auth/guest').length,1);
  const oauth=browser(async url=>url.endsWith('/auth')?[200,{authProviders:{google:true,discord:true}}]:[401,{error:{code:'IDENTITY_REQUIRED'}}]);
  await oauth.flush();oauth.elements['google-signin'].click();
  assert.equal(oauth.assigned[0],'/api/auth/oauth/google/start?return_to=%2Fjaspercraft%2F');
  const pendingOAuth=JSON.parse(oauth.ctx.localStorage.getItem('jaspercraft.oauth.pending.v1'));
  assert.equal(pendingOAuth.provider,'google');
  assert.equal(pendingOAuth.returnTo,'/jaspercraft/');
});

test('mobile OAuth failures return to JasperCraft with the form draft intact and actionable provider feedback',async()=>{
  const now=Date.now();
  const b=browser(async url=>url.endsWith('/auth')?[200,{authProviders:{google:true,discord:true}}]:[401,{error:{code:'IDENTITY_REQUIRED'}}],{
    href:'https://jaspr.chat/jaspercraft/?auth_error=signin_expired',
    storage:{
      'jaspercraft.oauth.pending.v1':JSON.stringify({provider:'discord',startedAt:now,returnTo:'/jaspercraft/'}),
      'jaspercraft.auth.draft.v1':JSON.stringify({mode:'register',username:'mobile_friend',displayName:'Mobile Friend'})
    }
  });
  await b.flush();
  assert.match(b.elements.status.textContent,/Discord sign-in expired/i);
  assert.equal(b.elements.username.value,'mobile_friend');
  assert.equal(b.elements['display-name'].value,'Mobile Friend');
  assert.equal(b.elements['register-tab'].attrs['aria-selected'],'true');
  assert.equal(b.ctx.localStorage.getItem('jaspercraft.oauth.pending.v1'),null);
  assert.equal(b.historyReplaced[0],'/jaspercraft/');
});

test('first-time Google identity completes its shared Jaspr username before auto-entry',async()=>{
  let setup=true;
  const pending={...me,profileSetupRequired:true,gameName:'jc_pending'};
  const b=browser(async(url,opts)=>{
    if(url.endsWith('/auth'))return [200,{authProviders:{google:true,discord:true}}];
    if(url==='/api/users/me/google-alias'){assert.equal(opts.method,'PATCH');setup=false;return [200,{ok:true}];}
    if(url.endsWith('/me'))return [200,setup?pending:{...me,gameName:'cipher_nomad'}];
    return [200,{gameName:'cipher_nomad',serverAddress:'wss://jaspr.chat/jaspercraft/socket'}];
  });
  await b.flush();assert.equal(b.elements['alias-panel'].hidden,false);assert.equal(b.starts.length,0);
  b.elements['alias-username'].value='Cipher Nomad';
  b.elements['alias-panel'].events.submit({preventDefault(){}});await b.flush();
  assert.equal(b.starts.length,1);assert.equal(b.starts[0].gameName,'cipher_nomad');
});

test('account switches and gateway failures never start a mismatched game',async()=>{
  const b=browser(async url=>url.endsWith('/auth')?[200,{authProviders:{google:true,discord:true}}]
    :url.endsWith('/me')?[200,me]:[200,{gameName:'someone_else',serverAddress:'wss://jaspr.chat/jaspercraft/socket'}]);
  await b.flush();assert.equal(b.starts.length,0);assert.equal(b.elements['game-host'].children.length,0);
  assert.match(b.elements['game-status-text'].textContent,/account changed/);
});

test('audio startup is nonblocking and resumes on ordinary gameplay input',async()=>{
  let resumeCalls=0, mainCalls=0;
  class NativeAudioContext {
    constructor(){this.state='suspended';}
    resume(){resumeCalls++;return Promise.resolve();}
  }
  const listeners={};
  class NativeSocket { constructor(url){this.url=url;} }
  const ctx={URL,Proxy,Reflect,Math,setTimeout,WebSocket:NativeSocket,parent:{},location:new URL('https://jaspr.chat/jaspercraft/client.html'),
    AudioContext:NativeAudioContext,
    addEventListener:(name,fn)=>{listeners[name]=fn;},
    main:()=>{mainCalls++;ctx.audio=new ctx.AudioContext();}};
  ctx.window=ctx;vm.runInNewContext(source('jaspr-client.js'),ctx);
  ctx.JasperCraftClient.start({gameName:'jasper',serverAddress:'wss://jaspr.chat/jaspercraft/socket',join:true});
  assert.equal(mainCalls,1);assert.equal(resumeCalls,1);
  listeners.keydown();assert.equal(resumeCalls,2);
});

test('mobile taps activate HTML controls without the canvas stealing focus',()=>{
  const listeners={},timers=[];
  let canvasFocusCalls=0,windowFocusCalls=0;
  const canvas={
    hasAttribute:()=>false,
    setAttribute:()=>{},
    focus:()=>{canvasFocusCalls++;}
  };
  class NativeSocket { constructor(url){this.url=url;} }
  const ctx={URL,Proxy,Reflect,Math,Promise,WebSocket:NativeSocket,parent:{},location:new URL('https://jaspr.chat/jaspercraft/client.html'),
    localStorage:fakeStorage(),
    setTimeout:(fn,delay)=>{timers.push({fn,delay});return timers.length;},clearTimeout:()=>{},
    fetch:async()=>({ok:true,status:200,json:async()=>({ok:true,revision:1})}),
    focus:()=>{windowFocusCalls++;},
    addEventListener:(name,fn)=>{(listeners[name]||(listeners[name]=[])).push(fn);},
    document:{hidden:false,pointerLockElement:null,hasFocus:()=>true,querySelector:selector=>selector==='canvas'?canvas:null,
      body:canvas,addEventListener:(name,fn)=>{(listeners['document:'+name]||(listeners['document:'+name]=[])).push(fn);}},
    main:()=>{}};
  ctx.window=ctx;vm.runInNewContext(source('jaspr-client.js'),ctx);
  ctx.JasperCraftClient.start({gameName:'jasper',serverAddress:'wss://jaspr.chat/jaspercraft/socket',join:true});
  canvasFocusCalls=0;windowFocusCalls=0;
  listeners.touchstart[0]({target:{closest:()=>({tagName:'BUTTON'})}});
  assert.equal(canvasFocusCalls,0);assert.equal(windowFocusCalls,0);
  listeners.touchstart[0]({target:{closest:()=>null}});
  assert.equal(canvasFocusCalls,1);assert.equal(windowFocusCalls,1);
});

test('client bytes remain pinned; first-load disclaimer stays bypassed; SSO adds no frame hooks',()=>{
  const hashes={'classes.js':'5e76cb9544fddd2df23d02390890de8e3f7f1f336a8d07f622f09d80bf571344','assets.epk':'dbf3e804fc851f68987b35a32b39518aa28de9780ea541f9b06967b57f7488d6'};
  for(const [name,hash] of Object.entries(hashes))assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(siteRoot,name))).digest('hex'),hash);
  assert.match(source('classes.js'),/if\(false \)\{d=new B1K;\$p=9;continue _;\}/);
  assert.doesNotMatch(source('classes.js'),/if\(!c\.cqw\)\{d=new B1K;\$p=9;continue _;\}/);
  assert.doesNotMatch(source('lang/en_us.lang'),/^gui\.firstload\./m);
  assert.match(source('classes.js'),/function FKS\(\)\{\}/);
  assert.match(source('classes.js'),/function DJn\(\)\{HIl=1;\}/);
  assert.doesNotMatch(source('classes.js'),/Mobile Browser Detected|_eaglercraftX_mobile_launch_client/);
  assert.doesNotMatch(source('jaspr-client.js'),/window\.DJn|window\.HIl/);
  assert.match(source('jaspr-client.js'),/focusGameSurface/);
  assert.match(source('jaspr-client.js'),/isInteractiveTarget/);
  assert.match(source('jaspr-sso.js'),/frame\.contentWindow\.focus\(\)/);
  assert.match(source('index.html'),/jaspr-sso\.js\?build=20260915-shader-picker-dedup1/);
  assert.match(source('client.html'),/classes\.js\?build=20260915-shader-picker-dedup1/);
  assert.match(source('jaspr-sso.js'),/client\.html\?build=20260915-shader-picker-dedup1/);
  assert.match(source('client.html'),/jaspr-client\.js\?build=20260914-dlights10/);
  assert.match(source('jaspr-client.js'),/assets\.epk\?build=20260914-dlights10/);
  assert.match(source('index.html'),/class="discord-promo"/);
  assert.match(source('index.html'),/href="https:\/\/discord\.gg\/5cdQYGXkvP"/);
  assert.match(source('index.html'),/target="_blank"/);
  assert.match(source('index.html'),/auth\/discord-clyde\.svg/);
  assert.match(source('jaspr-sso.css'),/\.playing \.discord-promo\{display:grid\}/);
  assert.doesNotMatch(source('jaspr-client.js'),/searchParams\.set\("ticket"/);
  for(const name of ['jaspr-sso.js','jaspr-profile.js','jaspr-client.js'])assert.doesNotMatch(source(name),/setInterval|requestAnimationFrame/);
});

test('server defaults new players to Survival and preserves per-player gamemode changes',()=>{
  const properties=fs.readFileSync(path.join(root,'server','server.properties'),'utf8');
  assert.match(properties,/^gamemode=0$/m);
  assert.match(properties,/^force-gamemode=false$/m);
  assert.match(properties,/^motd=JasperCraft - The Last Broadcast \| Zombie Apocalypse$/m);
  assert.match(properties,/^difficulty=3$/m);
  assert.doesNotMatch(properties,/^gamemode=1$/m);
});
