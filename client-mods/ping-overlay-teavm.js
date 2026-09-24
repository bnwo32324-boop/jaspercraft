/* Version-pinned adapter for the native 1.12.2 player-list overlay.
 * FQM supplies the same NetworkPlayerInfo used by the vanilla ping bars.
 * This is presentation-only: it does not measure, transmit, or persist data.
 */
var JasprPingOverlay = (function(){
  "use strict";
  var errorReported = false;

  function normalized(info){
    if(!info) return -1;
    var value=info.bzW;
    if(typeof value!=="number") value=parseFloat(value);
    return isFinite(value)&&value>=0?Math.max(0,Math.round(value)):-1;
  }
  function label(value){return value<0?"-- ms":String(value)+" ms";}
  function color(value){
    if(value<0) return -5197648; // opaque gray
    if(value<100) return -6226016; // opaque green
    if(value<200) return -128; // opaque yellow
    if(value<500) return -16288; // opaque amber
    return -32640; // opaque red
  }
  function draw(gui,columnWidth,right,rowY,info){
    try{
      if(!gui||!gui.lJ||!gui.lJ.bw||!info||typeof FgQ!=="function"||typeof CA!=="function") return;
      var value=normalized(info),text=$rt_str(label(value)),font=gui.lJ.bw;
      var iconX=((right+columnWidth)|0)-11,width=CA(font,text),x=iconX-width-4;
      FgQ(font,text,x,rowY,color(value));
    }catch(error){
      // A cosmetic label must never break the native tab overlay.
      if(!errorReported&&$rt_globals.console&&typeof $rt_globals.console.warn==="function"){
        errorReported=true;$rt_globals.console.warn("[JasperCraft ping overlay] disabled after a draw error");
      }
    }
  }
  return {draw:draw};
}());
