"""Converts the BetterNether / NetherEx structure templates (gzipped NBT, palette + blocks) into JasprNether's compact
text templates. The palette keeps the ORIGINAL block ids (betternether:* / netherex:* / minecraft:*) with a canonical
meta (vanilla-shape encoding of the same family); the plugin resolves them through resources/blocks.tsv, the single
emulation table. Output: the plugin's resources/structures/*.jnt.  Run: python convert.py"""
import sys, os, glob, base64
sys.path.insert(0, r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\scripts\muse-glm\lib\vendor")
import nbtlib

BN = r"C:\Users\AM\AppData\Local\Temp\claude\C--Users-AM-Desktop-Curser-Test-Jaspergers\e333d2de-b241-42fb-89cb-cfb65c114623\scratchpad\nether\jars\bn\assets\betternether\structures"
NEX = r"C:\Users\AM\AppData\Local\Temp\claude\C--Users-AM-Desktop-Curser-Test-Jaspergers\e333d2de-b241-42fb-89cb-cfb65c114623\scratchpad\nether\jars\nex\assets\netherex\structures"
OUT = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\custom-plugins\JasprNether\resources\structures"
TSV = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\custom-plugins\JasprNether\resources\blocks.tsv"

STAIR_FACING = {'east': 0, 'west': 1, 'south': 2, 'north': 3}
DOOR_FACING = {'east': 0, 'south': 1, 'west': 2, 'north': 3}
FACING4 = {'north': 2, 'south': 3, 'west': 4, 'east': 5}
GATE_FACING = {'south': 0, 'west': 1, 'north': 2, 'east': 3}
COLORS = {'white': 0, 'orange': 1, 'magenta': 2, 'light_blue': 3, 'yellow': 4, 'lime': 5, 'pink': 6, 'gray': 7,
          'silver': 8, 'cyan': 9, 'purple': 10, 'blue': 11, 'brown': 12, 'green': 13, 'red': 14, 'black': 15}
AXIS3 = {'y': 0, 'x': 4, 'z': 8}

def canonical_meta(name, p):
    """Vanilla-shape meta of a block state, used both for minecraft:* and as the mod meta key into blocks.tsv."""
    n = name.split(':', 1)[1]
    if n.endswith('_stairs'):
        return STAIR_FACING[p.get('facing', 'north')] + (4 if p.get('half') == 'top' else 0)
    if 'door' in n:
        if p.get('half') == 'upper':
            return 8 + (1 if p.get('hinge') == 'right' else 0) + (2 if p.get('powered') == 'true' else 0)
        return DOOR_FACING[p.get('facing', 'north')] + (4 if p.get('open') == 'true' else 0)
    if n.endswith('fence_gate'):
        return GATE_FACING[p.get('facing', 'south')] + (4 if p.get('open') == 'true' else 0)
    if n in ('ladder', 'reeds_ladder', 'chest', 'cincinnasite_forge', 'furnace'):
        return FACING4.get(p.get('facing', 'north'), 2)
    if n.endswith('_slab') or n.endswith('_slab_half') or n == 'stone_slab':
        top = 8 if (p.get('half') == 'top' or p.get('type') == 'top') else 0
        if name == 'minecraft:stone_slab':
            return {'nether_brick': 6, 'quartz': 7}.get(p.get('variant'), 0) + top
        return top
    if name == 'minecraft:double_stone_slab':
        return 6 if p.get('variant') == 'nether_brick' else 0
    if 'color' in p:
        return COLORS[p['color']]
    if name == 'minecraft:quartz_block':
        return {'default': 0, 'chiseled': 1, 'lines_y': 2, 'lines_x': 3, 'lines_z': 4}[p.get('variant', 'default')]
    if name == 'minecraft:bone_block':
        return AXIS3[p.get('axis', 'y')]
    if name in ('minecraft:lava', 'minecraft:water'):
        return int(p.get('level', 0))
    if name in ('minecraft:nether_wart', 'minecraft:cauldron'):
        return int(p.get('age', p.get('level', 0)))
    if name == 'minecraft:skull':
        return 1
    if name == 'minecraft:fire':
        return 0
    if n == 'bone_mushroom':
        return 0 if p.get('facing', 'up') == 'up' else 1
    if n == 'elder_mushroom':
        return 0 if p.get('type', 'red') == 'red' else 1
    return 0

def entry(name, p):
    """(source, meta, kind, marker) for one palette state. kind: 0 block, 1 chest, 2 spawner, 3 skull, 4 void."""
    if name == 'minecraft:structure_block':
        return ('minecraft:air', 0, 0, None)
    if name == 'minecraft:structure_void':
        return (None, 0, 4, None)
    if name in ('minecraft:lava', 'minecraft:water') and p.get('level', '0') != '0':
        name = name.replace('minecraft:', 'minecraft:flowing_')
    meta = canonical_meta(name, p)
    kind = {'minecraft:chest': 1, 'minecraft:mob_spawner': 2, 'minecraft:skull': 3}.get(name, 0)
    marker = {'netherex:blue_fire': 'bluefire', 'netherex:urn_of_sorrow': 'urn', 'betternether:pig_statue_01': 'statue'}.get(name)
    return (name, meta, kind, marker)

def load_table():
    rows = set()
    for line in open(TSV, encoding='utf-8'):
        if not line.strip() or line.startswith('#'): continue
        rows.add(line.split('\t')[0])
    return rows

TABLE = load_table()

def convert(path, name):
    root = nbtlib.load(path)
    sx, sy, sz = [int(v) for v in root['size']]
    pal = root['palette']
    mapped = []
    for p in pal:
        props = {str(k): str(v) for k, v in p.get('Properties', {}).items()}
        e = entry(str(p['Name']), props)
        if e[0] and not e[0].startswith('minecraft:') and e[0] not in TABLE:
            raise SystemExit('blocks.tsv has no row for %s (in %s)' % (e[0], name))
        mapped.append(e)
    vol = bytearray([255]) * (sx * sy * sz)
    markers = []
    outpal, index = [], {}
    def pidx(t):
        if t not in index:
            index[t] = len(outpal)
            outpal.append(t)
        return index[t]
    for b in root['blocks']:
        x, y, z = [int(v) for v in b['pos']]
        st = int(b['state'])
        pname = str(pal[st]['Name'])
        source, meta, kind, marker = mapped[st]
        nbt = b.get('nbt')
        if pname == 'minecraft:structure_block' and nbt is not None:
            md = str(nbt.get('metadata', '')).strip()
            parts = md.split()
            markers.append((x, y, z, parts[0], parts[1] if len(parts) > 1 else '-') if parts else (x, y, z, 'end', '-'))
        elif kind == 1:
            table = str(nbt.get('LootTable', 'minecraft:chests/nether_bridge')) if nbt is not None else 'minecraft:chests/nether_bridge'
            markers.append((x, y, z, 'chest', table))
        elif kind == 2:
            mob = 'minecraft:blaze'
            if nbt is not None and 'SpawnData' in nbt:
                mob = str(nbt['SpawnData'].get('id', mob))
            markers.append((x, y, z, 'spawner', mob))
        elif kind == 3:
            rot = int(nbt.get('Rot', 0)) if nbt is not None else 0
            typ = int(nbt.get('SkullType', 0)) if nbt is not None else 0
            markers.append((x, y, z, 'skull', '%d:%d' % (typ, rot)))
        if marker:
            markers.append((x, y, z, marker, '-'))
        if kind == 4:
            continue
        vol[(y * sz + z) * sx + x] = pidx((source, meta, kind))
    lines = ['name ' + name, 'size %d %d %d' % (sx, sy, sz), 'palette %d' % len(outpal)]
    for t in outpal:
        lines.append('%s %d %d' % t)
    lines.append('data ' + base64.b64encode(bytes(vol)).decode('ascii'))
    for m in markers:
        lines.append('marker %d %d %d %s %s' % m)
    with open(os.path.join(OUT, name + '.jnt'), 'w', newline='\n') as f:
        f.write('\n'.join(lines) + '\n')
    return sx, sy, sz, len(outpal), len(markers)

os.makedirs(OUT, exist_ok=True)
count = 0
for f in sorted(glob.glob(BN + r"\**\*.nbt", recursive=True)):
    base = os.path.splitext(os.path.basename(f))[0]
    name = ('city_' + base if os.path.basename(os.path.dirname(f)) == 'city' and not base.startswith('city') else base)
    print('bn_' + name, convert(f, 'bn_' + name)); count += 1
for f in sorted(glob.glob(NEX + r"\**\*.nbt", recursive=True)):
    base = os.path.splitext(os.path.basename(f))[0]
    print('nex_' + base, convert(f, 'nex_' + base)); count += 1
print('converted', count)
