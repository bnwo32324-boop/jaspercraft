package jaspr.audit.harness;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

/**
 * Why this exists: the register set pieces fill their chests during generation, and
 * chat.jaspr.biomes.ExpeditionLoot.rareWeapon(r) draws from the SAME Random that places the
 * structure's blocks -- but only when a plugin named "JasprApocalypse" is enabled (live: always,
 * once the server is up). Without it no number is drawn, every later block decision of that
 * structure shifts, and the capture would not be what the live server builds.
 *
 * The real JasprApocalypse needs AuthMe and would drag half the live server in. It is not needed:
 * rareWeapon only calls the static item factory chat.jaspr.apocalypse.ApocalypseItems through
 * getPlugin("JasprApocalypse").getClass().getClassLoader(). So the harness registers a stand-in
 * Plugin (a dynamic proxy defined in a class loader over a read-only copy of the live
 * JasprApocalypse.jar in the test server's lib\ folder, NOT in plugins\): isPluginEnabled() is true
 * and the real ApocalypseItems/Arsenal classes build the real items. Nothing of JasprApocalypse is
 * enabled or run (no listeners, no tasks, no config).
 *
 * mode "real" = as above; "stub" = same stand-in but the harness' own StubItems class (placeholder
 * item) -- same random draws, placeholder guns; "off" = no stand-in (NOT live-faithful).
 */
final class ApocalypseShim {
    private ApocalypseShim() { }
    static Plugin proxy;
    static String status = "off";

    static String install(File serverDir, String mode, Logger log) {
        if (mode == null || mode.equals("off")) { status = "off"; return status; }
        try {
            ClassLoader parent = Bukkit.class.getClassLoader();
            ClassLoader loader;
            if (mode.equals("real")) {
                File jar = new File(new File(serverDir, "lib"), "JasprApocalypse.jar");
                if (!jar.isFile()) throw new IllegalStateException("missing " + jar);
                loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, parent);
                // Fail now, not inside world generation: the factory must be loadable.
                Class.forName("chat.jaspr.apocalypse.ApocalypseItems", true, loader);
            } else {
                loader = new StubLoader(ApocalypseShim.class.getClassLoader());
            }
            final PluginDescriptionFile desc = new PluginDescriptionFile("JasprApocalypse", "shim-" + mode,
                    "jaspr.audit.harness.ApocalypseShim");
            InvocationHandler h = (p, m, a) -> {
                switch (m.getName()) {
                    case "getName": return "JasprApocalypse";
                    case "isEnabled": return Boolean.TRUE;
                    case "getDescription": return desc;
                    case "isNaggable": return Boolean.FALSE;
                    case "toString": return "JasprApocalypse(capture-harness shim " + mode + ")";
                    case "hashCode": return System.identityHashCode(p);
                    case "equals": return p == a[0];
                    default:
                        Class<?> r = m.getReturnType();
                        if (r == boolean.class) return Boolean.FALSE;
                        if (r == int.class) return 0;
                        if (r == long.class) return 0L;
                        return null;
                }
            };
            proxy = (Plugin) Proxy.newProxyInstance(loader, new Class<?>[]{Plugin.class}, h);
            PluginManager pm = Bukkit.getPluginManager();
            plugins(pm).add(proxy);
            // Paper keys lookupNames by the lower-cased name, CraftBukkit by the name itself.
            names(pm).put("jasprapocalypse", proxy);
            names(pm).put("JasprApocalypse", proxy);
            if (!pm.isPluginEnabled("JasprApocalypse")) throw new IllegalStateException("shim not visible");
            status = mode;
        } catch (Throwable t) {
            log.severe("CAPTURE_SHIM_FAILED mode=" + mode + " " + t);
            status = "failed:" + t.getClass().getSimpleName();
        }
        return status;
    }

    /** Takes the stand-in out again, before shutdown, so the server does not try to disable it. */
    static void remove() {
        if (proxy == null) return;
        try {
            PluginManager pm = Bukkit.getPluginManager();
            plugins(pm).remove(proxy);
            names(pm).remove("jasprapocalypse");
            names(pm).remove("JasprApocalypse");
        } catch (Throwable ignored) { }
        proxy = null;
    }

    @SuppressWarnings("unchecked")
    private static List<Plugin> plugins(PluginManager pm) throws ReflectiveOperationException {
        Field f = pm.getClass().getDeclaredField("plugins");
        f.setAccessible(true);
        return (List<Plugin>) f.get(pm);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Plugin> names(PluginManager pm) throws ReflectiveOperationException {
        Field f = pm.getClass().getDeclaredField("lookupNames");
        f.setAccessible(true);
        return (Map<String, Plugin>) f.get(pm);
    }

    /** "stub" mode: serves chat.jaspr.apocalypse.ApocalypseItems from the harness' StubItems. */
    static final class StubLoader extends ClassLoader {
        StubLoader(ClassLoader parent) { super(parent); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals("chat.jaspr.apocalypse.ApocalypseItems")) throw new ClassNotFoundException(name);
            try (java.io.InputStream in = getParent().getResourceAsStream("jaspr/audit/harness/stub/ApocalypseItems.bin")) {
                if (in == null) throw new ClassNotFoundException(name);
                byte[] b = in.readAllBytes();
                return defineClass(name, b, 0, b.length);
            } catch (java.io.IOException e) { throw new ClassNotFoundException(name, e); }
        }
    }
}
