/* Networking of the stage (MUTANTS_PROTOCOL.md sections 2, 3 and 6):
 *  - channel "jaspr:mutants": op 0 HELLO (client -> server, once per connection), op 1 SPAWN (server -> client), the
 *    client half of FML's EntitySpawnHandler.spawnEntity,
 *  - channel "mutantbeasts": the mod's SimpleNetworkWrapper (discriminator byte + toBytes, big-endian):
 *    0 CreeperMinionTrackerPacket (sent by the tracker screen), 1 HeldBlockPacket, 2 SpawnParticlePacket,
 *    3 TeleportPacket (packet/*.java, Handler.onMessage translated),
 *  - channel "jaspr:scale": the dungeon's Big Mobs render scale table.
 * Incoming payloads are read from the engine's PacketBuffer inside handleCustomPayload; outgoing ones are queued and
 * sent by the resumable JasprMutantsTick at the start of Minecraft.runTick. */
(function (M) {
  "use strict";
  var R = M.R, W = M.W;
  var CH_MUTANTS = "jaspr:mutants", CH_MOD = "mutantbeasts", CH_SCALE = "jaspr:scale";
  M.CHANNELS = [CH_MUTANTS, CH_MOD, CH_SCALE];
  var unknownTypes = Object.create(null);

  // PacketBuffer readers (engine functions; each throws on underflow, which drops the message)
  var PB = M.PB = {
    readable: function (pb) { return G6(pb); },
    varInt: function (pb) { return EmC(pb); }, int: function (pb) { return E7Z(pb); }, byte: function (pb) { return CZl(pb); },
    ubyte: function (pb) { return DOh(pb); }, bool: function (pb) { return !!DPz(pb); }, short: function (pb) { return FSR(pb); },
    long: function (pb) { return CC$(pb); }, double: function (pb) { return GxP(pb); }, float: function (pb) { return E$K(pb); },
    uuid: function (pb) { return GfL(pb); }, string: function (pb, max) { return M.ustr(CRh(pb, max)); },
    entries: function (pb) { return GUN(pb); },                // EntityDataManager.readEntries
    bytes: function (pb, n) { var out = new Uint8Array(n); for (var i = 0; i < n; i++) out[i] = CZl(pb) & 255; return out; }
  };
  function angle(b) { return (b * 360) / 256.0; }             // byte angle -> degrees (as handleSpawnMob/FML)

  // ---------------------------------------------------------------- op 1 SPAWN
  M.readSpawn = function (pb) {
    var m = {};
    m.entityId = PB.varInt(pb);
    m.uuid = PB.uuid(pb);
    m.typeId = PB.varInt(pb);
    m.x = PB.double(pb); m.y = PB.double(pb); m.z = PB.double(pb);
    m.yaw = angle(PB.byte(pb)); m.pitch = angle(PB.byte(pb)); m.headYaw = angle(PB.byte(pb));
    m.motionX = PB.short(pb) / 8000.0; m.motionY = PB.short(pb) / 8000.0; m.motionZ = PB.short(pb) / 8000.0;
    m.thrower = PB.varInt(pb) - 1;
    m.entries = PB.entries(pb);
    var n = PB.varInt(pb);
    if (n < 0 || n > 4096 || n > PB.readable(pb)) throw new Error("bad spawn data length " + n);
    m.spawnData = PB.bytes(pb, n);
    return m;
  };
  // FMLClientHandler / EntitySpawnHandler.spawnEntity with the protocol's field order
  M.spawn = function (m, world) {
    var type = M.typeById(m.typeId);
    if (type === null) {
      M.stats.spawnUnknown++;
      if (!unknownTypes[m.typeId]) { unknownTypes[m.typeId] = 1; M.state("spawn-unknown-type", { typeId: m.typeId | 0 }); }
      return null;
    }
    var entity = type.cls.create(world);
    entity.setEntityId(m.entityId);
    entity.setUniqueId(m.uuid);
    Cbn(entity, m.x, m.y, m.z);                              // EntityTracker.updateServerPosition
    entity.setLocationAndAngles(m.x, m.y, m.z, m.yaw, m.pitch);
    if (entity instanceof Co) { entity.rotationYawHead = m.headYaw; entity.renderYawOffset = m.headYaw; entity.prevRotationYawHead = m.headYaw; entity.prevRenderYawOffset = m.headYaw; }
    entity.motionX = m.motionX; entity.motionY = m.motionY; entity.motionZ = m.motionZ;
    if (entity.$jmThrowable) {                               // IThrowableEntity
      var p = M.player(), thrower = null;
      if (m.thrower >= 0) thrower = p !== null && p.entityId === m.thrower ? p : W.getEntityByID(world, m.thrower);
      entity.setThrower(thrower);
    }
    if (m.entries !== null) FZE(entity.y, m.entries);         // getDataManager().setEntryValues(list)
    if (entity.$jmThrowable) entity.setVelocity(m.motionX, m.motionY, m.motionZ);
    if (typeof entity.readSpawnData === "function") {
      var buf = new M.ByteReader(m.spawnData);
      entity.readSpawnData(buf);
    }
    W.addEntityToWorld(world, m.entityId, entity);
    M.stats.spawns++;
    return entity;
  };

  // ---------------------------------------------------------------- channel "mutantbeasts" (MBPacketHandler)
  M.readModMessage = function (pb) {
    var disc = PB.byte(pb), m = { disc: disc };
    if (disc === 1) { m.entityId = PB.int(pb); m.blockId = PB.int(pb); m.blockIndex = PB.byte(pb); }
    else if (disc === 2) {
      m.particleId = PB.int(pb);
      m.posX = PB.double(pb); m.posY = PB.double(pb); m.posZ = PB.double(pb);
      m.offsetX = PB.double(pb); m.offsetY = PB.double(pb); m.offsetZ = PB.double(pb);
      m.amount = PB.int(pb);
    } else if (disc === 3) { m.entityId = PB.int(pb); m.blockPos = PB.long(pb); }
    else if (disc === 0) { m.entityId = PB.int(pb); m.optionsId = PB.byte(pb); m.value = PB.bool(pb); }
    else throw new Error("unknown mutantbeasts discriminator " + disc);
    return m;
  };
  M.handleModMessage = function (m, world) {
    var T = M.T, entity;
    if (m.disc === 1) {                                      // HeldBlockPacket.Handler
      entity = W.getEntityByID(world, m.entityId);
      if (entity instanceof T.MutantEndermanEntity && m.blockIndex > 0 && m.blockId !== -1 && m.blockIndex < entity.heldBlock.length) entity.sendHoldBlock(m.blockIndex, m.blockId);
    } else if (m.disc === 2) {                               // SpawnParticlePacket (fromBytes + Handler)
      var particleType = M.particle(m.particleId);
      if (particleType === null) particleType = M.P().BARRIER;
      var random = world.R, amount = Math.min(m.amount, 4096), i;
      if (particleType === M.MBParticles.ENDERSOUL) {
        for (i = 0; i < amount; ++i) {
          var f = (R.nextFloat(random) - 0.5) * 1.8, f1 = (R.nextFloat(random) - 0.5) * 1.8, f2 = (R.nextFloat(random) - 0.5) * 1.8;
          var tempX = m.posX + (R.nextFloat(random) - 0.5) * m.offsetX;
          var tempY = m.posY + (R.nextFloat(random) - 0.5) * m.offsetY + 0.5;
          var tempZ = m.posZ + (R.nextFloat(random) - 0.5) * m.offsetZ;
          W.spawnParticle(world, M.MBParticles.ENDERSOUL, tempX, tempY, tempZ, f, f1, f2, M.intArray(0));
        }
      } else {
        for (i = 0; i < amount; ++i) {
          var posX = m.posX + R.nextFloat(random) * m.offsetX * 2.0 - m.offsetX;
          var posY = m.posY + 0.5 + R.nextFloat(random) * m.offsetY;
          var posZ = m.posZ + R.nextFloat(random) * m.offsetZ * 2.0 - m.offsetZ;
          var x = R.nextGaussian(random) * 0.02, y = R.nextGaussian(random) * 0.02, z = R.nextGaussian(random) * 0.02;
          W.spawnParticle(world, particleType, posX, posY, posZ, x, y, z, M.intArray(0));
        }
      }
      M.stats.particles += amount;
    } else if (m.disc === 3) {                               // TeleportPacket.Handler
      entity = W.getEntityByID(world, m.entityId);
      if (entity instanceof T.MutantEndermanEntity) entity.setTeleportPosition(M.blockPosFromLong(BigInt.asIntN(64, BigInt(m.blockPos))));
    }
  };

  // ---------------------------------------------------------------- outgoing (queued, sent by JasprMutantsTick)
  var outgoing = [];
  M.queue = function (channel, bytes) { if (outgoing.length < 64) outgoing.push({ channel: channel, bytes: bytes }); };
  M.takeOutgoing = function () { return outgoing.shift() || null; };
  M.outgoingCount = function () { return outgoing.length; };
  function u8(arr) { return new Uint8Array(arr); }
  function writeVarInt(out, v) { do { var b = v & 0x7f; v >>>= 7; out.push(v !== 0 ? (b | 0x80) : b); } while (v !== 0); }
  function writeString(out, s) { var bytes = new TextEncoder().encode(s); writeVarInt(out, bytes.length); for (var i = 0; i < bytes.length; i++) out.push(bytes[i]); }
  function writeInt(out, v) { out.push((v >>> 24) & 255, (v >>> 16) & 255, (v >>> 8) & 255, v & 255); }
  M.helloBytes = function () { var out = [0]; writeVarInt(out, M.PROTOCOL_VERSION); writeString(out, M.version.slice(0, 64)); return u8(out); };
  // CreeperMinionTrackerPacket(creeperMinion, optionsId, setOption) through MBPacketHandler.INSTANCE.sendToServer
  M.sendTrackerPacket = function (minion, optionsId, value) {
    var out = [0]; writeInt(out, minion.entityId | 0); out.push(optionsId & 255); out.push(value ? 1 : 0);
    M.queue(CH_MOD, u8(out));
  };

  // ---------------------------------------------------------------- "jaspr:scale" (Big Mobs, protocol section 6)
  var scaleTable = new Map();                                // entityId -> scale
  M.scaleTable = scaleTable;
  M.applyScaleText = function (text) {
    scaleTable.clear();
    if (text) {
      var parts = String(text).split(",");
      for (var i = 0; i < parts.length && i < 512; i++) {
        var kv = parts[i].split(":");
        if (kv.length !== 2) continue;
        var id = parseInt(kv[0], 10), h = parseInt(kv[1], 10);
        if (isFinite(id) && isFinite(h) && h > 0 && h <= 2000) scaleTable.set(id, h / 100.0);
      }
    }
    M.stats.scaleTables++;
  };
  // hitbox once per change (Entity.setSize from the base size); never for the mod's own entities
  M.updateScaledEntities = function (world) {
    if (world === null) return;
    var self = this;
    scaleTable.forEach(function (s, id) {
      var e = W.getEntityByID(world, id);
      if (e === null || e.constructor.$jm) return;
      if (e.$jmScale === s) return;
      if (e.$jmBaseW === undefined) { e.$jmBaseW = e.width; e.$jmBaseH = e.height; }
      e.$jmScale = s;
      FET(e, e.$jmBaseW * s, e.$jmBaseH * s);
      if (s > 1.0) e.ignoreFrustumCheck = 1;
    });
    // entities dropped from the table go back to their base size
    var list = world.gw;
    for (var i = 0; list && i < list.g; i++) {
      var e = list.qN.data[i];
      if (e && e.$jmScale !== undefined && !scaleTable.has(e.entityId)) {
        FET(e, e.$jmBaseW, e.$jmBaseH);
        e.$jmScale = undefined;
      }
    }
  };
  M.renderScaleOf = function (e) { var s = e.$jmScale; return s === undefined || e.constructor.$jm ? 1.0 : s; };

  // ---------------------------------------------------------------- dispatch from handleCustomPayload
  M.payload = function (packet) {
    var channel = M.ustr(packet.S$);
    if (channel !== CH_MUTANTS && channel !== CH_MOD && channel !== CH_SCALE) return false;
    if (!M.installed) return true;
    M.stats.messages++;
    var pb = packet.Wm, world = M.world();
    if (channel === CH_SCALE) { M.applyScaleText(PB.string(pb, 32767)); return true; }
    if (world === null) return true;
    if (channel === CH_MUTANTS) {
      var op = PB.byte(pb);
      if (op === 1) { if (M.enabled("spawn")) { try { M.spawn(M.readSpawn(pb), world); } catch (e) { M.stats.spawnErrors++; M.fail("spawn", e); } } }
      // op 0 HELLO is client -> server, op 2 DATA_KEYS and ops >= 3 are reserved
      return true;
    }
    if (M.enabled("modchannel")) { try { M.handleModMessage(M.readModMessage(pb), world); } catch (e) { M.fail("modchannel", e); } }
    return true;
  };
})(JasprMutants);
