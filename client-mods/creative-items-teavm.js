// Native TeaVM adapter. Browser entries are display-only request tokens; Paper replaces
// a selected token with a fresh server-authored item before it reaches player inventory.
function JasprCreativeTabAllows(a,b){
  if(b==='gun'||b==='melee'||b==='armor')return a===KQ5;
  if(b==='gadget')return a===KQ5;
  if(b==='consumable')return a===KQO;
  if(b==='material'||b==='supply'||b==='artifact')return a===KQL;
  if(b==='block')return a===KIY;
  return false;
}
function JasprCreativeAppend(a,b,c){var d,e,f,g,h,i,$p,$z;$p=0;if(FX()){var $T=Ds();$p=$T.l();i=$T.l();h=$T.l();g=$T.l();f=$T.l();e=$T.l();d=$T.l();c=$T.l();b=$T.l();a=$T.l();}_:while(true){switch($p){
case 0:d=JasprCreativeCatalog;e=0;f=c&&a!==null?$rt_ustr(a).toLowerCase():'';$p=1;
case 1:while(e<d.length){g=d[e];if(c?(f===''||g.search.indexOf(f)>=0):JasprCreativeTabAllows(a,g.category))break;e++;}if(e>=d.length)return;h=$rt_str(g.snbt);i=new Bk;$p=2;
case 2:$z=E0F(h);if(B()){break _;}h=$z;$p=3;
case 3:BH8(i,h);if(B()){break _;}$p=4;
case 4:Ghp(b,i);if(B()){break _;}e++;$p=1;continue _;
default:FT();}}Ds().s(a,b,c,d,e,f,g,h,i,$p);}
