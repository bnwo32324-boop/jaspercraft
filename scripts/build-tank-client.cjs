'use strict';

// Exact, candidate-only JasprTanks stage for the already-composed browser client (site/classes.js).
// Four fenced hooks plus one appended block; every anchor must match exactly once or the build stops.
//  - Minecraft.runTick: JasprTank.tick(mc) gives the driver 1-block steps while tank parts ride them and
//    caches whether the server advertises tanks (hidden "jtk" objective) for the touch controls.
//  - Entity.updatePassenger (DJF): armor stands seated on a player take the player's body yaw (hull, first
//    stand) or head yaw (turret, second stand), including the previous-tick value, so they interpolate
//    exactly like the player model.
//  - RenderLivingBase.doRender (DWR): skips the "rider faces its mount" clamp for those stands only.
//  - JasprVideoMobileBridge.tank()/tankView(): cached state and the third/first-person switch for the touch controls.
// Two more fenced hooks (/*JASPR_VEHICLE_V1*/) put a "Mobile: Tank / Orbital Sentinel" toggle on the Edit Profile screen:
//  - GuiScreenEditProfile.initGui (E_t) adds the button under Add Skin / Clear Skin, above Done.
//  - GuiScreenEditProfile.actionPerformed (E_1) flips the saved choice when that button is pressed.
// The choice lives in this browser (localStorage jaspr.vehicle.v1); in game, JasprVehicle.sync asks the server for it once
// per connection or change, and adopts switches made in game (Mode button, /tank mode) so both stay in agreement.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const source = process.env.TANK_CLIENT_SOURCE || path.join(root, 'site', 'classes.js');
const sha = value => crypto.createHash('sha256').update(value).digest('hex');
const MARK = '/*JASPR_TANK_V1*/';
const VEHICLE = '/*JASPR_VEHICLE_V1*/';

