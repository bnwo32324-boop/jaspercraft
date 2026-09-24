'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const cp = require('node:child_process');
const root = path.resolve(__dirname, '..');
const plugin = path.join(root, 'server/custom-plugins/JasprHorrorBiomes');
const rows = name => fs.readFileSync(path.join(plugin, 'resources', name), 'utf8')
  .split(/\r?\n/).filter(line => line.trim() && !line.startsWith('#')).map(line => line.split('|'));

test('all 62 carriers have explicit distinct detail recipes and safe palettes', () => {
  const biomes = rows('biomes.tsv'), details = rows('biome-details.tsv');
  assert.equal(details.length, 62);
  assert.deepEqual(details.map(row => row[0]), biomes.map(row => row[0]));
  assert.equal(new Set(details.map(row => row[1])).size, 62);
  assert.equal(new Set(details.map(row => row.slice(2, 9).join('|'))).size, 62);
  assert.equal(new Set(details.flatMap(row => row.slice(2, 5))).size, 47);
  assert.equal(new Set(details.map(row => row[5])).size, 12);
  const safe = new Set([1,4,5,17,20,24,35,43,44,45,48,80,85,98,101,102,112,113,125,126,139,159,162,172,174,179,216]);
  for (const row of details) {
    assert.equal(row.length, 10, row[0]);
    assert.equal(new Set(row.slice(2, 5)).size, 3, row[1]);
    assert.equal(row[9], '0', row[1]);
    for (const material of row.slice(6, 9)) {
      assert.match(material, /^\d+:\d+$/);
      const [id, data] = material.split(':').map(Number);
      assert.ok(safe.has(id), `${row[1]} unsafe material ${id}`);
      assert.ok(data >= 0 && data <= 15);
    }
  }
});

