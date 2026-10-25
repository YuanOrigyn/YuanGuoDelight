package dev.yuan.yuanguo.cauldron;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Presence check for NyanaFluid, the fluid library Farmer's Delight 1.2.4 uses.
 *
 * <p>The jam fluids and their bottles are declared by the content pack in
 * {@code configuration/fluids.yml}, so nothing has to be registered from Java. This class only
 * reports whether the library is installed, which keeps the cauldron chain working on a server
 * without it: jam is still a plain item there, it just cannot be poured into a jug.</p>
 *
 * <p>The API class is looked up in the library's own class loader without initializing it. That
 * avoids both a hard compile-time dependency and a {@code NoClassDefFoundError} when the plugin is
 * missing.</p>
 */
public final class FluidSupport {

    private static final String PLUGIN_NAME = "NyanaFluid";
    private static final String API_CLASS = "net.nyana.fluid.api.NyanaFluid";

    private FluidSupport() {
    }

    public static boolean available() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin == null || !plugin.isEnabled()) {
                return false;
            }
            Class.forName(API_CLASS, false, plugin.getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}