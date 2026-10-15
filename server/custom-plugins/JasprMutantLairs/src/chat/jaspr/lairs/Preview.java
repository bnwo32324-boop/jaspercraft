package chat.jaspr.lairs;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Offline preview of every lair (no server): isometric pictures of the drawing on flat land, cut at several heights,
 * plus a check of each drawing. java -cp JasprMutantLairs.jar chat.jaspr.lairs.Preview <out dir> [design id] [seed]
 */
public final class Preview {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File out = new File(args.length > 0 ? args[0] : "lair-preview");
        out.mkdirs();
        String only = args.length > 1 ? args[1] : null;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 12345L;
        int bad = 0;
        for (Design d : Designs.ALL) {
            if (only != null && !only.equals("all") && !only.equals(d.id)) continue;
            Canvas c = d.render(seed);
            List<String> problems = c.problems();
            int chests = 0, vault = 0, spawners = 0;
            for (Canvas.Tile t : c.tiles) { if (t.kind == Canvas.CHEST) { chests++; if (t.vault) vault++; } if (t.kind == Canvas.SPAWNER) spawners++; }
            System.out.println("LAIR " + d.id + " " + d.w + "x" + d.d + " y" + c.bottom() + ".." + c.top() + " chests=" + chests + " vault=" + vault
                + " spawners=" + spawners + " garrisons=" + c.garrisons.size() + " seals=" + c.seals.size() + (problems.isEmpty() ? " OK" : " PROBLEMS " + problems));
            if (!problems.isEmpty()) bad++;
            ImageIO.write(iso(c, Canvas.YMAX, false), "png", new File(out, d.id + ".png"));
            ImageIO.write(iso(c, 2, false), "png", new File(out, d.id + "-cut2.png"));
            if (c.bottom() < -1) ImageIO.write(iso(c, c.boss != null && c.boss[1] < 0 ? c.boss[1] + 3 : -2, true), "png", new File(out, d.id + "-below.png"));
            if (c.vault != null) ImageIO.write(iso(c, c.vault[1] + 3, c.vault[1] < 0), "png", new File(out, d.id + "-vault.png"));
        }
        System.out.println(bad == 0 ? "PREVIEW OK" : "PREVIEW PROBLEMS " + bad);
        if (bad > 0) System.exit(1);
    }

    /** The state at a position on flat test land (pad columns levelled, untouched land = grass over dirt at y <= 0). */
    static int state(Canvas c, int u, int y, int v, boolean undergroundOnly) {
        int s = c.get(u, y, v);
        if (s >= 0) return s;
        if (y > 0) return 0;
        if (undergroundOnly) return 0;
        return y == 0 ? M.GRASS : y > -4 ? M.DIRT : M.STONE;
    }

    static BufferedImage iso(Canvas c, int cutY, boolean undergroundOnly) {
        int S = 6, H = 3;     // half-width of a block's top diamond, and its height step
        int y0 = undergroundOnly ? Canvas.YMIN : Math.max(Canvas.YMIN, -12), y1 = Math.min(cutY, Canvas.YMAX);
        int width = (c.w + c.d) * S + 40, height = (c.w + c.d) * S / 2 + (y1 - y0 + 1) * H * 2 + 40;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(24, 26, 32)); g.fillRect(0, 0, width, height);
        int ox = c.d * S + 20, oy = (y1 - y0 + 1) * H * 2 + 20;
        // painter's order: far to near (u + v ascending), bottom to top
        for (int sum = 0; sum <= c.w + c.d - 2; sum++)
            for (int y = y0; y <= y1; y++)
                for (int u = Math.max(0, sum - (c.d - 1)); u <= Math.min(c.w - 1, sum); u++) {
                    int v = sum - u, s = state(c, u, y, v, undergroundOnly);
                    if (s <= 0 || M.id(s) == 0) continue;
                    int sx = ox + (u - v) * S, sy = oy + (u + v) * S / 2 - (y - y0) * H * 2;
                    Color base = color(s);
                    boolean small = small(M.id(s));
                    if (small) { g.setColor(base); g.fillRect(sx - 2, sy - H, 4, H + 1); continue; }
                    boolean topOpen = y == y1 || isAirish(state(c, u, y + 1, v, undergroundOnly));
                    if (topOpen) poly(g, base.brighter(), sx, sy - H * 2, sx + S, sy - H * 2 + S / 2, sx, sy - H * 2 + S, sx - S, sy - H * 2 + S / 2);
                    poly(g, base, sx - S, sy - H * 2 + S / 2, sx, sy - H * 2 + S, sx, sy + S, sx - S, sy + S / 2);
                    poly(g, base.darker(), sx + S, sy - H * 2 + S / 2, sx, sy - H * 2 + S, sx, sy + S, sx + S, sy + S / 2);
                }
        // the boss point and the vault box
        if (c.boss != null) {
            int sx = ox + (c.boss[0] - c.boss[2]) * S, sy = oy + (c.boss[0] + c.boss[2]) * S / 2 - (c.boss[1] - y0) * H * 2;
            g.setColor(Color.RED); g.fillOval(sx - 4, sy - 10, 8, 8);
        }
        g.dispose();
        return img;
    }
    private static boolean isAirish(int s) { return s <= 0 || M.id(s) == 0 || small(M.id(s)); }
    private static boolean small(int id) {
        switch (id) { case 30: case 31: case 32: case 37: case 38: case 39: case 40: case 50: case 63: case 65: case 68: case 66: case 101: case 102: case 106: case 160: case 85: case 113: case 139: case 188: case 189: case 190: case 191: case 192: case 107: case 183: case 184: case 185: case 186: case 187: case 144: case 171: case 198: case 175: case 59: case 83: case 111: case 69: case 77: return true; default: return false; }
    }
    private static void poly(Graphics2D g, Color col, int... xy) {
        Polygon p = new Polygon();
        for (int i = 0; i < xy.length; i += 2) p.addPoint(xy[i], xy[i + 1]);
        g.setColor(col); g.fillPolygon(p);
    }

    static Color color(int s) {
        int id = M.id(s), d = M.data(s);
        switch (id) {
            case 1: return d == 0 ? rgb(0x7d7d7d) : d <= 2 ? rgb(0x9a6b55) : d <= 4 ? rgb(0xbcbcbc) : rgb(0x848486);
            case 2: return rgb(0x5d9a3b);
            case 3: return d == 2 ? rgb(0x5a4122) : rgb(0x866043);
            case 4: return rgb(0x7a7a7a);
            case 5: return d == 1 ? rgb(0x664f2f) : d == 2 ? rgb(0xc4b17a) : d == 5 ? rgb(0x3e2912) : d == 4 ? rgb(0xa95a31) : rgb(0x9c7f4e);
            case 12: return d == 1 ? rgb(0xa9541d) : rgb(0xdbd3a0);
            case 13: return rgb(0x857f7e);
            case 17: case 162: return d % 4 == 1 && id == 162 ? rgb(0x3c2e1a) : rgb(0x5f4a2b);
            case 18: case 161: return rgb(0x2e5a1c);
            case 20: case 95: case 102: case 160: return glass(id, d);
            case 24: case 128: return rgb(0xd8ca9b);
            case 30: return rgb(0xe6e6e6);
            case 35: case 171: return dye(d);
            case 43: case 44: return rgb(0xa0a0a0);
            case 45: case 108: return rgb(0x96614f);
            case 47: return rgb(0x6b5232);
            case 48: return rgb(0x627162);
            case 49: return rgb(0x14121d);
            case 50: return rgb(0xffd060);
            case 52: return rgb(0x1b2a35);
            case 54: return rgb(0xa1772f);
            case 65: return rgb(0x9a7a45);
            case 67: return rgb(0x7a7a7a);
            case 81: return rgb(0x0f7a1f);
            case 85: case 188: case 191: case 107: case 186: return rgb(0x5c4325);
            case 86: case 91: return rgb(0xe08a1c);
            case 87: return rgb(0x6f3634);
            case 88: return rgb(0x4f3c30);
            case 89: return rgb(0xf9d49c);
            case 98: case 109: return d == 1 ? rgb(0x6a7a5c) : d == 2 ? rgb(0x6f6f6f) : rgb(0x7a7a7a);
            case 99: case 100: return d == 10 ? rgb(0xcfc9b3) : id == 100 ? rgb(0xb22b28) : rgb(0x8d6a52);
            case 101: return rgb(0x5f5f5f);
            case 103: return rgb(0x8ca533);
            case 110: return rgb(0x6f6265);
            case 112: case 113: case 114: return rgb(0x2c161a);
            case 118: return rgb(0x3b3b3b);
            case 121: return rgb(0xdfe0a6);
            case 126: case 134: case 164: case 53: return d % 8 == 1 ? rgb(0x664f2f) : d % 8 == 5 ? rgb(0x3e2912) : rgb(0x9c7f4e);
            case 139: return d == 1 ? rgb(0x627162) : rgb(0x7a7a7a);
            case 144: return rgb(0xd8d8c8);
            case 145: return rgb(0x444444);
            case 155: case 156: return rgb(0xeae4dc);
            case 159: return dye(d).darker();
            case 165: return rgb(0x76be6d);
            case 168: return rgb(0x34574c);
            case 169: return rgb(0xbfd9d1);
            case 170: return rgb(0xb9a12b);
            case 172: return rgb(0x965d43);
            case 174: return rgb(0x9fb7f1);
            case 179: case 180: return rgb(0xb9612e);
            case 198: return rgb(0xf8f0ff);
            case 201: case 202: case 203: case 204: case 205: return rgb(0xa77ba7);
            case 206: return rgb(0xe2e6a9);
            case 208: return rgb(0x947c45);
            case 213: return rgb(0x8e3f1a);
            case 214: return rgb(0x75070a);
            case 215: return rgb(0x450709);
            case 216: return rgb(0xe1ddc9);
            case 251: case 252: return dye(d);
            case 9: case 8: return rgb(0x2f55d4);
            case 10: case 11: return rgb(0xd25b0c);
            case 79: return rgb(0x8db3ff);
            case 80: case 78: return rgb(0xf4fdfd);
            case 82: return rgb(0xa0a6b3);
            case 173: return rgb(0x151515);
            case 199: case 200: return rgb(0x5f3b60);
            case 61: case 58: return rgb(0x6d6d6d);
            case 117: return rgb(0x9a9a6a);
            case 66: return rgb(0x7a6a50);
            default: return rgb(0xb0b0b0);
        }
    }
    private static Color rgb(int x) { return new Color(x); }
    private static Color dye(int d) {
        int[] c = {0xe9ecec, 0xf07613, 0xbd44b3, 0x3aafd9, 0xf8c627, 0x70b919, 0xed8dac, 0x3e4447, 0x8e8e86, 0x158991, 0x792aac, 0x35399d, 0x724728, 0x546d1b, 0xa12722, 0x141519};
        return new Color(c[d & 15]);
    }
    private static Color glass(int id, int d) {
        Color base = id == 20 || id == 102 ? new Color(0xc8e6f0) : dye(d);
        return new Color(Math.min(255, base.getRed() / 2 + 100), Math.min(255, base.getGreen() / 2 + 100), Math.min(255, base.getBlue() / 2 + 110));
    }
}