test('detail generation has no scheduling, entity, neighbor-loading or progression writes', () => {
  const detail = fs.readFileSync(path.join(plugin, 'src/chat/jaspr/biomes/BiomeDetails.java'), 'utf8');
  const native = fs.readFileSync(path.join(plugin, 'src/chat/jaspr/biomes/OuterRealms.java'), 'utf8');
  for (const source of [detail, native]) {
    assert.doesNotMatch(source, /getChunkAt\s*\(|getBlockAt\s*\(|getHighestBlock\w*\s*\(|getEntities\s*\(|spawnEntity\s*\(|runTask\w*\s*\(|setGlowing\s*\(/);
    assert.doesNotMatch(source, /new\s+Random\s*\(|random\.next/);
  }
  assert.match(native, /BiomeDetails\.decorateNative\(c,s,w\.getSeed\(\),nether\)/);
  assert.match(detail, /if\(reserved\(cx,cz\)\)return/);
});

test('standalone JVM: zero clutter in all 62 biomes and native realms; natural blocks preserved', {timeout: 90000}, () => {
  const output = fs.mkdtempSync(path.join(os.tmpdir(), 'jaspr-biome-zero-detail-test-'));
  const jar = path.join(root, 'server/cache/patched_1.12.2.jar');
  // Reuse the existing terrain/native fixtures without editing their Java source.
  // This temporary harness replaces the former expectation of positive decoration.
  const harness = path.join(output, 'ZeroDetailTest.java');
  fs.writeFileSync(harness, String.raw`
package chat.jaspr.biomes;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;

public final class ZeroDetailTest {
  static int checks;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static Object field(Object object,String name)throws Exception {
    Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
  }
  static Object call(String name,Class<?>[] types,Object... args)throws Exception {
    Method m=BiomeDetailsTest.class.getDeclaredMethod(name,types);m.setAccessible(true);
    return m.invoke(null,args);
  }
  public static void main(String[] args)throws Exception {
    call("recipes",new Class<?>[0]);call("shapes",new Class<?>[0]);
    Class<?> chunkClass=Class.forName("chat.jaspr.biomes.BiomeDetailsTest$TestChunk");
    Constructor<?> chunkCtor=chunkClass.getDeclaredConstructor(long.class,int.class,int.class,int.class);
    chunkCtor.setAccessible(true);
    for(long seed:new long[]{0L,0x62b10de7a1L,-987654321L}) {
      Terrain terrain=new Terrain(seed);
      Map<?,?> points=(Map<?,?>)call("points",new Class<?>[]{Terrain.class},terrain);
      check(points.size()==62,"All biome terrain regions found");
      for(int biome=0;biome<62;biome++) {
        check(BiomeDetails.ALL.get(biome).density==0,"Detail disabled "+biome);
        int[] at=(int[])points.get(biome);
        for(int height:new int[]{-1,70,57,-2}) {
          Object fixture=chunkCtor.newInstance(seed,at[0],at[1],height);
          int[] blocks=(int[])field(fixture,"blocks");
          int[][] heights=(int[][])field(fixture,"heights");
          // Existing logs, leaves, grass, flowers, water, snow and cactus are immutable.
          int[] natural={17,18,31,38,9,78,81};
          for(int n=0;n<natural.length;n++)blocks[((n+2)*16+3)*256+115]=natural[n]<<4;
          int[] before=blocks.clone();int[][] beforeHeights=new int[16][];
          for(int x=0;x<16;x++)beforeHeights[x]=heights[x].clone();
          for(int repeat=0;repeat<2;repeat++)
            BiomeDetails.decorate((ChunkGenerator.ChunkData)fixture,terrain,at[0],at[1],heights);
          check(Arrays.equals(before,blocks),"Terrain/water/trees/foliage changed "+biome);
          check(Arrays.deepEquals(beforeHeights,heights),"Terrain heights changed "+biome);
          check(((Integer)field(fixture,"writes"))==0,"Artificial landmark/litter writes "+biome);
        }
      }
    }
    Class<?> nativeClass=Class.forName("chat.jaspr.biomes.BiomeDetailsTest$NativeFixture");
    Constructor<?> nativeCtor=nativeClass.getDeclaredConstructor(long.class,boolean.class,int.class,int.class);
    nativeCtor.setAccessible(true);
    Method populate=nativeClass.getDeclaredMethod("populate",long.class);populate.setAccessible(true);
    for(boolean nether:new boolean[]{true,false}) {
      Set<Integer> profiles=new HashSet<>();
      for(int region=-24;region<=24;region++) {
        int cx=region*24+16,cz=14;
        profiles.add(OuterRealms.profile(0x62b10de7a1L,nether,cx*16+8,cz*16+8).index);
        Object fixture=nativeCtor.newInstance(0x62b10de7a1L,nether,cx,cz);
        Object data=field(fixture,"data");int[] blocks=(int[])field(data,"blocks"),before=blocks.clone();
        int added=BiomeDetails.decorateNative((Chunk)field(fixture,"chunk"),
          (ChunkSnapshot)field(fixture,"snapshot"),0x62b10de7a1L,nether);
        check(added==0&&Arrays.equals(before,blocks),"Native detail must be a no-op");
        check(((Integer)field(data,"writes"))==0,"Native detail writes");
        populate.invoke(fixture,7L);
        check(((Integer)field(fixture,"biomes"))==256,"Native biome metadata retained");
        for(int k=0;k<blocks.length;k++)
          if(k%256!=64)check(blocks[k]==before[k],"Native skyline or subsurface changed");
      }
      check(profiles.size()==(nether?9:5),"All native profiles covered");
    }
    // Zero is the only newly permitted density; malformed and formerly invalid values still fail.
    String[] row={"OCEAN","test","BOAT","PIER","BOLLARDS","SALT","17:0","5:0","159:8","0"};
    for(String density:new String[]{"-1","1","2","5"}) {
      row[9]=density;
      try{new BiomeDetails.Detail(Catalog.ALL.get(0),row);throw new AssertionError("Invalid density accepted");}
      catch(IllegalArgumentException expected){checks++;}
    }
    System.out.println("ZERO_DETAIL_PASS biomes=62 seeds=3 nativeProfiles=14 checks="+checks);
  }
}
`, 'utf8');
  const sources = ['Catalog.java','Terrain.java','BiomeDetails.java','OuterRealms.java']
    .map(name => path.join(plugin, 'src/chat/jaspr/biomes', name));
  sources.push(path.join(__dirname, 'java/chat/jaspr/biomes/BiomeDetailsTest.java'), harness);
  const compile = cp.spawnSync('javac', ['--release','8','-encoding','UTF-8','-cp',jar,'-d',output,...sources],
    {encoding:'utf8', timeout:30000, windowsHide:true});
  assert.equal(compile.status, 0, `${compile.error || ''}${compile.stdout || ''}${compile.stderr || ''}`);
  const run = cp.spawnSync('java', ['-Xmx384m','-cp',[output,jar,path.join(plugin,'resources')].join(path.delimiter),
    'chat.jaspr.biomes.ZeroDetailTest'], {encoding:'utf8', timeout:55000, maxBuffer:2*1024*1024, windowsHide:true});
  process.stdout.write(run.stdout || '');
  assert.equal(run.status, 0, `${run.error || ''}${run.stdout || ''}${run.stderr || ''}`);
  assert.match(run.stdout, /ZERO_DETAIL_PASS biomes=62 seeds=3 nativeProfiles=14/);
});
