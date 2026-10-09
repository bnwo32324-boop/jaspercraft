/* client/gui/CreeperMinionTrackerScreen.java and ClientProxy.getClientGuiElement (GUI id 0), on the engine's GuiScreen
 * (CO, ctor BGm). Fields: mc j, fontRenderer J, width q, height L, buttonList be; virtuals initGui ee, onGuiClosed nY,
 * actionPerformed eB, updateScreen iU, drawScreen dK (GuiScreen's own: ElL), doesGuiPauseGame T7. GuiButton = B3 (ctor
 * Bq3(id, x, y, w, h, text); fields id bF, enabled bS, displayString dd). The texture lives at
 * minecraft:textures/gui/jaspr_mutants/creeper_minion_tracker.png and is loaded with the stage's other textures, so drawing
 * never suspends. */
(function (M) {
  "use strict";
  var TEXTURE = null;
  M.guiTextures = function () { if (TEXTURE === null) TEXTURE = M.rl("textures/gui/jaspr_mutants/creeper_minion_tracker.png"); return [TEXTURE]; };

  function format(key) { return GWe(M.JS(key), G(D, 0)); }    // I18n.format(key)
  function trackerFormat(key) { return format("gui.mutantbeasts.creeper_minion_tracker." + key); }
  function concat(a, b) { return M.JS(M.ustr(a) + M.ustr(b)); }
  // Float.toString / StringBuilder.append(float): the shortest decimal that reads back as the same float
  function jfloat(x) {
    x = Math.fround(x);
    if (x !== x) return "NaN";
    if (x === Infinity) return "Infinity";
    if (x === -Infinity) return "-Infinity";
    if (x === Math.trunc(x) && Math.abs(x) < 1e7) return (x === 0 && 1 / x < 0 ? "-0" : String(x)) + ".0";
    for (var p = 1; p <= 9; p++) { var s = x.toPrecision(p); if (Math.fround(parseFloat(s)) === x) return String(parseFloat(s)); }
    return String(x);
  }
  M.jfloat = jfloat;
  var Screen = null;
  // a throwing screen method switches "gui" off; the tick then closes the screen (JasprMutantsTick)
  function guarded(virtuals) {
    var out = {};
    for (var v in virtuals) out[v] = M.guardVirtual("gui", v, virtuals[v], v === "T7" ? function () { return 0; } : null);
    return out;
  }
  function button(id, x, y, w, h, text) { var b = new B3(); Bq3(b, id, x, y, w, h, text); return b; }

  M.defineGui = function () {
    if (Screen !== null) return Screen;
    var xSize = 176, ySize = 166;
    Screen = M.defineClass({
      name: "chumbanotz.mutantbeasts.client.gui.CreeperMinionTrackerScreen", extend: CO,
      fields: function (s) { s.guiX = 0; s.guiY = 0; s.creeperMinion = null; s.canRideOnShoulder_ = 0; s.canDestroyBlocks_ = 0; s.alwaysShowName = 0; },
      virtuals: guarded({
        ee: function () {                                     // initGui
          var m = this.creeperMinion;
          this.canDestroyBlocks_ = m.canDestroyBlocks();
          this.alwaysShowName = !!m.getAlwaysRenderNameTag();
          this.canRideOnShoulder_ = m.canRideOnShoulder();
          this.guiX = (this.q - xSize) / 2 | 0;
          this.guiY = (this.L - ySize) / 2 | 0;
          var buttonWidth = (xSize / 2 | 0) - 10;
          EGX(this, button(0, this.guiX + 8, this.guiY + ySize - 78, buttonWidth * 2 + 4, 20, this.destroysText()));
          EGX(this, button(1, this.guiX + 8, this.guiY + ySize - 54, buttonWidth * 2 + 4, 20, this.showNameText()));
          EGX(this, button(2, this.guiX + 8, this.guiY + ySize - 30, buttonWidth * 2 + 4, 20, this.shoulderText()));
          if (!m.isOwner(this.j.v)) {
            var list = M.listToArray(this.be);
            for (var i = 0; i < list.length; i++) list[i].bS = 0;
          }
          if (!M.CFG.ENTITIES.creeperMinionOnShoulder) M.listGet(this.be, 2).bS = 0;
          GIb(1);                                             // Keyboard.enableRepeatEvents(true)
        },
        nY: function () { GIb(0); },                          // onGuiClosed
        eB: function (b) {                                    // actionPerformed
          var m = this.creeperMinion;
          switch (b.bF) {
            case 0:
              this.canDestroyBlocks_ = !this.canDestroyBlocks_;
              M.sendTrackerPacket(m, 0, this.canDestroyBlocks_);
              b.dd = this.destroysText();
              break;
            case 1:
              this.alwaysShowName = !this.alwaysShowName;
              M.sendTrackerPacket(m, 1, this.alwaysShowName);
              b.dd = this.showNameText();
              break;
            case 2:
              this.canRideOnShoulder_ = !this.canRideOnShoulder_;
              M.sendTrackerPacket(m, 2, this.canRideOnShoulder_);
              b.dd = this.shoulderText();
              break;
          }
        },
        iU: function () {                                     // updateScreen
          if (!this.creeperMinion.isEntityAlive()) Cpd(this.j.v);   // mc.player.closeScreen()
        },
        dK: function (mouseX, mouseY, partialTicks) {         // drawScreen
          var m = this.creeperMinion, fr = this.J, x = this.guiX, y = this.guiY;
          EpO(this);                                          // drawDefaultBackground
          CFh(1.0, 1.0, 1.0, 1.0);
          D17(this.j.bE, TEXTURE);
          FYu(this, x, y, 0, 0, xSize, ySize);
          var health = M.f2i(Math.fround(Math.fround(m.getHealth() * 150.0) / m.getMaxHealth()));
          FYu(this, x + 13, y + 16, 0, 166, health, 6);
          Efa(fr, DQt(m.iG()), x + 13, y + 5, 0x404040);       // getDisplayName().getUnformattedText()
          Efa(fr, trackerFormat("health"), x + 13, y + 28, 0x404040);
          Efa(fr, trackerFormat("explosion"), x + 13, y + 48, 0x404040);
          Efa(fr, trackerFormat("blast_radius"), x + 13, y + 68, 0x404040);
          var sb = jfloat(m.getHealth() / 2.0) + " / " + jfloat(m.getMaxHealth() / 2.0);
          Ck1(this, fr, M.JS(sb), x + (xSize / 2 | 0) + 38, y + 30, 0xFFFFFF);
          Ck1(this, fr, m.canExplodeContinuously() ? trackerFormat("continuous") : trackerFormat("one_time"), x + (xSize / 2 | 0) + 38, y + 50, 0xFFFFFF);
          var temp = M.f2i(Math.fround(m.getExplosionRadius() * 10.0));
          Ck1(this, fr, M.JS(jfloat(temp / 10.0)), x + (xSize / 2 | 0) + 38, y + 70, 0xFFFFFF);
          ElL(this, mouseX, mouseY, partialTicks);            // super.drawScreen: the buttons
        },
        T7: function () { return 0; }                         // doesGuiPauseGame
      })
    });
    Screen.prototype.showNameText = function () { return concat(trackerFormat("always_show_name"), format(this.alwaysShowName ? "options.on" : "options.off")); };
    Screen.prototype.destroysText = function () { return concat(trackerFormat("destroys_blocks"), format(this.canDestroyBlocks_ ? "options.on" : "options.off")); };
    Screen.prototype.shoulderText = function () {
      if (M.CFG.ENTITIES.creeperMinionOnShoulder) return concat(trackerFormat("can_ride_on_shoulder"), format(this.canRideOnShoulder_ ? "options.on" : "options.off"));
      return trackerFormat("disabled");
    };
    M.CreeperMinionTrackerScreen = Screen;
    return Screen;
  };

  // player.openGui(MutantBeasts.INSTANCE, id, world, x, 0, 0) on the client: FMLClientHandler.showGuiScreen(
  // ClientProxy.getClientGuiElement(id, ...)), where id 0 is the tracker screen of world.getEntityByID(x). The screen is
  // shown by the resumable JasprMutantsTick at the start of the next tick (displayGuiScreen may suspend; this caller,
  // the entity's processInteract, must not).
  M.openGui = function (id, minion) {
    if (id !== 0 || minion === null || !M.enabled("gui")) return;
    try {
      var s = new (M.defineGui())();
      BGm(s);                                                 // GuiScreen()
      s.creeperMinion = minion;
      M.pendingScreen = s;
      M.stats.screens++;
    } catch (e) { M.fail("gui", e); }
  };
})(JasprMutants);