const BLOCK = [
  '/* JASPR_TANK_V1_BEGIN */',
  '/* JasprTanks: the server seats two invisible armor stands wearing the hull and turret models on the driver,',
  ' * for clients only. The hull follows the driver\'s body yaw, the turret the head yaw; the driver steps up whole',
  ' * blocks and starts in third person. Up to four riders sit on the hull stand; they are placed on the rear deck',
  ' * and the track guards. state() is a cached, read-only view for the touch controls (no engine calls from DOM',
  ' * handlers). */',
  '/* Vehicle choice for touch players: Tank or Orbital Sentinel, toggled on the Edit Profile screen and kept in this browser. */',
  'var JasprVehicle=(function(){',
  '  var KEY="jaspr.vehicle.v1",ID=7301,pending=true,lastMode=null,requests=0,settled=0;',
  '  function get(){try{return $rt_globals.localStorage.getItem(KEY)==="sentinel"?"sentinel":"tank";}catch(e){return "tank";}}',
  '  function put(v){try{$rt_globals.localStorage.setItem(KEY,v==="sentinel"?"sentinel":"tank");}catch(e){}}',
  '  function label(v){return $rt_str(v==="sentinel"?"Mobile: Orbital Sentinel":"Mobile: Tank");}',
  '  // Edit Profile: right column under Add Skin / Clear Skin, clear of the player preview and above Done.',
  '  function add(screen){var b=new B3;Bq3(b,ID,((screen.q/2|0)-21|0),((screen.L/6|0)+134|0),143,20,label(get()));Y(screen.be,b);}',
  '  function pressed(button){if(!button||button.bF!==ID)return false;var v=get()==="sentinel"?"tank":"sentinel";put(v);button.dd=label(v);pending=true;return true;}',
  '  function reset(){pending=true;lastMode=null;settled=0;}',
  '  // Once per connection or change: ask for the saved choice. Afterwards a switch made in game becomes the choice.',
  '  function sync(state){',
  '    // Wait ~1.5 s after the vehicle appears: the server applies the claim and its mode score arrives after the mount.',
  '    if(!state.supported||!state.active){settled=0;return;}if(++settled<3)return;',
  '    var want=get(),bridge=$rt_globals.JasprVideoMobileBridge;',
  '    if(pending){pending=false;lastMode=state.mode;if(state.mode!==want&&bridge){requests++;bridge.text("/tank mode "+want,true);}return;}',
  '    if(lastMode!==null&&state.mode!==lastMode)put(state.mode);',
  '    lastMode=state.mode;',
  '  }',
  '  // Diagnostics: the choice, sync state and, on the Edit Profile screen, where the toggle sits (GUI units).',
  '  function status(){var s=HEH&&HEH.cj,l=s instanceof Zk?s.be:null,i,b,button=null;',
  '    for(i=0;l&&i<l.g;i++){b=l.qN.data[i];if(b&&b.bF===ID)button={x:b.eh,y:b.d$,w:b.fg,h:b.i2,label:$rt_ustr(b.dd),screenW:s.q,screenH:s.L};}',
  '    return {choice:get(),pending:pending,requests:requests,button:button};}',
  '  return {get:get,add:add,pressed:pressed,reset:reset,sync:sync,status:status};',
  '})();',
  'var JasprTank=(function(){',
  '  var STEP=1.0,VANILLA_STEP=0.6000000238418579,cache={supported:false,active:false,cooldown:30,view:0,mode:"tank",pickup:false,follow:false},checked=0,failure=null,wasActive=false;',
  '  // Rider seats in the hull frame: [back, right, drop] in blocks from the server seat (driver feet + 1.0). All four',
  '  // sit on the track guards, so the driver\'s third-person view over the turret stays clear.',
  '  var SEATS=[[0.45,-0.8,-1.24],[0.45,0.8,-1.24],[-0.3,-0.8,-1.24],[-0.3,0.8,-1.24]];',
  '  function slotOf(list,entity){var k=0,i,e;for(i=0;i<list.g;i++){e=list.qN.data[i];if(e===entity)return k;if(e instanceof HC)k++;}return -1;}',
  '  function parts(p){var list=p&&p.a1k,n=0,i;if(!list)return 0;for(i=0;i<list.g;i++)if(list.qN.data[i] instanceof HC)n++;return n;}',
  '  function seat(stand,rider){',
  '    var driver=stand.fS,list=stand.a1k,i,s,yaw;',
  '    if(!(driver instanceof Cb)||slotOf(driver.a1k,stand)!==0)return;',
  '    for(i=0;i<list.g&&list.qN.data[i]!==rider;i++){}',
  '    s=SEATS[i];if(!s)return;yaw=driver.cZ*0.017453292519943295;',
  '    rider.RW(rider.b+Math.sin(yaw)*s[0]-Math.cos(yaw)*s[1],rider.f+s[2],rider.c-Math.cos(yaw)*s[0]-Math.sin(yaw)*s[1]);',
  '  }',
  '  function align(vehicle,passenger){',
  '    if(vehicle instanceof HC&&passenger instanceof Cb){seat(vehicle,passenger);return;}',
  '    if(!(vehicle instanceof Cb)||!(passenger instanceof HC))return;',
  '    var slot=slotOf(vehicle.a1k,passenger),prev,cur;',
  '    if(slot<0)return;',
  '    prev=slot===0?vehicle.s1:vehicle.zM;cur=slot===0?vehicle.cZ:vehicle.gN;',
  '    passenger.cy=prev;passenger.C=cur;passenger.s1=prev;passenger.cZ=cur;passenger.zM=prev;passenger.gN=cur;',
  '  }',
  '  function view(mc){var g=mc&&mc.G;if(!g)return 0;g.lv=g.lv===0?1:0;cache.view=g.lv;return g.lv;}',
  '  function free(entity,vehicle){return entity instanceof HC&&vehicle instanceof Cb;}',
  '  function advertised(mc){',
  '    var w=mc.X,sb=w&&w.k3,o,sc,i,s,n;cache.supported=false;',
  '    if(!sb){failure="no-scoreboard";return;}',
  '    try{o=Cbd(sb,$rt_str("jtk"));if(!o){failure="no-objective";return;}',
  '      if($rt_ustr(o.a47).indexOf("JTK v1")!==0){failure="display";return;}cache.supported=true;',
  '      sc=EEW(sb,o);for(i=0;sc&&i<sc.g;i++){s=sc.qN.data[i];n=$rt_ustr(s.X5);',
  '        if(n==="cooldown")cache.cooldown=s.jk|0;else if(n==="mode")cache.mode=(s.jk|0)===1?"sentinel":"tank";else if(n==="pickup")cache.pickup=(s.jk|0)===1;else if(n==="follow")cache.follow=(s.jk|0)===1;}',
  '      failure=null;}',
  '    catch(e){failure=String(e&&e.message||e).slice(0,160);}',
  '  }',
  '  function tick(mc){',
  '    var p=mc&&mc.v;if(!p){cache.active=cache.supported=wasActive=false;JasprVehicle.reset();return;}',
  '    cache.active=parts(p)>0;',
  '    if(cache.active){if(p.r5<STEP)p.r5=STEP;p.$jasprTankStep=1;}',
  '    else if(p.$jasprTankStep){p.r5=VANILLA_STEP;p.$jasprTankStep=0;}',
  '    // Drivers start behind their tank (third person); the View button or F5 switches back.',
  '    if(cache.active&&!wasActive&&mc.G)mc.G.lv=1;',
  '    wasActive=cache.active;cache.view=mc.G?mc.G.lv:0;',
  '    if(++checked>=10){checked=0;advertised(mc);JasprVehicle.sync(cache);}',
  '  }',
  '  function state(){return {supported:cache.supported,active:cache.active,cooldown:cache.cooldown,view:cache.view,mode:cache.mode,pickup:cache.pickup,follow:cache.follow};}',
  '  // Diagnostics only: state names and counts, nothing identifying.',
  '  $rt_globals.JasprTankDiagnostics={status:function(){var p=HEH&&HEH.v,s=p&&p.fS;return {supported:cache.supported,active:cache.active,cooldownTicks:cache.cooldown,',
  '    view:cache.view,mode:cache.mode,pickup:cache.pickup,follow:cache.follow,parts:parts(p),riders:s instanceof HC?s.a1k.g:0,riding:s instanceof HC,stepHeight:p?p.r5:null,blocksPerSecond:p?Math.round(Math.sqrt((p.b-p.dn)*(p.b-p.dn)+(p.c-p.dv)*(p.c-p.dv))*200)/10:null,vehicle:JasprVehicle.status(),failure:failure};}};',
  '  return {tick:tick,align:align,free:free,state:state,view:view,parts:parts};',
  '})();',
  '/* JASPR_TANK_V1_END */',
].join('\r\n');

