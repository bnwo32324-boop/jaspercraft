'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {build,unpatch,sha,BASE}=require('../scripts/build-gore-client.cjs');

function adapterFixture(){
  const calls=[];
  const context={
    $rt_str:value=>String(value),
    $rt_globals:{console:{warn:message=>calls.push({type:'warn',message})}},
    CA:(_font,text)=>String(text).length*6,
    FgQ:(_font,text,x,y,color)=>calls.push({type:'draw',text,x,y,color})
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../client-mods/ping-overlay-teavm.js'),'utf8'),context);
  return {context,calls};
}

test('ping label uses the native response-time field and preserves bars',()=>{
  const live=fs.readFileSync(path.join(__dirname,'../site/classes.js'),'utf8'),candidate=build(live);
  const start=candidate.indexOf('function FQM('),end=candidate.indexOf('\nfunction ',start+10),body=candidate.slice(start,end);
  assert.match(body,/l=e\.bzW/);
  assert.match(body,/JasprPingOverlay\.draw\(a,b,c,d,e\)/);
  assert.match(candidate,/var JasprPingOverlay = \(function\(\)/);
  assert.equal(sha(unpatch(candidate)),BASE);
});

test('tab ping text is rounded, color-coded, and safe when unavailable',()=>{
  const {context,calls}=adapterFixture(),gui={lJ:{bw:{}}};
  context.JasprPingOverlay.draw(gui,100,200,20,{bzW:123.4});
  context.JasprPingOverlay.draw(gui,100,200,30,{bzW:742.8});
  context.JasprPingOverlay.draw(gui,100,200,40,{bzW:-1});
  assert.deepEqual(calls.map(call=>call.type),['draw','draw','draw']);
  assert.equal(calls[0].text,'123 ms');
  assert.equal(calls[1].text,'743 ms');
  assert.equal(calls[2].text,'-- ms');
  assert.notEqual(calls[0].color,calls[1].color);
  assert.equal(calls[2].x,255);
});
