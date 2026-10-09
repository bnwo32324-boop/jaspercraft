/* client/particle/{EndersoulParticle,SkullSpiritParticle}.java and their registration (ClientProxy.init), on the
 * engine's Particle (D_). Fields: prevPos kX iR kW, pos d1 db d0, motion bT b2 bU, canCollide dsM, isExpired dk0,
 * rand dc, age ez, maxAge cT, scale er, red eC, green ey, blue eu; virtuals onUpdate dt, renderParticle qy,
 * getFXLayer G7, getBrightnessForRender a51, move bD1.
 * SkullSpiritParticle: the mod registers its sprite in the block atlas (TextureStitchEvent.Pre) and draws on layer 1.
 * This client stitches no extra sprites, so the particle draws the same quad itself on layer 3 (the self-drawing layer)
 * with its own texture, minecraft:textures/particle/jaspr_mutants/skull_spirit.png, and the state layer 1 would have. */
(function (M) {
  "use strict";
  var R = M.R;
  var SKULL_TEXTURE = null;
  M.particleTextures = function () { if (SKULL_TEXTURE === null) SKULL_TEXTURE = M.rl("textures/particle/jaspr_mutants/skull_spirit.png"); return [SKULL_TEXTURE]; };

  function particleInit(p, world, x, y, z, vx, vy, vz) { BZl(p, world, x, y, z, vx, vy, vz); }   // Particle(World, x, y, z, xs, ys, zs)
  // a particle whose code throws (or while the part is off) expires instead of reaching the particle manager's loop
  function expire() { this.dk0 = 1; }
  function guarded(virtuals) {
    var out = {};
    for (var v in virtuals) out[v] = M.guardVirtual("particles", v, virtuals[v], v === "a51" ? function () { return 0xF000F0; } : v === "G7" ? function () { return 3; } : expire);
    return out;
  }

  M.defineParticles = function () {
    // ============================================================ EndersoulParticle.java
    var EndersoulParticle = M.defineClass({
      name: "chumbanotz.mutantbeasts.client.particle.EndersoulParticle", extend: D_,
      fields: function (s) { s.fullScale = 0.0; },
      virtuals: guarded({
        qy: function (buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ) {   // renderParticle
          var scale = 1.0 - (this.ez + partialTicks) / this.cT;
          scale *= scale;
          scale = 1.0 - scale;
          this.er = this.fullScale * scale;
          Evn(this, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        },
        a51: function () { return 0xF000F0; },                  // getBrightnessForRender
        dt: function () {                                       // onUpdate
          this.kX = this.d1; this.iR = this.db; this.kW = this.d0;
          if (this.ez++ >= this.cT) Gt(this);                   // setExpired
          this.b2 += 0.002;
          this.bD1(this.bT, this.b2, this.bU);                  // move
          if (this.db === this.iR) { this.bT *= 1.1; this.bU *= 1.1; }
          this.bT *= 0.9; this.b2 *= 0.9; this.bU *= 0.9;
        }
      })
    });
    EndersoulParticle.create = function (world, x, y, z, xSpeedIn, ySpeedIn, zSpeedIn) {
      var p = new EndersoulParticle();
      particleInit(p, world, x, y, z, 0.0, 0.0, 0.0);
      p.cT = M.f2i(Math.random() * 15.0) + 10;                 // particleMaxAge
      p.bT *= 0.10000000149011612; p.b2 *= 0.10000000149011612; p.bU *= 0.10000000149011612;
      p.bT += xSpeedIn; p.b2 += ySpeedIn; p.bU += zSpeedIn;
      p.fullScale = p.er = R.nextFloat(p.dc) * 0.4 + 2.4;
      p.ey = p.eu = R.nextFloat(p.dc) * 0.6 + 0.4;            // particleGreen = particleBlue
      p.eC = p.eu;                                            // particleRed = particleBlue
      p.ey *= 0.3;
      p.eC *= 0.9;
      p.dsM = 0;                                              // canCollide = false
      IC(p, M.f2i(Math.random() * 8.0));                      // setParticleTextureIndex
      return p;
    };

    // ============================================================ SkullSpiritParticle.java
    var SkullSpiritParticle = M.defineClass({
      name: "chumbanotz.mutantbeasts.client.particle.SkullSpiritParticle", extend: D_,
      fields: function (s) { s.skullScale = 0.0; },
      virtuals: guarded({
        G7: function () { return 3; },                          // getFXLayer (1 with an atlas sprite; 3 draws its own quad)
        qy: function (buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ) {
          var timeScale = (this.ez + partialTicks) / this.cT * 32.0;
          if (timeScale < 0.0) timeScale = 0.0;
          if (timeScale > 1.0) timeScale = 1.0;
          this.er = this.skullScale * timeScale;
          drawQuad(this, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        },
        dt: function () {
          this.kX = this.d1; this.iR = this.db; this.kW = this.d0;
          if (this.ez++ >= this.cT) this.dk0 = 1;               // isExpired = true
          this.b2 += 0.002;
          this.bD1(this.bT, this.b2, this.bU);
          if (this.db === this.iR) { this.bT *= 1.1; this.bU *= 1.1; }
          this.bT *= 0.9599999785423279; this.b2 *= 0.9599999785423279; this.bU *= 0.9599999785423279;
        }
      })
    });
    SkullSpiritParticle.create = function (world, x, y, z, xx, yy, zz) {
      var p = new SkullSpiritParticle();
      particleInit(p, world, x, y, z, 0.0, 0.0, 0.0);
      p.bT *= 0.10000000149011612; p.b2 *= 0.10000000149011612; p.bU *= 0.10000000149011612;
      p.bT += xx; p.b2 += yy; p.bU += zz;
      p.ey = p.eu = 1.0 - Math.random() * 0.2;
      p.eC = p.eu;
      p.er *= 1.0;
      var scale = 0.4 + R.nextFloat(p.dc) * 0.6;
      p.er *= scale;
      p.skullScale = p.er;
      p.cT = M.f2i(8.0 / (Math.random() * 0.8 + 0.2));
      p.cT = M.f2i(p.cT * scale);
      p.dsM = 0;
      return p;
    };

    // Particle.renderParticle's quad (vanilla 1.12) with the whole skull_spirit texture as its sprite, drawn with the
    // particle layers' state (blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA, alpha 516 1/255, depth mask on).
    function drawQuad(p, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ) {
      if (SKULL_TEXTURE === null || SKULL_TEXTURE.a3s !== 1) return;
      var f = 0.0, f1 = 1.0, f2 = 0.0, f3 = 1.0;
      var f4 = 0.1 * p.er;
      var f5 = p.kX + (p.d1 - p.kX) * partialTicks - LoV;
      var f6 = p.iR + (p.db - p.iR) * partialTicks - LoW;
      var f7 = p.kW + (p.d0 - p.kW) * partialTicks - LoX;
      var i = p.a51(partialTicks), j = i >> 16 & 65535, k = i & 65535;
      var v = [[-rotationX * f4 - rotationXY * f4, -rotationZ * f4, -rotationYZ * f4 - rotationXZ * f4],
        [-rotationX * f4 + rotationXY * f4, rotationZ * f4, -rotationYZ * f4 + rotationXZ * f4],
        [rotationX * f4 + rotationXY * f4, rotationZ * f4, rotationYZ * f4 + rotationXZ * f4],
        [rotationX * f4 - rotationXY * f4, -rotationZ * f4, rotationYZ * f4 - rotationXZ * f4]];
      var uv = [[f1, f3], [f1, f2], [f, f2], [f, f3]];
      D17(HEH.bE, SKULL_TEXTURE);
      CyM(); Fb_(770, 771); DQU(516, 0.003921569); EFX(1);
      C5();
      var t = GdM(), buf = t.dy;
      Ep0(buf, 7, Lou);                                       // PARTICLE_POSITION_TEX_COLOR_LMAP
      for (var n = 0; n < 4; n++) {
        CUb(buf, f5 + v[n][0], f6 + v[n][1], f7 + v[n][2]);
        EpJ(buf, uv[n][0], uv[n][1]);
        Eip(buf, p.eC, p.ey, p.eu, p.zC);
        Do_(buf, j, k);
        E74(buf);
      }
      FE$(t);
      DQU(516, 0.1);
    }

    // ClientProxy.init: effectRenderer.registerParticle(id, new Factory())
    function factory(create) {
      return { gW: M.guardVirtual("particles", "gW", function (id, world, x, y, z, xs, ys, zs) { M.stats.particles++; return create(world, x, y, z, xs, ys, zs); },
        function () { return null; }) };                     // IParticleFactory.createParticle (null: nothing spawned)
    }
    M.PARTICLE_FACTORIES = [[M.CFG.GENERAL.endersoulParticleID, factory(EndersoulParticle.create)], [M.CFG.GENERAL.skullSpiritParticleID, factory(SkullSpiritParticle.create)]];
    M.EndersoulParticle = EndersoulParticle;
    M.SkullSpiritParticle = SkullSpiritParticle;
  };
  M.registerParticles = function (pm) {
    for (var i = 0; i < M.PARTICLE_FACTORIES.length; i++) FAj(pm, M.PARTICLE_FACTORIES[i][0], M.PARTICLE_FACTORIES[i][1]);
    return M.PARTICLE_FACTORIES.length;
  };
})(JasprMutants);
