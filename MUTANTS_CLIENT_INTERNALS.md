# JasperCraft browser client internals (for porting Mutant Beasts as a client stage)

Research only. Nothing in the live client or in `Eaglercraft-1.12.2-Tailscale` was changed. Written 2026-10-05.

## 0. Ground truth, tools, legend

| Item | Value |
| --- | --- |
| Live bundle | `C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\site\classes.js`, 16,032,420 bytes, sha256 `7863c7db4b22fd90...`, Oct 5 18:05. It is the original bundle plus about 28 fenced `/* JASPR_*_BEGIN/END */` blocks and a few small marked hooks (`/*JW*/`, `/*JT*/`, `/*JF*/`, `/*JN*/`, ...). Minified names are unchanged. |
| Original unpatched bundle | `C:\Users\AM\Documents\.eagler-1122-evaluation\1.12.2-WASM\classes.js`, sha256 `93d29170...`. This matches `client-reference\SOURCE.md` (TwoMuchNerdo/1.12.2-WASM `94e4c10`). Its `classes.js.map` is byte-identical to `site\classes.js.map` (sha `0a703c86...`). **Map against this copy: the offsets are exact.** `scripts\client-source-map.cjs` maps against the patched live file, so its offsets drift after inserted stages. |
| Java source | `C:\Users\AM\Documents\JasperLoader-Compatibility-Work\vendor\eagler-base-112\src\game\java` (MCP names; lenabena67 "client base", the same Eaglercraft 1.12 lineage). Glue code is under `src\main\java\net\lax1dude\eaglercraft\` and the browser platform under `src\teavm\java\...`. |
| Line drift | Some files match the map exactly (ModelRenderer). Others drift: RenderLivingBase about +2, NetHandlerPlayClient about ±3. Some vendor files have **local JasperLoader edits**: NetHandlerPlayClient (`forgeHandshake`), DefaultResourcePack (`JasperGeneratedContent`), RenderItem, Item, Block, GuiMainMenu and the recipe packets. Use `git show HEAD:<path>` in the vendor checkout for the pristine upstream file. Where they differ, the deployed JS wins. |
| MCP mapping files | The vendor folder has no `mcp-1.12.2-srg` or `mcp-snapshot`. They are not needed because the source is already MCP-named. `JasperCraft-Mutants\mappings\` holds Bukkit csrg files, which are for the server side. |
| Mod source | `C:\Users\AM\Downloads\MutantCreaturesLegacy-main (1)\MutantCreaturesLegacy-main` (chumbanotz Mutant Beasts 1.12.2). The client code uses `addBox` 129 times, `new ModelRenderer` 84, `JointModelRenderer` 41, `ScalableModelRenderer` 7, the texture matrix (`matrixMode`) 8, `setLightmapTextureCoords` 7 and `renderBlockBrightness` 4. It also calls `enableNormalize` and `enableOutlineMode`, which **do not exist** in Eaglercraft's GlStateManager, so drop them. |

**How names were verified**

- **SM**: the source map of the original bundle, through my index that maps JS functions to Java file and line range, plus a matching Java body.
- **BODY**: I read the JS body and it matches the Java.
- **STAGE**: a live stage uses the name; the stage is named.
- **META**: the TeaVM `$rt_metadata` virtual-method table.
- **HARN**: checked offline. `scripts\mobends-native-harness.cjs` loads the live bundle in `node:vm` without running `main()`. My script ran 36 checks and all passed. Two more sections (MathHelper and Particle) could not run because a static initializer logs through log4j's date formatter, which fails in the vm.

**Confidence**: H = verified two independent ways. M = one way, or reasoned from code. L = inferred.

My scratch tools (`build-index.cjs`, `q.cjs`, `peekfn.cjs`, `fdiff.cjs`, `strpool.cjs`, `epklist.cjs`, `anchors.cjs`, `verify-snippets.cjs`) live in a session scratchpad that may be cleaned up. Appendix A recreates the essential one, the JS-to-Java index.

---

## 1. TeaVM conventions

| Concept | JS form | Evidence / conf. |
| --- | --- | --- |
| Module | The whole bundle is one UMD closure `function($rt_globals,$rt_exports){...}` in strict mode. Stages insert code before the final `}));` (Mo' Bends, NBT-skin) or before `\nvar JasprCreativeCatalog=[` (armory, armor bar). Code placed there sees every minified name as a closure variable. `$rt_globals` is `self`/`window`. | BODY+STAGE H |
| Runtime aliases | `I`=$rt_throw, `E`=$rt_cls, `G`=$rt_createArray(cls,n), `T`=$rt_createArrayFromData(cls,[..]), `Cm`=$rt_isInstance, `Ds`=$rt_nativeThread, `B`=$rt_suspending, `FX`=$rt_resuming, `FT`=$rt_invalidPointer, `C`=$rt_s (string pool), `R`=$rt_eraseClinit, `W`=$rt_imul, `F`=$rt_wrapException, `J`=$rt_classWithoutFields, `Bd`=$rt_compare, `Bh`/`Ej`/`B_`/`EH`/`QR`/`JK`=int/float/byte/char/double/long array of size n, `CN`/`Lz`/`Mz`/`ANB`/`I5`=int/float/byte/char/double array from data, `DU`=Long_toNumber, `N`=Long_fromInt, `M`=Long_create, `BD`=Long_ZERO, `BV`=Long_lo | Alias block right after `function $rt_throwCCE(){}` in the bundle, H |
| JS string → Java String | `$rt_str("x")` makes a new `java.lang.String` on every call, so cache it (armory `api.key()`, nbt-skin `JasprNbtSkinKeys`). The pooled literal is `C(n)`, for example `C(7575)`="Couldn't render entity". | BODY+HARN H |
| Java → JS string | `$rt_ustr(s)` reads `s.iJ.data`, a char array | BODY+HARN H |
| Objects | Allocate the class, then call the constructor: `p=new Bb;Gp9(p,str)`. Many classes also have a static helper `function BX(a,b,c){var d=new M2();BZS(d,a,b,c);return d;}`. To find one, grep `var x=new CLS();` | STAGE (armory, nbt-skin) H |
| Fields | Plain properties with per-class minified names. Booleans are ints (0/1). Floats are plain JS numbers (no `Math.fround`). Stages add their own properties with a `$` prefix (`$mb`, `$jasprTankStep`, `$dsText`); minified names never start with `$`. | BODY+STAGE H |
| Virtual call | `obj.name(args)`, with names from `$rt_metadata`, for example `"jV",function(b,...){DqP(this,...)}`. A non-virtual call (final, private, super, static) is a global function with the receiver first: `DqP(a,b,...)`. Generic bridges add extra names, for example RenderLiving `jV`→`F61`→`a.RC(...)`→`CIl`. A plain JS object can implement an interface by method name: Mo' Bends' layer `{pq,no}` and nbt-skin's getter `{TM}`. It must not reach `hashCode`/`equals`/`instanceof`. | META+STAGE H |
| Statics and class init | Statics are top-level `var`s (`var HEH=null;` is Minecraft.theMinecraft). Each class has a lazy initializer "trampoline", for example `function IY(){...AGY_$clinitCalled...;Ce8();IY=R(AGY);}`. After it runs once it replaces itself with an empty function. Call the trampoline before reading a static of a class that may not be initialized yet. Examples: `Cx()` SoundCategory, `WP()` ISound.AttenuationType, `C5()` DefaultVertexFormats, `FG()` MathHelper, `I$()` EntityList, `NN()` Entity, `Ub()` EntityLivingBase, `C7()` GlStateManager, `BvO()` TEISR, `Lp()` TextureMap. Mo' Bends calls `G9();Hc();Wq();By();Dt();AKp();U6();HJ();C5();` once. | BODY+STAGE H |
| Arrays | `.data` is a JS Array, or a typed array for primitives (HARN: `Bh(4).data` is an Int32Array). Element classes are JS classes: `G(M2,8)` makes a ModelRenderer[8]. | HARN H |
| ArrayList / HashMap | `Bq()` = new ArrayList, `Y(l,x)` = add, `Bm(l,i)` = get, `HB(l,i)` = remove(i), `ECM(l,o)` = remove(o), `l.g` = size, `l.qN.data` = backing array. `Ci()` = new HashMap, `Cno(m,k)` = get, `EDK(m,k,v)` = put. `U(i)` = Integer.valueOf. Iterators: `BA(c)` iterator, `Bz(it)` hasNext, `GDx(it)` next. | HARN+BODY H |
| int / long | `x|0`, `W(a,b)` (imul). In this runtime, `long` is **BigInt** when `BigInt64Array` exists (all current browsers; HARN `typeof N(5)==='bigint'`), otherwise `{lo,hi}` objects. Always use `N()`/`DU()`. | BODY+HARN H |
| Exceptions | Java exceptions surface as JS errors. `F($$e)` unwraps one. Stages wrap their entry points in try/catch and disable themselves on the first error (Mo' Bends `fail()`). | STAGE H |

### Resumable (suspendable) functions

TeaVM compiles any method that *might* suspend into a state machine:

```js
function X(a,b){var c,$p,$z;$p=0;
  if(FX()){var $T=Ds();$p=$T.l();c=$T.l();b=$T.l();a=$T.l();}   // resuming: pop own locals (reverse order)
  _:while(true){switch($p){
    case 0: ...; $p=1;
    case 1: $z=Callee(a); if(B()){break _;}   // B(): thread is suspending -> unwind
            ...; return r;
    default: FT();                             // invalid state
  }}
  Ds().s(a,b,c,$p);                            // save own locals + $p on the thread stack
}
```

- The game loop suspends **every frame**: `PlatformInput.update` calls the @Async `asyncRequestAnimationFrame` or `immediateContinue`. Almost every engine function is therefore compiled resumable, even simple ones (all GlStateManager calls start with `C7()`).
- A call **actually** suspends only if it reaches an `@Async` native. In the render path these are:
  - **Image decode.** `PlatformAssets.loadImageFile` is `GbM`, which calls `thread.suspend(...)` and `Dtw`. It is reached by the **first** `TextureManager.bindTexture` (`D17`) of a ResourceLocation that is not yet loaded (`rl.a3s!==1`), by `SimpleTexture.loadTexture`, and by `TextureManager.loadTexture` (`GC3`) with a SimpleTexture.
  - **Audio decode.** `PlatformAudio.decodeAudioBrowserAsync` is reached by the first `SoundManager.playSound` of an .ogg not yet decoded: `DFK` SoundHandler.playSound → `Egn` → `loadAudioDataNew`. The JOrbis fallback is synchronous.
  - **Network, IndexedDB, sleep and similar.** The `PlatformRuntime` @Async methods include remote lang-file downloads.
- **Safe to call synchronously from plain JS** (verified bodies, no async below them once their class is initialized): every GlStateManager function; Tessellator and WorldRenderer; ModelRenderer `E7Q`/`Eu3`/`FFZ`/`F$t`; callList `Dle`; `bindTexture` of an already-loaded RL or of a DynamicTexture; FontRenderer drawing; `Ceq` (block rendering, uses the atlas); `ParticleManager.addEffect`/`spawnEffectParticle`; MathHelper; all entity and data-manager reads; `Gp9` (the ResourceLocation constructor). Mo' Bends, gore and waypoints do exactly this ("Hooks are entered at completed renderer fiber boundaries; nothing here suspends").
- **Must be resumable**: a plain-JS function must never call something that can actually suspend. If it does, the callees unwind while the plain function keeps running, and on resume the frames are popped in the wrong order. The result is `FT()` "Invalid recorded state" or corrupted locals.
  - The cure is to write the function in the state-machine form above (templates: `JasprArmorBarDraw` in `build-armor-bar-client.cjs`, `JasprGoreDraw`, `JasprDSSoundStart`).
  - Its call site must itself be a `case N:` inside a resumable engine function, followed by `if(B()){break _;}`.
- Do not call engine code from DOM handlers or timers, which run outside the game-loop thread. The tank stage comment says the same: "no engine calls from DOM handlers".

---

## 2. Model API (ModelBase / ModelRenderer / ModelBox)

### ModelBase: JS class `DQ`, init `Gs(a)` (SM L14-18, HARN)

| Java | JS | Notes |
| --- | --- | --- |
| swingProgress / isRiding / isChild | `v5` / `b$r` / `ww` | `ww` defaults to 1 |
| boxList / modelTextureMap | `cJ9` (ArrayList) / `cXZ` (HashMap) | |
| textureWidth / textureHeight | `vI` / `vd` | Default 64/32. **Set these before creating parts**, because the part constructor copies them. |
| render(Entity,f×6) | virtual `ha` | No base implementation in the vtable (only subclasses define it, e.g. ModelBiped `ha`→`FUa`). HARN. |
| setRotationAngles(f×6, Entity) | virtual `i3` (base `FUu`, empty) | META+HARN |
| setLivingAnimations(ELB, f, f, pt) | virtual `Lo` (base `BNm`, empty) | META+HARN |
| copyModelAngles(src,dst) | `AGZ(src,dst)` | Copies angles and rotation points |
| setModelAttributes(m) | `AAt(a,m)`, virtual `bpZ` | |
| getRandomModelBox / setTextureOffset(name,x,y) / getTextureOffset | `EPk` / `Fdp(a,name,x,y)` / `DfT(a,name)` (TextureOffset fields `dI9`,`dI8`) | |

ModelBiped `OB` fields: `lA` head, `Ea` headwear, `k_` body, `gM` rightArm, `f3` leftArm, `mD` rightLeg, `nc` leftLeg, `bmB` isSneak, `a2N`/`a6t` arm poses. Virtual `cD2(scale, side)` is postRenderArm (STAGE Mo' Bends; H).

### ModelRenderer: JS class `M2` (all SM exact lines + HARN)

| Java | JS |
| --- | --- |
| textureWidth/Height | `bdO` / `bby` |
| textureOffsetX/Y | `bH9` / `bH$` |
| rotationPointX/Y/Z | `cD` / `bs` / `bA` |
| rotateAngleX/Y/Z (radians) | `A` / `bb` / `bX` |
| offsetX/Y/Z | `bot` / `bcS` / `bcR` |
| compiled / displayList | `clh` / `bWg` |
| mirror / showModel / isHidden | `i$` / `eT` / `cIT` (ints) |
| cubeList / childModels | `a6Y` (ArrayList) / `OS` (ArrayList or null) |
| boxName / baseModel | `dI3` / `ddy` |
| `<init>(model, String name)` | `DGf(a, model, name)`: sets tex 64×32, `showModel=1`, `cubeList`, `baseModel`, **adds itself to `model.cJ9`**, then `FR(a, model.vI, model.vd)` |
| `<init>(model)` / `(model,u,v)` | `DxI(a,m)` / `BZS(a,m,u,v)`; allocation helpers `H7(m)`, `BX(m,u,v)` |
| addChild | `HV(parent, child)` (creates `OS` lazily) |
| setTextureOffset (returns this) | `DW(a,u,v)` |
| setTextureSize (returns this) | `FR(a,w,h)` |
| addBox(x,y,z,w,h,d) (returns this) | `CH(a,x,y,z,w,h,d)` builds `F2d(a,a.bH9,a.bH$,x,y,z,w,h,d,0)`, using `a.i$` as mirror |
| addBox(...,float scaleFactor) (void) | `B$(a,x,y,z,w,h,d,delta)` |
| addBox(...,boolean mirrored) | Inlined into its callers. Equivalent: `Y(a.a6Y, Hrq(a,a.bH9,a.bH$,x,y,z,w,h,d,delta,mirror)); a.clh=0;` |
| addBox(String part,...) | `FaV(a,name,x,y,z,w,h,d)`, which uses `model.getTextureOffset(boxName+"."+part)` |
| setRotationPoint | `BQ(a,x,y,z)` |
| render(scale) | `E7Q(a,scale)` |
| renderWithRotation(scale) | `Eu3(a,scale)` |
| postRender(scale) | `FFZ(a,scale)` |
| compileDisplayList(scale) | `F$t(a,scale)`: `F6T()` allocates a list, `Clc(list,4864)` starts it, each quad is drawn, `ELo()` ends it, then `clh=1` |

`E7Q` does the following:
- It returns if `cIT` is set or `eT` is 0. It compiles the part if `!clh`, then translates by the offset.
- If all angles are 0, it translates by `rotationPoint*scale` (when that is non-zero), calls the list and renders the children.
- Otherwise it pushes, translates, calls `GoB(rx,ry,rz)` (rotateZYXRad: Z, then Y, then X), calls the list, renders the children and pops.
- Children are rendered with **direct `E7Q(child,scale)` calls**, not virtual calls, so overriding a `render` method on an instance has no effect on children.
- HARN checked the translation and rotation of the result.

Live patches of `E7Q`, `Eu3` and `FFZ` (do not anchor on these texts):
- Mo' Bends prepends `if(a.$mb!==undefined){JasprMoBendsBridge.render(a,b);return;}`.
- Gore replaced `Dle(f)` with `JasprGoreDraw(a,b,f)`. Gore takes over only parts owned (`ddy`) by the mob model it is currently treating; other parts take the native path (`gore-runtime.js drawPart`).

### ModelBox `DZG`, TexturedQuad `Bgq`, PositionTextureVertex `Xb`

| Java | JS |
| --- | --- |
| ModelBox(renderer,u,v,x,y,z,dx,dy,dz,delta) | `G4b(box,...)` (uses `renderer.i$`); helper `F2d(r,u,v,x,y,z,dx,dy,dz,delta)` |
| ModelBox(...,delta, mirror) | `Ehl(box,...,mirror)`; helper `Hrq(r,u,v,x,y,z,dx,dy,dz,delta,mirror)` |
| posX1..posZ2 | `dsk dsh dse` / `dsl dsi dsf` (not inflated) |
| vertexPositions[8] / quadList[6] / boxName | `dSQ` / `a4t` / `eje` |
| Quad order | 0 = +X (east, the box's "left" face), 1 = −X, 2 = −Y (top in model space), 3 = +Y, 4 = −Z (front), 5 = +Z (back). UVs are the vanilla layout. HARN: front face UV is `(u+d .. u+d+w, v+d .. v+d+h)`. |
| TexturedQuad(verts,u1,v1,u2,v2,texW,texH) | `Dlm(q,...)`; helper `A5Y(T(Xb,[v0,v1,v2,v3]),u1,v1,u2,v2,tw,th)`; fields `a4R` verts, `emm` count, `ecB` invertNormal |
| PositionTextureVertex(x,y,z,u,v) | `G2X`; helper `AMs(x,y,z,u,v)`; `Kj` (Vec3d `ET`: `bh`,`bq`,`bi`), `cDP`,`cDQ` (UV normalized 0..1); `AY_(v,u,v)` copies with new UV |

**How Mo' Bends draws.** It creates real engine `M2` objects, either through `DGf` (registered in the model's `cJ9`) or by field-initialising unregistered ones. It adds engine `ModelBox`es (`F2d`) or builds its own `DZG` with custom quads (`Ehl` with zero size, then replaces `a4t` with `A5Y`/`AMs` quads; this is `mutatedBox`). It compiles each part with the engine's `F$t` and draws with `Dle(r.bWg)` (or `JasprGoreDraw`), applying its own transforms with GL calls (`DPm`, `Gc9`, `FWM`, `EQk(Matrix4f)`). For loose meshes (the wolf mouth) it compiles display lists by hand (`F6T`, `Clc(list,4864)`, tessellator, `ELo`). Trails are direct tessellator quads. (`client-mods\mobends\mobends-teavm.js`; H)

**Cheapest reliable way to build a new model.** Use the engine objects directly; nothing has to be patched (HARN-verified):

```js
var m = new DQ(); Gs(m); m.vI = 128; m.vd = 128;          // ModelBase holder, texture size first
var body = BX(m, 0, 0); BQ(body, 0, 2, 0);                // new ModelRenderer(model,u,v); setRotationPoint
CH(body, -8, -12, -4, 16, 12, 8);                         // addBox (int dims; delta 0; mirror = body.i$)
B$(body, -8, -12, -4, 16, 12, 8, 0.25);                   // addBox with inflate
var arm = BX(m, 64, 0); arm.i$ = 1;                       // mirror BEFORE addBox
CH(arm, -2, -2, -2, 4, 20, 4); BQ(arm, 9, -10, 0); HV(body, arm);
// per frame (inside a render hook, GL already positioned/scaled like a vanilla model):
body.A = pitchRad; arm.A = swing; arm.bX = roll;          // radians
E7Q(body, 0.0625);                                        // compiles once, then callList; recurses children
```

- Mutant Beasts' `JointModelRenderer` is a parent `M2` with one inner child `M2`. Emulate it with two parts.
- `ScalableModelRenderer` is `render` wrapped in push / `FWM(s,s,s)` / pop. Because children are rendered by direct `E7Q` calls, a nested scalable part needs its own JS traversal, as Mo' Bends' `renderPart` does: `if(!r.clh)F$t(r,s); Eu0(); DPm(...); GoB(...); FWM(k,k,k); Dle(r.bWg); for children...; ECi();`.
- Do not recompile often: `F$t` allocates a new list each time and the old one is never freed.

---

## 3. Rendering pipeline

The call chain:
- Minecraft.runGameLoop `Dz6`
- EntityRenderer
- RenderGlobal.renderEntities `DbP(a=RenderGlobal, b=viewEntity, c=camera, d=partialTicks)`
- RenderManager.renderEntityStatic `Ghk(a=RM, b=entity, c=pt, d=flag)`: interpolates `lastTickPos`→`pos`, sets the lightmap (`G0W`) and color, subtracts the camera position (`a.bP0/bP1/bPZ`)
- RenderManager.doRenderEntity `Gxv(a=RM, b=entity, c=x, d=y, e=z, f=yaw, g=pt, h=flag)` (MCP "renderEntity"):
  - `j=EPj(a,b)` getEntityRenderObject
  - `D_f(j,outlines)`
  - **`j.jV(b,c,d,e,f,g)` doRender**
  - `EnF(j,...)` doRenderShadowAndFire
  - `FG_` debug box

All SM+BODY, H. Live hooks in `Gxv`: Mo' Bends `pre(b,j,g,a)`/`post` and gore `begin`/`end` around `jV`.

### Render `FV` (fields `ja` renderManager, `Cb` shadowSize, `b$m` shadowOpaque, `gL` renderOutlines)

| Java | JS |
| --- | --- |
| doRender | virtual `jV`; base `DqP(a,b,x,y,z,yaw,pt)` = `if(!gL) a.cNU(b,x,y,z)` (name tag) |
| shouldRender | virtual `b2r` (`FY$`) |
| renderName / canRenderName | virtual `cNU` / `cTS`; RenderLivingBase overrides via `egj`→`Co$` and `dfo`→`F0w`; RenderLiving `dfo`→`DYd` |
| getEntityTexture | virtual `eI` |
| bindEntityTexture | `Ew0(a,e)` |
| bindTexture(RL) | `FTd(a,rl)`, which is `D17(a.ja.a43, rl)` |
| renderLivingLabel | `FM7(a,e,str,x,y,z,maxDist)`; height comes from `e.bZ+0.5` |
| doRenderShadowAndFire | `EnF(a,e,x,y,z,yaw,pt)`: shadow if option `KY.t8` and `Cb>0`; radius `Cb`; fire `DSQ` scales by entity width `bI` and height `bZ` |
| constructor | `B68(a,rm)` |

### RenderLivingBase `Y4` (`iK` mainModel, `cHT` layerRenderers, `cub` renderMarker); RenderLiving `D$`

| Java | JS (virtual name → impl) | Notes |
| --- | --- | --- |
| ctor(rm, model, shadow) | `B7Q(a,rm,model,shadow)` | |
| addLayer | `C9g(a,layer)` (= `Y(a.cHT,layer)`) | |
| getMainModel | `dRJ`→`C$3` | |
| interpolateRotation(prev,cur,pt) | `EUK(a,prev,cur,pt)` | |
| **doRender** | **`DWR(a,b,x,y,z,yaw,pt)`**; RenderLiving `jV`→`F61`→`RC`→`CIl` = `DWR` then `F0M` renderLeash | |
| renderLivingAt | `dqK`→`DOK` (`DPm(x,y,z)`) | |
| handleRotationFloat | `b9U`→`Gjk` (`ticksExisted+pt`) | |
| rotateCorpse/applyRotations | `a95`→`ECq(a,e,age,yaw,pt)`: `Gc9(180-yaw,0,1,0)`, death tilt from `uS`, Dinnerbone | perf stage patched the name check inside |
| getDeathMaxRotation | `cZf`→`E0J` (90) | |
| prepareScale | `DVF(a,e,pt)`: `FWM(-1,-1,1)`; `a.tp(e,pt)`; `DPm(0,-1.501,0)`; returns 0.0625 | |
| preRenderCallback | `tp`→`EgN` (empty) | |
| renderModel | `enx`→`CoL` (visibility, `bindEntityTexture`, `model.ha(...)`, translucent invisible) | |
| setDoRenderBrightness / unsetBrightness | `Dl3(a,e,pt)` (hurt/death red, `getColorMultiplier` virtual `ebL`) / `EdA(a)` | |
| renderLayers | `CM$(a,e,ls,lsa,pt,age,yaw,pitch,scale)`: each layer `l.pq()` (shouldCombineTextures), then `l.no(e,ls,lsa,pt,age,yaw,pitch,scale)` (doRenderLayer) | |
| getSwingProgress | `Dpe` → `C3W(entity,pt)` | |

**DWR state map**:

| State | Code | Java step |
| --- | --- | --- |
| 1 | `Eu0` | push |
| 2 | `F1Q` | disableCull |
| 3–5 | `h.v5`/`h.b$r`/`h.ww` | model swingProgress, riding, child |
| 6–8 | | body and head yaw |
| 17 | `a.dqK(b,c,d,e)` | renderLivingAt |
| 18 | `a.b9U` | ageInTicks |
| 19 | `a.a95` | rotateCorpse |
| 20 | `DVF` | prepareScale |
| 21/25 | | limb swing |
| 26 | `D6M` | enableAlpha |
| 27 | `h.Lo` | setLivingAnimations |
| 28 | `h.i3` | setRotationAngles |
| 29–30 | `Dl3`, `a.enx` | brightness, renderModel |
| 33 | `EFX(1)` | depthMask(true) |
| 36/38 | `CM$` | layers |
| 40 | `ET8` | disableRescaleNormal |
| 11–15 | `GnI(33985)`; `CQ6`; `GnI(33984)`; `Ggy`; `ECi` | restore |
| **16** | **`DqP(a,b,c,d,e,f,g)`** | Render.doRender (name tag), then return |

The only live patch in `DWR` is the tank stage in state 10. The Big Mobs plan (not deployed) inserts after `a.dqK(b,c,d,e)` in state 17.

### RenderManager `BZt`

| Field | JS |
| --- | --- |
| entityRenderMap (Class→Render) | `dg` |
| skinMap / playerRenderer | `bfw` / `bkY` |
| textRenderer | `dZE` |
| renderPosX/Y/Z | `bP0/bP1/bPZ` (camera) |
| renderEngine (TextureManager) | `a43` |
| worldObj | `a5Y` |
| renderViewEntity / pointedEntity | `a18` / `cCX` |
| playerViewY / playerViewX | `v9` / `a1G` |
| options | `KY` |
| viewerPosX/Y/Z | `bX0/bXZ/bX1` |
| renderOutlines / renderShadow / debugBoundingBox | `dRS` / `cmy` / `cli` |

| Method | JS |
| --- | --- |
| getEntityRenderObject | `EPj(a,e)` (players by skin type, else `DIa(a, BW(e))`) |
| getEntityClassRenderObject | `DIa` (walks superclasses, caches) |
| setRenderPosition | `BD9` |
| cacheActiveRenderInfo | `CUo` |
| shouldRender | `GvD`. Two live stages patch it: the video stage culls non-players beyond the "Entity distance" setting (`JasprVideoCull`, 32–192 m), and the perf stage adds occlusion (`JasprPerfEntityHidden`). Then `render.b2r` checks the frustum against the entity's render bounding box. |

The map key is a `java.lang.Class`: `E(SomeJsClass)` or `BW(obj)`. Registering a whole new Render for a class is `EDK(rm.dg, E(Cls), render)`, but mutants are vanilla classes, so the decision must be per entity (see §12).

### Layers

`RenderLivingBase.cHT` holds objects with `pq()` and `no(...)`. A plain JS object works: Mo' Bends' `wolfMiscLayer()` is pushed with `Y(renderer.cHT, layer)` and removed with `ECM` (H). Vanilla layers of a biped renderer call `renderer.dRJ()` model parts (armor, held item), so they are wrong for a replacement model. A full replacement skips them (§12).

---

## 4. GlStateManager (`net.lax1dude.eaglercraft.opengl.GlStateManager`) and tessellator

All SM (vendor line = map line − 1) + BODY. Every function starts with `C7()` (clinit) and is resumable in form but never suspends.

| Java | JS | Notes |
| --- | --- | --- |
| pushMatrix / popMatrix | `Eu0()` / `ECi()` | Stack overflow/underflow only logs |
| translate(f) / translate(d) | `DPm(x,y,z)` / `GkS(x,y,z)` | |
| rotate(deg,x,y,z) | `Gc9(deg,x,y,z)` | Fast paths for unit axes |
| rotateZYXRad(x,y,z) | `GoB(x,y,z)` | Radians, Z then Y then X; used by ModelRenderer |
| scale(f) | `FWM(x,y,z)` | There is no separate scale(double) function; use FWM |
| multMatrix(Matrix4f) / (float[]) | `EQk(m)` / `DEZ(arr)` | Matrix4f is class `Kn`. Column fields: `h_ h$ ia g4` (m00..m03), `h7 h9 h8 g3` (m10..m13), `h5 hy h6 gy` (m20..m23), `lB lD lC jU` (m30..m33) |
| matrixMode / loadIdentity | `DSz(mode)` / `Cds()` | 5888 modelview, 5889 projection, 5890 texture (per-unit texture stacks exist; creeper-charge scrolling works) |
| color(r,g,b,a) / color(r,g,b) | `CFh` / `FU7` | |
| enableBlend / disableBlend | `CyM()` / `CTP()` | |
| blendFunc / tryBlendFuncSeparate | `Fb_(src,dst)` / `B$p(s,d,sa,da)` | Ints only: 770/771 alpha, 1/1 additive, 768 SRC_COLOR |
| enableAlpha / disableAlpha | `D6M()` / `Db2()` | |
| alphaFunc(func,ref) | `DQU` | **Only 516 (GL_GREATER) is accepted; any other value throws** |
| depthMask / depthFunc | `EFX(bool)` / `CcT(func)` | depthFunc takes vanilla constants (515 LEQUAL, 514 EQUAL) and remaps them internally |
| enableDepth / disableDepth | `DVf()` / `C70()` | |
| enableCull / disableCull / cullFace | `Ggy()` / `F1Q()` / `CI3(mode)` | |
| enableLighting / disableLighting | `D75()` / `DFk()` | |
| enableColorMaterial / disableColorMaterial | `ElS()` / `Fpb()` | |
| enableRescaleNormal / disableRescaleNormal / shadeModel | `CF0()` / `ET8()` / `Gxj(m)` | **No-ops** |
| enableTexture2D / disableTexture2D | `CQ6()` / `DCQ()` | For the active unit |
| setActiveTexture | `GnI(33984 or 33985)` | |
| bindTexture(int glName) | `FUe(id)` | |
| callList | `Dle(list)` | Gore replaced it inside E7Q only |
| enable/disable polygon offset, doPolygonOffset | `D2N()` / `Dmr()` / `F_d(f,u)` | |
| fog | `FdH()` / `GB$()` | |
| setShaderBlendSrc / Add, enable/disable add | `DOD` / `GFe` / `GEE()` / `FQl()` | Eaglercraft's hurt tint |
| OpenGlHelper.setLightmapTextureCoords | `G0W(unit,x,y)` | The unit argument is ignored and unit 1 is used. Full-bright is `G0W(33985,240,240)`. Constants `defaultTexUnit`=33984 and `lightmapTexUnit`=33985 are inlined. |

**GL state globals** (for save and restore; gore `saveState` and Mo' Bends `glState` are templates):

| Global | Meaning |
| --- | --- |
| `KrH` | matrix mode |
| `HKD`, `HKM.data[HKD]` | modelview stack index and current matrix |
| `HEn.data[u]` | bound texture per unit |
| `HEo` | active unit (0/1) |
| `Krr.data[u]` | texture2D enabled per unit |
| `Kri` | lighting |
| `Krh` | color material |
| `HHT` | cull |
| `HHV` | blend |
| `Krc`/`Krd` | blend src/dst (packed `x|xAlpha<<16`) |
| `HKI..HKL` | color |
| `KrK.data[1]`/`KrL.data[1]` | lightmap coordinates |
| `HHR` | depth test |
| `KqX` | depth mask |
| `KqW` | depth func |
| `Krf` | alpha test |
| `Krg` | alpha ref |
| `HEf` | WebGL context |

Mo' Bends unwinds a leaked matrix depth with `if(KrH===5888&&HKD>depth)HKD=depth`.

**Tessellator / WorldRenderer (BufferBuilder)**

| Java | JS |
| --- | --- |
| Tessellator.getInstance / getBuffer | `GdM()` / `t.dy` |
| draw | `FE$(t)` |
| begin(mode, fmt) | `Ep0(buf,7,fmt)` |
| pos(d,d,d) | `CUb(buf,x,y,z)` |
| tex(u,v) | `EpJ(buf,u,v)` |
| color(f×4) / color(i×4) | `Eip(buf,r,g,b,a)` / `GzJ` |
| normal(x,y,z) | `Gu1(buf,x,y,z)` |
| putNormal | `FR7(buf,x,y,z)` (sets the last 4 vertices, correct packing) |
| lightmap(sky,block) | `Do_(buf,a,b)` |
| endVertex | `E74(buf)` |
| setTranslation | `EMC` |

**`normal()` caveat**: it computes `(int)x*127`, truncating each component **before** scaling, so only axis-aligned unit normals survive. Use `FR7` for arbitrary normals.

Vertex formats (clinit `C5()`; from the `Hnn` assignment order):

| Global | Format |
| --- | --- |
| `Lnq` | BLOCK |
| `Lof` | ITEM |
| **`Lq0`** | OLDMODEL_POSITION_TEX_NORMAL (what ModelBox, gore and waypoints use) |
| `Lou` | PARTICLE_POSITION_TEX_COLOR_LMAP |
| `HLm` | POSITION |
| **`HLn`** | POSITION_COLOR (Mo' Bends trails) |
| `HLo` | POSITION_TEX |
| `Lq1` | POSITION_NORMAL |
| `HE4` | POSITION_TEX_COLOR |
| `Lq2` | POSITION_TEX_NORMAL |
| `Lq3` | POSITION_TEX_LMAP_COLOR |
| `Lq4` | POSITION_TEX_COLOR_NORMAL |

Display list by hand: `var l=F6T(); Clc(l,4864); ...begin, vertices, FE$(t)...; ELo(); later Dle(l)` (Mo' Bends `compileMesh`, H).

---

## 5. Textures, ResourceLocation, EPK

| Item | JS | Evidence / conf. |
| --- | --- | --- |
| ResourceLocation class | `Bb`: `m2` domain, `iX` path, **`Rj` cachedPointer, `a3s` cachedPointerType (1 = texture loaded)** | BODY+HARN H |
| new ResourceLocation(String) | `var rl=new Bb; Gp9(rl, $rt_str("textures/entity/x.png"));`. The domain defaults to `minecraft`; domain and path are lower-cased. | STAGE armory/nbt-skin + HARN H |
| TextureManager | `HEH.bE` (also `renderManager.a43`); field `blH` = mapTextureObjects | BODY (startGame `FEH`) H |
| bindTexture(RL) | `D17(tm, rl)`. If `rl.a3s===1` it binds `rl.Rj.cn0()` (getGlTextureId) via `Fik`. Otherwise it creates a SimpleTexture (`Bkl`), calls `GC3` loadTexture (**async on the first image decode**) and caches the result on the RL. | BODY H |
| Render-level bind | `FTd(renderer, rl)` | STAGE H |
| loadTexture(rl, ITextureObject) | `GC3(tm, rl, tex)` | SM H |
| getDynamicTextureLocation(name, DynamicTexture) | `EpG(tm, $rt_str(name), dyn)` returns an RL "dynamic/name_n", already loaded | BODY M |
| DynamicTexture | `var t=new YW; Fl9(t,w,h); t.a5e.data[i]=ARGB|0; Egf(t);` (upload) `; FUe(FST(t));` (bind GL id) | STAGE Mo' Bends wolf_misc H |
| Atlas | `HEN` = TextureMap.LOCATION_BLOCKS_TEXTURE (clinit `Lp()`); `HEH.A7` = textureMapBlocks | BODY M |

**Caching pattern.** Build each RL once and keep it (armory `JasprArmory.cached/store`). The engine caches the loaded texture on the RL object itself, so the second and later binds are synchronous map-free calls.

**EPK lookup.**
- The DefaultResourcePack reads `EagRuntime.getResourceStream("/assets/"+domain+"/"+path)`, which becomes `PlatformAssets.assets.get("assets/minecraft/textures/...")`.
- The live `site\assets.epk` (v2, gzip, 7954 entries) stores entries **with** the `assets/` prefix, e.g. `assets/minecraft/textures/entity/zombie/zombie.png`. Builders detect the prefix (`merge-apocalypse-assets.cjs`).
- EPK format notes are in that script and in `build-armory-pack.cjs`.

**New namespaces do NOT work** with the built-in pack (H).
- The deployed `DefaultResourcePack` domains are `ImmutableSet.of("minecraft","eagler")` (`DIl`: `HLg=HnQ(C(1028),C(1029))`). The vendor copy's `JasperGeneratedContent` domains are a JasperLoader edit and are not in the bundle.
- `SimpleReloadableResourceManager.getResource` throws FileNotFoundException for any other domain. The result is a "Failed to load texture" warning and a missing texture.
- The EPK already contains `assets/somanyenchantments/...` and `assets/bettercombatmod/...`. They are dead weight: the BetterCombat sounds were re-registered under `minecraft` as `jaspr.bettercombat.*`.
- **Convention: put everything under `minecraft`**, e.g. `assets/minecraft/textures/entity/jaspr_mutants/mutant_zombie.png`, referenced as RL `textures/entity/jaspr_mutants/mutant_zombie.png`. A user resource pack could add a domain, but the stage cannot rely on that.

---

## 6. Entities on the client

### Entity `Eg` (field order matches Entity.java; all cross-checked with Mo' Bends, gore, DS and tank; H)

| Java | JS | Java | JS |
| --- | --- | --- | --- |
| entityId | `cu` | world | `a` |
| ridingEntity / riddenByEntities | `fS` / `a1k` | prevPosX/Y/Z | `dn` / `d9` / `dv` |
| posX/Y/Z | `b` / `f` / `c` | motionX/Y/Z | `s` / `p` / `t` |
| rotationYaw / rotationPitch | `C` / `bd` | prevRotationYaw / prevRotationPitch | `cy` / `c2` |
| boundingBox (AABB `DN`: minX `ct`, minY `bv`, minZ `cz`, maxX `cH`, maxY `cl`, maxZ `cK`) | `bc` | onGround | `bQ` |
| isDead | `ed` | width / height | `bI` / `bZ` |
| prevDistanceWalkedModified / distanceWalkedModified | `UA` / `Iy` | lastTickPosX/Y/Z | `fj` / `e2` / `fk` |
| stepHeight | `r5` | rand | `h` |
| ticksExisted | `cv` | inWater | `e1` |
| hurtResistantTime | `hx` | dataManager | `y` |
| serverPosX/Y/Z (long) | `cHl` `cHi` `cHj` | ignoreFrustumCheck | `che` |
| dimension | `iE` | entityUniqueID | `fY` |

Methods:

| Method | JS |
| --- | --- |
| setSize(w,h) | `FET(e,w,h)`. Rebuilds `bc`; on the client (`world.r`=isRemote) it never moves the entity. |
| isInvisible | `DfJ(e)` |
| getFlag(i) | `EuW(e,i)` |
| isSneaking | `e.q1()` |
| isInvisibleToPlayer | `e.cLt(p)` |
| isBurning | `e.bHM()` |
| getBrightnessForRender | `Ei5(e)` (also virtual `be9`) |
| isRiding / getRidingEntity | `E9Z(e)` / `CqZ(e)` |
| getLookVec | `Dyw(e)` |
| getParts | `e.dTG()` |
| setPosition | `e.RW(x,y,z)` |
| setPositionAndRotation | `FnF` |
| setUniqueId | `F63` |
| **handleStatusUpdate(byte)** | virtual **`q2`** (Entity base `CKF` is empty) |
| notifyDataManagerChange | virtual `BF` |
| getName | virtual `b1` |

### EntityLivingBase `Co` (H)

| Java | JS |
| --- | --- |
| isSwingInProgress / swingingHand / swingProgressInt | `G1` / `crX` / `a2f` |
| **hurtTime** / maxHurtTime / attackedAtYaw / **deathTime** | `o2` / `bkB` / `FI` / `uS` |
| prevSwingProgress / swingProgress | `dY8` / `bYC` |
| prevLimbSwingAmount / limbSwingAmount / limbSwing | `qi` / `hp` / `CE` |
| renderYawOffset / prevRenderYawOffset | `cZ` / `s1` |
| rotationYawHead / prevRotationYawHead | `gN` / `zM` |
| activeItemStack / useCount / ticksElytraFlying | `l0` / `wS` / `bwP` |

| Method | JS |
| --- | --- |
| getHealth | `ENU(e)` (data key `Ktc` Float `.fB`) |
| getMaxHealth | `Crp(e)` |
| getSwingProgress(pt) | `C3W(e,pt)` |
| isChild | virtual `bV5` |
| held item main / off | `EZ5(e)` / `EjD(e)` |

Other classes: EntityLiving `Gj`, EntityPlayer `Cb`, AbstractClientPlayer `Vf`, EntityArmorStand `HC`, zombie `Iw`, skeleton `OF`, pig zombie `PP`, spider `SN`, squid `ZL`, wolf `KF`, arrow `Kx`. Zombie `isArmsRaised` = `Dtz(e)` (key `KBi`, Boolean `.br`).

### Data manager (EntityDataManager; SM+BODY H)

| Item | JS |
| --- | --- |
| Data manager | `e.y` (fields `a2m` IntObjectHashMap of entries, `b9c` entity, `a6k` dirty) |
| get(key) | `E24(dm,key)` returns `.a3X` |
| Raw entry by numeric id | `Bd7(e.y.a2m, id)` returns a DataEntry (`b1U` key with id `b0H`, `a3X` value) or null |
| Boxed value fields | Boolean `.br`, Integer `.bn`, Float `.fB`, Byte `.eg`, String = Java String (`$rt_ustr`) |
| createKey | `C0(id, serializer)` |

Key ids are **hard-coded** in Eaglercraft: Entity 0–5, Living 6–10, EntityLiving 11, zombie 12–14. They match 1.12.2 for the mutant base mobs. Known wrong ids are in memory `eaglercraft-client-quirks.md`.

### World

| Item | JS |
| --- | --- |
| Client world | `HEH.X` (WorldClient). World class `AQY`. |
| loadedEntityList | `w.gw` (ArrayList; DS iterates `w.gw.qN.data[0..g)`) |
| playerEntities | `w.e5` |
| entitiesById | `w.a27` |
| isRemote | `w.r` |
| rand | `w.R` |
| scoreboard | `w.k3` |
| getEntityByID | `FyG(wc,id)` (returns the player for its own id) or virtual `w.baK(id)` (STAGE Mo' Bends) |
| addEntityToWorld | `E05(wc,id,e)` |
| playSound(x,y,z,SoundEvent,cat,vol,pitch,delay) | `D2I(wc,...)` |

**EntityList.**
- Registry `KsX` (RegistryNamespaced, field_191308_b); `WY(KsX,id)` is getObjectById, which returns a `java.lang.Class`.
- Class→constructor map `KUw`; `E$P(cls,world)` creates the entity through `ctor.du(world)` (Eaglercraft has no reflection). Clinit `I$()`.
- To map an **unknown spawn type to a vanilla class**, patch `FrD` at `case 1:I$();if(B()){break _;}j=WY(KsX,h);$p=2;` → `j=WY(KsX,JasprMutantsType(h))`. The anchor is unique (M; untested).
- A Paper server only sends vanilla type ids, so this is unnecessary if mutants are spawned as vanilla mobs.
- **Network type id → JS entity class.** From `EntityList.init` `GeH`, `D6a(id, name, E(Cls), factory, legacyName)`; use these for `instanceof` (H):

| id | name | JS class | id | name | JS class |
| --- | --- | --- | --- | --- | --- |
| 1 | item | `GC` | 2 | xp_orb | `Jo` |
| 3 | area_effect_cloud | `JR` | 4 | elder_guardian | `Yj` |
| 5 | wither_skeleton | `ACG` | 6 | stray | `ACP` |
| 10 | arrow | `Np` | 11 | snowball | `TR` |
| 12 | fireball | `R0` | 13 | small_fireball | `Nq` |
| 18 | item_frame | `Ih` | 20 | tnt | `MG` |
| 21 | falling_block | `N1` | 23 | husk | `ACq` |
| 27 | zombie_villager | `OW` | 30 | armor_stand | `HC` |
| 50 | creeper | `Kw` | 51 | skeleton | `OF` |
| 52 | spider | `SN` | 53 | giant | `ALC` |
| 54 | zombie | `Iw` | 55 | slime | `Rz` |
| 56 | ghast | `TD` | 57 | zombie_pigman | `PP` |
| 58 | enderman | `Nd` | 59 | cave_spider | `ABa` |
| 60 | silverfish | `YT` | 61 | blaze | `Tx` |
| 64 | wither | `N$` | 66 | witch | `Qh` |
| 67 | endermite | `Z1` | 68 | guardian | `Tm` |
| 69 | shulker | `O1` | 90 | pig | `LM` |
| 91 | sheep | `R4` | 92 | cow | `Us` |
| 93 | chicken | `OA` | 94 | squid | `ZL` |
| 95 | wolf | `KF` | 97 | snowman | `ACQ` |
| 99 | villager_golem | `Ki` | 100 | horse | `Mi` |
| 102 | polar_bear | `Wf` | 120 | villager | `IW` |

---

## 7. Network

Handlers are compiled **into the packet classes** as `fn(a=packet, b=NetHandlerPlayClient)`. NetHandlerPlayClient is `A1L`: `qf` netManager, `cb` Minecraft, `bk` WorldClient, `bdo` eaglerMessageController. The player's connection is `HEH.v.d_` (STAGE DS/wide).

| Packet / handler | JS | Behaviour (H unless noted) |
| --- | --- | --- |
| handleSpawnMob | `FrD(pkt,h)` | Fields: `bwR` entityId, `cnr` uuid, `b0q` type, `coC/coB/coA` x/y/z, `b6z/b6A/b6B` velocity/8000, `clS`/`cgK` yaw/pitch (×360/256), `ck4` headPitch → `cZ` and `gN`, `dWc` data entries. **Unknown type**: `E$P` returns null, the log says "Skipping Entity with id {}" (`C(4603)`), no entity is created, and later packets for that id are dropped because `FyG` returns null. |
| handleEntityMetadata | `GqL(pkt,h)`: `FyG(h.bk, pkt.ca5)`, then `FZE(e.y, pkt.bP4)` | |
| EntityDataManager.setEntryValues | `FZE(dm,list)` | **Silently ignores ids the entity lacks.** **No type check**: it copies `.a3X` blindly, then calls `entity.BF(key)`. A wrong type breaks later getters (AreaEffectCloud's `.fB` of a Boolean gives NaN, which froze the tab). |
| handleEntityStatus | `FXy(pkt,h)`: `c=FyG(h.bk,pkt.cq1)`, `e=pkt.cp5`. 21 = guardian sound, 35 = totem, else **`case 6:c.q2(e);if(B()){break _;}return;`** | **Hook point for custom status bytes** (unique anchor). Unknown bytes fall through to `Entity.handleStatusUpdate`, which is empty for the base class, so vanilla ignores them safely. |
| handleCustomPayload | `Cyr(h,pkt)`: `pkt.S$` channel (Java String), `pkt.Wm` PacketBuffer | Live channels in case 0: `jaspr:inv`→2990, `JASPR|Revive`→100, `JASPR|World`→101, `jaspr:gear`→102, `jaspr:combat`→198, `jaspr:surround`→1963. Big Mobs plans `jaspr:scale`→2991. Original states are 0..61. Unknown channels reach `eaglerMessageController` (`Ghl(h.bdo,...)`). |
| handleJoinGame | `E8R` | The wide stage says hello at `$p=291;case 291:/*JW*/JasprWideHello(b);` after `MC|Brand` |
| handleSoundEffect / handleCustomSound | See §8 | |
| SPacketCustomPayload | channel `S$`, data `Wm` | |
| CPacketCustomPayload | `AKy`, ctor `BgN(pkt, channelJavaString, packetBuffer)` (max 32767 bytes) | |

PacketBuffer (`Iu`; wrapped ByteBuf `hH`; ctor `Lg(pb, byteBuf)`; `Fru()` = Unpooled.buffer):

| Direction | Functions |
| --- | --- |
| Read | `CRh(pb,max)` String, `EmC` VarInt, `CFL` VarLong, `E7Z` int, `CZl` byte, `DOh` unsigned byte, `DPz` boolean, `FSR` short, `Gg8` unsigned short, `CC$` long (BigInt), `E$K` float, `GxP` double, `DXh` byte[], `GfL` UUID, `D7i` BlockPos, `FRK(pb,E(EnumCls))` enum |
| Write | `FuF(pb,jstr)` String, `FgJ` VarInt, `FJj` int, `F4D` byte, `DW0` boolean, `GAd` float, `C9L` double, `Eq1` long, `FeX` byte[] |

All SM+BODY H. Each call needs `if(B()){break _;}` inside resumable code.

**Receiving in a stage.** Pattern (H, five live channels):
- In Cyr case 0, add `if($rt_ustr(b.S$)==="jaspr:mutants"){$p=3101;continue _;}`.
- Add a new state: `case 3101:$z=CRh(b.Wm,32767);if(B()){break _;}JasprMutantsBridge.receive($rt_ustr($z));return;`.
- An anchor that does not overlap other stages' replacement texts: `c=C(1771);d=b.S$;$p=1;case 1:`, which is original text. Replace it with `<test>c=C(1771);d=b.S$;$p=1;continue _;case 3101:...;return;case 1:`.
- Never put a new `case` between `$p=1;` and `case 1:`, because that breaks the fall-through.

**Sending.** Resumable function, copied from `JasprGearSend` (gear-teavm.js) and `JasprWideHello`:

```js
function JasprMutantsSend(a /*NetworkManager = HEH.v.d_.qf; skip if a.bkf*/, b /*JS string*/) {
  var c, d, e, $p = 0, $z;
  if (FX()) { var $T = Ds(); $p = $T.l(); e = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
    case 0: c = new AKy; d = new Iu; $p = 1;
    case 1: $z = Fru(); if (B()) break _; Lg(d, $z); e = $rt_str(b); $p = 2;
    case 2: $z = FuF(d, e); if (B()) break _; BgN(c, $rt_str("jaspr:mutants"), $z); $p = 3;
    case 3: a.wd(c); if (B()) break _; return;      // wd = NetworkManager.sendPacket (WebSocket: Gvs)
    default: FT();
  } }
  Ds().s(a, b, c, d, e, $p);
}
```

---

## 8. Sounds

| Item | JS / fact | Evidence |
| --- | --- | --- |
| SoundHandler | `HEH.fA` (sndManager `.Bw`) | BODY startGame + Cyr MC\|StopSound, H |
| playSound(ISound) / stopSound | `DFK(sh, sound)` → `Egn` / `C4J(sh, sound)` | SM H |
| PositionedSoundRecord | class `Ne`; ctor `BRt(rec, RL, category, vol, pitch, repeat(0/1), repeatDelay, attenuation, x, y, z)`. Fields `BV` RL, `yo` category, `u_` volume, `a61` pitch, `bfQ` repeat, `bva` delay, `br8` attenuation, `nX/n2/n4` position. Other ctors: `Cd0(rec, SoundEvent, ...)` (uses `ev.WL`); `Fam(rec, SoundEvent, cat, vol, pitch, x, y, z)` | SM+HARN H |
| AttenuationType | `WP()` clinit; `KWl` NONE, `KWi` LINEAR | BODY (`GJx` names NONE/LINEAR) H |
| SoundCategory | `Cx()` clinit; `Lnd` MASTER, `LnN` MUSIC, `K1p` RECORDS, `LnO` WEATHER, `KvZ` BLOCKS, `KxK` HOSTILE, `Ks5` NEUTRAL, `KuC` PLAYERS, `KAP` AMBIENT, `LnP` VOICE | BODY + STAGE DS H |
| World sound | `D2I(HEH.X, x, y, z, soundEvent, cat, vol, pitch, distanceDelay)`; SoundEvent `.WL` = name RL; SoundEvents clinit `Bs()` | SM H |

- **The playing path can suspend** (first decode), so play from resumable code. `JasprDSSoundStart` in the DS stage is the template: `d=new Bb;Gp9(d,$rt_str(name));WP();e=new Ne;BRt(e,d,cat,vol,pitch,rep,0,att,x,y,z);DFK(a.fA,e);if(B())break _;`.
- **Name resolution.**
  - `PositionedSound.createAccessor` looks up `SoundHandler.soundRegistry` by RL. That registry is built from every **registered domain's** `sounds.json`, i.e. `minecraft` and `eagler`, and is independent of the `SoundEvent.REGISTRY`.
  - **New event names only need entries in `assets/minecraft/sounds.json`** plus .ogg files under a registered domain. The file loader also goes through the resource manager.
  - Live convention (H): 157 events named `jaspr.dsurround.*` / `jaspr.bettercombat.*` with sounds `minecraft:jaspr/<mod>/<file>` → `assets/minecraft/sounds/jaspr/...ogg` (711 files). `sounds.json` is 140 KB with 706 events. Use `jaspr.mutants.<entity>.<sound>`.
- **Unknown names.**
  - `SoundManager.playSound` logs "Unable to play unknown soundEvent(1): <name>" and plays nothing. It does not crash.
  - The server's `SPacketCustomSound` (Bukkit `World/Player.playSound(loc, String, ...)`) builds `new PositionedSoundRecord(new ResourceLocation(name), ...)` (LINEAR), so **server-triggered mutant sounds need no client code**.
  - `SPacketSoundEffect` carries a SoundEvent *registry id*. Paper only sends vanilla ids; an unknown id would produce a null SoundEvent, so never fake it.
- Bad volume/pitch values are clamped (pitch 0.5–2, gain 0–1).

---

## 9. Particles

| Item | JS | Evidence |
| --- | --- | --- |
| ParticleManager | `HEH.it` (class `A9l`): `cNC` factories (Integer→IParticleFactory, method `gW(id, world, x, y, z, vx, vy, vz, int[])`), `bop` queue, `uP` fxLayers[4][2], `Vg` world, `Tl` emitters | SM+BODY H |
| spawnEffectParticle | `GlW(pm, id, x, y, z, vx, vy, vz, Bh(0))`. Returns the Particle or null and does `addEffect`. **Bypasses** the particle setting and the 32-block range. | SM H |
| RenderGlobal.spawnParticle | `CsB(HEH.fE, id, ignoreRange?1:0, x, y, z, vx, vy, vz, Bh(0))` → `F2g`; respects `gameSettings.particleSetting` and range | SM M |
| addEffect | `CIb(pm, particle)` | STAGE DS H |
| registerParticle | `FAj(pm, id, factory)` | SM H |
| emitParticleAtEntity | `D$s(pm, e, type)` | SM H |
| EnumParticleTypes ids | explode 0, largeexplode 1, hugeexplosion 2, fireworksSpark 3, bubble 4, splash 5, wake 6, suspended 7, depthsuspend 8, crit 9, magicCrit 10, smoke 11, largesmoke 12, spell 13, instantSpell 14, mobSpell 15, mobSpellAmbient 16, witchMagic 17, dripWater 18, dripLava 19, angryVillager 20, happyVillager 21, townaura 22, note 23, portal 24, enchantmenttable 25, flame 26, lava 27, footstep 28, cloud 29, reddust 30, snowballpoof 31, snowshovel 32, slime 33, heart 34, barrier 35, iconcrack 36 (2 params), blockcrack 37 (1), blockdust 38 (1), droplet 39, take 40, mobappearance 41, dragonbreath 42, endRod 43, damageIndicator 44, sweepAttack 45, fallingdust 46 (1), totem 47, spit 48 | source H |

**Particle `D_`** (SM+BODY H; harness construction blocked by log4j clinit).

| Kind | Java | JS |
| --- | --- | --- |
| Field | world | `kj` |
| Field | prevPos / pos | `kX iR kW` / `d1 db d0` |
| Field | motion | `bT b2 bU` |
| Field | boundingBox | `nj` |
| Field | isCollided / canCollide / isExpired | `v_` / `dsM` / `dk0` |
| Field | width / height | `b$A` / `b9J` |
| Field | rand | `dc` |
| Field | textureIndexX/Y | `bzV` / `cfG` |
| Field | age / maxAge | `ez` / `cT` |
| Field | scale / gravity | `er` / `II` |
| Field | red / green / blue / alpha | `eC ey eu zC` |
| Field | particleTexture (sprite) | `Vi` |
| Field | angle | `b0c` |
| Statics | interpPosX/Y/Z / cameraViewDir | `LoV/LoW/LoX` / `LoY` |
| Ctor | (world,x,y,z,vx,vy,vz) | `LAA(...)` → `BZl` (randomises motion like vanilla) |
| Ctor | protected (world,x,y,z) | `LAz` |
| Method | multiplyVelocity | `BdI` |
| Method | multipleParticleScaleBy | `FFf` |
| Method | setRBGColorF | `Tb(p,r,g,b)` |
| Method | setAlphaF | `G8G` |
| Method | setParticleTextureIndex | `IC(p,i)` (i%16, i/16) |
| Method | setParticleTexture | `Bi_` (layer 1 only) |
| Method | setExpired | `Gt` |
| Method | setSize | `YH` |
| Method | setPosition | `B8U` |
| Virtual | onUpdate | `dt` (base `GjX`) |
| Virtual | renderParticle | `qy(buf, entity, pt, rX, rZ, rYZ, rXY, rXZ)` (base `Evn`) |
| Virtual | getFXLayer | `G7` (0 = particles.png, 1 = block/item atlas, 3 = lit "self-drawing": `C7r` calls `qy` with no begin/draw) |
| Virtual | getBrightnessForRender | `a51` (perf stage caches `DXe`) |
| Virtual | move | `bD1(dx,dy,dz)` |
| Virtual | isTransparent | `cdh` |

**Emulating a custom particle class** (H, live DS damage pop-offs):
- Create a vanilla particle (`LAA`, or a factory such as `CTM(null,0,world,x,y,z,0,0,0,null)` = SweepAttack, layer 3).
- Set its fields.
- Override virtuals **per instance**: `p.dt=function(){...}`, `p.qy=function(b,e,pt,...){...}`, `p.G7=function(){return 3}`, `p.a51=function(){return 0xF000F0}`.
- Call `CIb(HEH.it, p)`.

Mutant Beasts' EndersoulParticle uses `particles.png` indexes 0–7 with a custom colour, scale curve and full-bright, so layer 0 is enough. SkullSpiritParticle uses a stitched atlas sprite: emulate it with a layer-3 particle that binds its own texture and draws a camera-facing quad in `qy`, saving and restoring state like `JasprDSRenderText`. Layers 0–2 are frustum-culled by the perf stage (`JasprPerfParticleVisible`).

---

## 10. Item rendering

| Route | How | Conf. |
| --- | --- | --- |
| Damage-band carriers | Pure resource pack. Item model `overrides` on a damageable carrier (stone_sword / stone_shovel bands; `scripts\trinket-art\catalog.cjs`). | H (live) |
| NBT predicate | `addPropertyOverride` is `DQE(item, rl, getter)`; getter is `{TM:function(stack,world,entity){return n;}}`. Installed from the Items clinit `F7x` after the registry fetch (`KR7`=COOKED_BEEF). The item then gets `hasCustomProperties`, so `EAO` evaluates the overrides. Reads use `GaK(stack, key)` (getSubCompound) and `F54(tag, key)` (getString). (`client-mods\nbt-skin-teavm.js`) | H (live) |
| JSON cuboid models | e.g. Portal Gun: 66 cuboids, 128² atlas (`apocalypse-pack\portal-gun-art.cjs`) | H (live) |
| **Custom 3D (TEISR)** | `RenderItem.renderItem(stack, model)` is `FVg`. If `model.cd1()` (isBuiltInRenderer) it calls `CFh(1,1,1,1)`, `BvO()` and `F5U(Log, stack)` (`Log` = TEISR.instance), which forwards to `Dr3(teisr, stack, 1.0)`, after `Eu0(); DPm(-0.5,-0.5,-0.5)`. A stage can hook `Dr3`/`F5U` at `case 0:` (unique anchor `case 0:c=1.0;$p=1;case 1:Dr3(a,b,c);`), test the stack (item + NBT id), and draw an engine ModelRenderer model with its own bound texture (resumable bind for first use), then return. The item needs an override model whose parent is `builtin/entity` (supported: `C(8637)`; vanilla `item/shield.json` uses it) and the display transforms in that JSON. | M (path verified, not exercised) |

`ItemRenderer` is `HEH.a66`; `renderItemSide` is `Ch0(ir, entity, stack, transform, leftHand)` (Mo' Bends). `RenderItem` is `HEH.u4`.

---

## 11. Other things a port needs

| Topic | Fact | Conf. |
| --- | --- | --- |
| Minecraft singleton | `HEH` (class `AGY`, getter `E32()`). Fields: `v` player, `X` world, `fE` renderGlobal, `AM` renderManager, `bE` textureManager, `u4` renderItem, `a66` itemRenderer, `it` particleManager, `fA` soundHandler, `fU` entityRenderer, `EG` blockRendererDispatcher, `A7` textureMapBlocks, `G` gameSettings (`lv` thirdPersonView, `t8` entity shadows), `J4` timer, `cp` isGamePaused, `buv` paused partial ticks, `cj` currentScreen, `da` ingameGUI, `bw` fontRenderer, `hi` renderViewEntity, `h4` objectMouseOver. All checked against field order and against `startGame` (`FEH`). | H |
| Partial ticks | Render value is `HEH.cp ? HEH.buv : HEH.J4.UM` (runGameLoop `Dz6`). Timer `Bqy`: `UM` renderPartialTicks, `cQf` elapsedTicks, `dTv` delta. The value is passed down as `DbP` arg `d`, `Ghk` arg `c`, `Gxv` arg `g` and `DWR` arg `g`. | H |
| Interpolation | `EUK(renderer, prev, cur, pt)` wraps ±180°. Position interpolation is done by `Ghk` before `jV`. Use `cy/C`, `c2/bd`, `s1/cZ`, `zM/gN`, `qi/hp`. | H |
| MathHelper | sin `D2_(x)`, cos `CiK(x)` (table `Lqp.data[(x*10430.3779296875|0)&65535]`, clinit `FG()`), sqrt `CqA`/`CuM`, wrapDegrees `D3g`. Random: `X(rand)` nextFloat; `C9()` Math.random | BODY H |
| Block rendering (thrown/held blocks) | `Ceq(HEH.EG, blockState, brightness)` = renderBlockBrightness (inlined; vanilla LayerHeldBlock `D7T` binds the atlas `FTd(r,HEN)` and lightmap `G0W(33985, Ei5(e)%65536, Ei5(e)/65536|0)` first). `AT2(disp, state)` = getModelForState. | M |
| Font | `HEH.AM.dZE`: `fr.ei2(jstr, x, y, color, shadow)` drawString, `CA(fr, jstr)` width, `fr.c6` height (DS stage) | M |
| Tick hook | `CHq` = Minecraft.runTick. Live: `case 0:JasprMoBendsBridge.tick();$p=1963;case 1963:JasprDSTick(a);if(B()){break _;}b=a.bTh;`. Not `Gq3` (updateDisplay, per frame). | H |
| Frame hook | `DbP` after `BD9(a.Pp,f,p,h);JasprGoreBridge.frame(a.d8,a.Pp);JasprMoBendsBridge.frame(d,a.Pp);` (RenderGlobal: `Pp` renderManager, `fd` Minecraft, `d8` world). The in-world overlay pass is `case 42:` (waypoints, gore pieces, dynamic lights). | H |
| World change | Compare `HEH.X` (or `player.a`) with the last seen value (Mo' Bends `lastWorld`). | H |
| Video Settings toggle | Rows are built by `JasprVideoBuild`: `case 4: e<30`, ids 944–947 then 948..973 in pairs; `case 6:if(j>973){h=null;` (Mo' Bends raised 972→973 for its id 973). Labels and actions come from the wrapped globals `JasprVideoLabel(id)` / `JasprVideoAction(id)`; set `JasprVideoRefresh=true` after a change. **All 30 slots are used**: a new toggle (e.g. 974) must also raise `e>=30`→`e>=32` and `j>973`→`j>975`. Persist with localStorage `jaspr.<feature>.v1` inside try/catch. | H |
| Diagnostics | Same-origin `fetch("/api/diagnostics/events", {method:"POST", credentials:"same-origin", headers:{"Content-Type":"application/json","X-Jaspergers-Client":"web-v1"}, body:JSON.stringify({events:[{event:"jaspercraft.<feature>.<name>", pageSessionId, at, details}]})})`. Only when `location.pathname` starts with `/jaspercraft/`; at most 12 per page; bounded details (counters, state names, error text ≤180 chars); no names, positions or credentials. Expose `$rt_globals.Jaspr<Feature>Diagnostics=Object.freeze({status(){...}})`. Events land in Jaspr.chat `events.jsonl`. | H (Mo' Bends) |
| Stage builder conventions | Fenced `/* JASPR_X_BEGIN */…END` module (ASCII). Each hook is `[function, anchor, replacement, count]`, applied only inside that function's text with exact counts. The builder checks that native names are still declared, that the result parses (`vm.Script`), and that `unpatch(build(x))===x` byte for byte. Read and write latin1. Output goes to `candidate/<stage>/`. Bump the `classes.js?v=` key in `client.html`. Install the new stage **last** and unpatch it before rebuilding older stages. Choose anchors from original (unpatched) text that do not overlap other stages' replacement text; then stages stay independent. | H |

---

## 12. Recommended hook plan: render chosen entity ids with a custom model, texture and scale

**Data flow** (server design is a later step; the client side is shown here):
- **Entity table.** The Paper plugin spawns a vanilla base mob and sends `jaspr:mutants` text messages: `v1 set <entityId> <kind> <scale100>`, `v1 del <id>`, and periodic `v1 table ...`. The Big Mobs `jaspr:scale` format and schedule are a proven model. The client keeps `Map<entityId, def>` and clears it when the world changes.
- **Animation triggers.**
  - Custom status bytes (e.g. 100–127) via NMS `world.broadcastEntityEffect(entity, (byte)n)`. They reach only trackers, are ordered with the movement packets, and vanilla ignores unknown bytes.
  - For data-carrying animations, use a `jaspr:mutants` message `anim <id> <animId> <param>`.
  - Per-entity animation state lives in a `WeakMap<entity, state>` or as properties `entity.$jm`, advanced from `entity.cv + pt`.
- **Sounds.** The server calls `playSound(loc, "jaspr.mutants.<...>", HOSTILE, v, p)`. That needs no client code; the assets go in `assets/minecraft/sounds.json` and `sounds/jaspr/mutants/*.ogg`.
- **Hitbox.** On a table change, call `FET(entity, w, h)` once, so mouse-over, fire, name tag height and frustum use the right size. Server reach is still feet-to-feet < 6 (Big Mobs finding). For models larger than the box, set `entity.che = 1` (ignoreFrustumCheck).

**Hook A: full model replacement at RenderLivingBase.doRender** (`DWR`; anchor unique; original text, untouched by any stage):

```text
anchor : case 0:$p=1;case 1:Eu0();if(B()){break _;}$p=2;case 2:F1Q();if(B()){break _;}h=a.iK;
replace: case 0:if(!JasprMutantsBridge.claims(a,b)){$p=1;continue _;}$p=3100;
         case 3100:JasprMutantsBind(a,b);if(B()){break _;}
         if(JasprMutantsBridge.draw(a,b,c,d,e,f,g)){$p=16;continue _;}$p=1;
         case 1:Eu0();if(B()){break _;}$p=2;case 2:F1Q();if(B()){break _;}h=a.iK;
```

- `claims(a,b)` is synchronous: is the entity in the table, `!a.gL` (not an outline pass), and not a player.
- The replacement is shown on several lines for reading; the real patch is one line of text, with no newlines.
- `JasprMutantsBind(a,b)` is **resumable** (code below). It calls `FTd(a, rl)` for each of the def's RLs with `rl.a3s!==1`, so the first image decode suspends safely, just like vanilla's first bind.
- `draw(...)` is synchronous and returns true when it drew. It returns false, so vanilla draws, if anything is unloaded or on any error, after which the stage disables itself.
- State 16 runs `DqP`, so the vanilla name tag stays. `RenderLiving.CIl` then draws the leash, and `Gxv` draws the shadow and fire (`EnF`).
- Position is already interpolated (args `c,d,e`).
- Shadow radius is `a.Cb`. The renderer is shared, so `draw` sets `a.Cb=def.shadow` for claimed entities and restores the saved original (`a.$jmShadow0`) whenever an unclaimed entity passes through `claims`.

Resumable bind, in the same style as `JasprArmorBarDraw`. It suspends only on a texture's first use:

```js
function JasprMutantsBind(a /*renderer*/, b /*entity*/) {
  var c, d, $p = 0;
  if (FX()) { var $T = Ds(); $p = $T.l(); d = $T.l(); c = $T.l(); b = $T.l(); a = $T.l(); }
  _: while (true) { switch ($p) {
    case 0: c = JasprMutantsBridge.textures(b); d = 0; $p = 1;   // JS array of cached Bb objects for this kind
    case 1: if (d >= c.length) return;
            if (c[d].a3s === 1) { d = d + 1 | 0; continue _; }   // already loaded -> nothing to do
            $p = 2;
    case 2: FTd(a, c[d]); if (B()) break _;                        // first image decode may suspend here
            d = d + 1 | 0; $p = 1; continue _;
    default: FT();
  } }
  Ds().s(a, b, c, d, $p);
}
```

`draw()` body, mirroring `DWR` with engine helpers (all synchronous):

```js
function draw(a, e, x, y, z, yaw, pt) {
  var def = table.get(e.cu); if (!def || !def.ready()) return false;
  var depth = KrH === 5888 ? HKD : -1;
  Eu0(); F1Q();
  try {
    var body = EUK(a, e.s1, e.cZ, pt), head = EUK(a, e.zM, e.gN, pt), pitch = e.c2 + (e.bd - e.c2) * pt;
    var age = e.cv + pt, amt = Math.min(1, e.qi + (e.hp - e.qi) * pt), swing = e.CE - e.hp * (1 - pt);
    DPm(x, y, z);                                   // renderLivingAt
    if (def.vanillaDeath) ECq(a, e, age, body, pt); // rotate(180-yaw) + death tilt (uS)
    else Gc9(180 - body, 0, 1, 0);                  // custom death animation instead
    FWM(-1, -1, 1); FWM(def.scale, def.scale, def.scale); DPm(0, -1.501, 0); // prepareScale + preRenderCallback
    D6M();                                          // enableAlpha
    var tint = Dl3(a, e, pt);                       // hurt/death red flash (creeper flash via renderer ebL)
    if (!DfJ(e)) { FTd(a, def.texture); def.model.pose(e, swing, amt, age, head - body, pitch, pt); def.model.render(0.0625); }
    if (tint) EdA(a);
    EFX(1);
    def.layers(a, e, swing, amt, pt, age, head - body, pitch, 0.0625); // glow eyes: CyM();Fb_(1,1);G0W(33985,240,240);... restore
  } finally { GnI(33985); CQ6(); GnI(33984); Ggy(); ECi(); if (depth >= 0 && KrH === 5888 && HKD > depth) HKD = depth; }
  return true;
}
```

- **Glow layer.** Restore the lightmap with `var l=Ei5(e); G0W(33985, l%65536, l/65536|0)`.
- **Charged / energy layer.** Use the texture matrix: `DSz(5890);Cds();DPm(t*0.01,t*0.01,0);DSz(5888); … DSz(5890);Cds();DSz(5888)`.
- **Hand-held blocks.** Bind the atlas with `FTd(a,HEN)` (after `Lp()`), then `Ceq(HEH.EG, state, 1.0)`.
- **Gore and Mo' Bends still run for claimed entities.** Mo' Bends' `pre`/`post` in `Gxv` still animate the hidden vanilla zombie model. Gore `begin` treats the mob, but our parts are not owned by `a.iK`, so they take the native path and no vanilla pieces are drawn. Optionally call `JasprGoreBridge.end()` at the start of `draw` to drop gore's context for that entity. Check blood and death pieces in a browser.

**Hook B: status bytes** (`FXy`, unique original anchor):
`case 6:c.q2(e);if(B()){break _;}return;` → `case 6:if(JasprMutantsBridge.status(c,e))return;c.q2(e);if(B()){break _;}return;`

**Hook C: channel** (`Cyr`, original-text anchor): see §7. Optionally reply or say hello from `E8R` like the wide stage, so the server knows the client has the stage, e.g. `mutants1`. Old cached pages then keep seeing vanilla mobs.

**Hook D: per-frame bookkeeping** (`DbP`, synchronous). Insert `JasprMutantsBridge.frame(d,a.Pp);` before the original text `b=a.fd.fU;$p=8;case 8:Gdm(b);`. That text sits after the Mo' Bends call, so neither stage's anchor overlaps the other. Use it for pruning dead entities (`ed`), world-change resets and the paused flag (`HEH.cp`). A tick hook (`CHq`) is only needed for client-scheduled sounds; prefer server-side sounds.

**Optional**:
- Video Settings toggle "Mutant models ON/OFF" (§11); when off, `claims()` returns false and the entities render as their vanilla base mob.
- Diagnostics: `jaspercraft.mutants.state` and `.error` events plus `JasprMutantsDiagnostics.status()` with counters (claimed entities, draws, binds, errors, last error).

**Cost**: one display list per part, compiled once per model instance (share one model object per kind; pose it per entity before rendering, as vanilla does with `mainModel`). Each part costs one draw call per frame.

---

## 13. Open questions and risks

1. **Not yet run in a browser**:
   - the `DWR` replacement and its interplay with Mo' Bends `pre`/`post` and gore (blood and death pieces for claimed mobs);
   - the resumable first-bind inside `DWR` (expected to behave like vanilla's first bind);
   - TEISR item hook.
   - Plan one lean fixture test (memory `low-compute-testing.md`).
2. **Server side**: which vanilla base mob each mutant uses (hitbox, AI, metadata layout), the message format, and status-byte numbers. With a vanilla base, setEntryValues type mismatches are impossible. Remapping unknown spawn types (`FrD`) is only needed if the server fakes non-vanilla type ids.
3. **Mutant Beasts specifics to port by hand**: `JointModelRenderer` (2 parts), `ScalableModelRenderer` (own traversal), the animation API (`Animator`/`IAnimatedEntity`: tick-based keyframes; drive them from synced triggers plus `entity.cv`), `enableNormalize`/`enableOutlineMode` (drop), SkullSpirit atlas sprite (layer-3 particle), Endersoul hand TEISR (texture-matrix swirl).
4. **Video Settings has no free slot**: adding a toggle edits `JasprVideoBuild`, whose relevant text Mo' Bends already replaced, so the stage-ordering rule applies.
5. **Offline harness limits**: it cannot run class initializers that log (MathHelper `FG`, Particle's random). Stub them or set statics by hand (`fn.LAy`) when unit-testing; GL must be stubbed as in `tests\mobends-native.test.cjs`.
6. **Display-list growth**: never recompile parts per entity or per frame (`F$t` leaks the old list). Changing boxes needs `clh=0`, so do it rarely.
7. **Performance on phones**: measure claimed entities × parts. The perf stage's entity occlusion and particle culling still apply.
8. **Line numbers** in this document come from the TwoMuchNerdo build's source map. The vendor Java can drift by a few lines, and some vendor files carry JasperLoader edits (§0).

---

## Appendix A: JS function → Java file and line index (original bundle, exact)

Run with node at low priority. It reads 14 MB once and writes `fnindex.json` next to itself.

```js
'use strict'; // build-index.cjs
const os=require('os');try{os.setPriority(os.constants.priority.PRIORITY_LOW)}catch(e){}
const fs=require('fs'),D='C:/Users/AM/Documents/.eagler-1122-evaluation/1.12.2-WASM/';
const src=fs.readFileSync(D+'classes.js','utf8'),map=JSON.parse(fs.readFileSync(D+'classes.js.map','utf8'));
const V={};[...'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'].forEach((c,i)=>V[c]=i);
const vlq=s=>{let n=0,sh=0,r=[];for(const c of s){const v=V[c];n+=(v&31)*2**sh;if(v&32)sh+=5;else{r.push(n&1?-(n>>>1):n>>>1);n=0;sh=0;}}return r;};
const lines=[0];for(let i=0;i<src.length;i++)if(src.charCodeAt(i)===10)lines.push(i+1);
const fns=[];{const re=/function ([A-Za-z_$][\w$]*)\(/g;let m;while((m=re.exec(src)))fns.push([m.index,m[1]]);}
const enc=o=>{let lo=0,hi=fns.length-1;while(lo<hi){const mid=(lo+hi+1)>>1;if(fns[mid][0]<=o)lo=mid;else hi=mid-1;}return fns[lo][1];};
let f=0,l=0,c=0;const out={};
map.mappings.split(';').forEach((g,gl)=>{let gc=0;for(const seg of g.split(',')){if(!seg)continue;const d=vlq(seg);gc+=d[0];if(d.length<4)continue;
  f+=d[1];l+=d[2];c+=d[3];const k=enc(lines[gl]+gc),r=((out[k]=out[k]||{})[map.sources[f]]=out[k][map.sources[f]]||[1e9,0,0]);
  r[0]=Math.min(r[0],l+1);r[1]=Math.max(r[1],l+1);r[2]++;}});
fs.writeFileSync(__dirname+'/fnindex.json',JSON.stringify(out));
// query: Object.entries(out.DWR) -> [["net/minecraft/client/renderer/entity/RenderLivingBase.java",[71,305,117]]]
// reverse: for (k in out) if (out[k]['net/minecraft/client/model/ModelRenderer.java']) ...
```

To read a function body, slice the live `classes.js` from `\nfunction NAME(` to the next `\nfunction `. To decode a pooled string `C(n)`, eval the array literal passed to `$rt_stringPool([` in the bundle and index it.