function functionBody(text, name) {
  const start = text.indexOf('function ' + name + '(');
  if (start < 0 || text.indexOf('function ' + name + '(', start + 1) >= 0) throw new Error(name + ' must be defined once');
  const end = text.indexOf('\nfunction', start + 10);
  return [start, end < 0 ? text.length : end];
}
function within(text, name, from, to, label) {
  const [start, end] = functionBody(text, name);
  const body = text.slice(start, end);
  if (body.split(from).length !== 2) throw new Error(label + ' anchor must occur exactly once in ' + name);
  return text.slice(0, start) + body.replace(from, () => to) + text.slice(end);
}
function once(text, from, to, label) {
  if (text.split(from).length !== 2) throw new Error(label + ' must occur exactly once');
  return text.replace(from, () => to);
}

/** Upgrade path: the hooks are already installed; replace only the fenced JasprTank block with this version. */
function refresh(input) {
  const begin = '/* JASPR_TANK_V1_BEGIN */', end = '/* JASPR_TANK_V1_END */';
  if (input.split(MARK).length - 1 !== 4) throw new Error('Installed tank hooks are damaged');
  if (input.split(begin).length !== 2 || input.split(end).length !== 2) throw new Error('Tank block fences must occur exactly once');
  const output = input.slice(0, input.indexOf(begin)) + BLOCK + input.slice(input.indexOf(end) + end.length);
  new vm.Script(output, {filename: 'candidate/tank-client/classes.js'});
  return output;
}

/** Edit Profile toggle hooks; installed once, verified afterwards. */
function vehicleHooks(input) {
  const count = input.split(VEHICLE).length - 1;
  if (count === 2) return input;
  if (count !== 0) throw new Error('Installed vehicle hooks are damaged');
  let output = within(input, 'E_t', '$p=15;case 15:ErU(a);', VEHICLE + 'JasprVehicle.add(a);$p=15;case 15:ErU(a);', 'edit profile buttons');
  output = within(output, 'E_1', 'case 0:if(!a.ru){c=b.bF;if(!c){', 'case 0:if(!a.ru){c=b.bF;' + VEHICLE + 'if(JasprVehicle.pressed(b))return;if(!c){', 'edit profile action');
  new vm.Script(output, {filename: 'candidate/tank-client/classes.js'});
  return output;
}

function build(input) {
  if (input.includes('JASPR_TANK_V1')) return vehicleHooks(refresh(input));
  let output = input;
  output = once(output, 'case 0:JasprRevive.tick();JasprDH.maintain();$p=99;',
    'case 0:JasprRevive.tick();JasprDH.maintain();' + MARK + 'JasprTank.tick(a);$p=99;', 'runTick hook');
  // Entity.updatePassenger: passenger.setPosition(x, y + mountedYOffset + yOffset, z).
  output = within(output, 'DJF', 'case 4:b.RW(d,e,f);if(B()){break _;}return;',
    'case 4:b.RW(d,e,f);if(B()){break _;}' + MARK + 'JasprTank.align(a,b);return;', 'updatePassenger');
  // RenderLivingBase.doRender: "if riding a living entity, face the mount".
  output = within(output, 'DWR', 'h=$z;if(h instanceof Co){$p=22;continue _;}',
    'h=$z;if(h instanceof Co&&' + MARK + '!JasprTank.free(b,h)){$p=22;continue _;}', 'rider yaw clamp');
  output = once(output, 'bindings:function(){return HEH&&HEH.G;}};',
    'bindings:function(){return HEH&&HEH.G;},' + MARK + 'tank:function(){return JasprTank.state();},tankView:function(){return JasprTank.view(HEH);}};', 'mobile bridge');
  output = once(output, '/* JASPR_REVIVE_DH_V1_END */', '/* JASPR_REVIVE_DH_V1_END */\r\n\r\n' + BLOCK, 'append anchor');
  return vehicleHooks(output);
}

if (require.main === module) {
  const inputBytes = fs.readFileSync(source);
  const output = build(inputBytes.toString('latin1'));
  const outputBytes = Buffer.from(output, 'latin1');
  const dir = path.join(root, 'candidate', 'tank-client');
  fs.mkdirSync(dir, {recursive: true});
  fs.writeFileSync(path.join(dir, 'classes.js'), outputBytes);
  const report = {source: path.relative(root, source), inputSha256: sha(inputBytes), outputSha256: sha(outputBytes),
    inputBytes: inputBytes.length, outputBytes: outputBytes.length, hooks: output.split(MARK).length - 1, vehicleHooks: output.split(VEHICLE).length - 1};
  fs.writeFileSync(path.join(dir, 'build-report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
module.exports = {build};
