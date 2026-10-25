package dev.yuan.yuanguo.compat;

import net.momirealms.craftengine.core.pack.PackManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Locates PapersDelight without relying on the plugin being called {@code PapersDelight}.
 *
 * <p>The official builds are shipped obfuscated, so the Java package and the API class are only
 * usable as hints: the distributed jar keeps neither. The signal that does survive obfuscation is
 * the CraftEngine config section PapersDelight registers, because that name is a semantic
 * identifier CraftEngine itself matches on.</p>
 *
 * <p>Signals, cheapest first:</p>
 * <ol>
 *   <li>The plugin's own main class sits in the PapersDelight package (source builds).</li>
 *   <li>Its class loader exposes the PapersDelight API class (source builds).</li>
 *   <li>A plugin is literally named {@code PapersDelight}.</li>
 *   <li>Its jar contains {@code dev/tako/papersdelight/} entries (source builds).</li>
 *   <li>CraftEngine reports {@code papersdelight_recipes} as a registered config section. This is
 *       the real capability test and the only one an obfuscated build still passes.</li>
 * </ol>
 *
 * <p>Signal 5 is only observable once PapersDelight has finished loading, so {@link #detect()} is
 * run again on enable and the later result wins.</p>
 */
public final class PapersDelightSupport {

    /** Java package a non-obfuscated PapersDelight build keeps. */
    public static final String PACKAGE = "dev.tako.papersdelight";
    private static final String PACKAGE_PATH = "dev/tako/papersdelight/";
    private static final String API_CLASS = "dev.tako.papersdelight.api.PapersDelightApi";
    private static final String PLUGIN_NAME = "PapersDelight";

    /** Config section that carries the cooking pot and cutting board recipes. */
    public static final String RECIPE_SECTION = "papersdelight_recipes";
    /** Config section that carries the advanced tag definitions. */
    public static final String ADVANCED_TAG_SECTION = "advanced_tags";

    private final Plugin plugin;
    private final String signal;

    private PapersDelightSupport(@Nullable Plugin plugin, String signal) {
        this.plugin = plugin;
        this.signal = signal;
    }

    /**
     * @return the detected installation, or {@code null} when nothing indicates PapersDelight.
     */
    @Nullable
    public static PapersDelightSupport detect() {
        Plugin byPackage = null;
        Plugin byApi = null;
        Plugin byJar = null;
        Plugin byName = null;
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (byPackage == null && mainClassInPackage(candidate)) {
                byPackage = candidate;
            }
            if (byApi == null && exposesApi(candidate)) {
                byApi = candidate;
            }
            if (byName == null && PLUGIN_NAME.equalsIgnoreCase(candidate.getName())) {
                byName = candidate;
            }
            // Scanning jars is the most expensive check, so it only runs when the cheap ones miss.
            if (byJar == null && byPackage == null && byApi == null && jarContainsPackage(candidate)) {
                byJar = candidate;
            }
        }
        if (byPackage != null) {
            return new PapersDelightSupport(byPackage,
                    "package '" + PACKAGE + "' of plugin '" + byPackage.getName() + "'");
        }
        if (byApi != null) {
            return new PapersDelightSupport(byApi, "API class in plugin '" + byApi.getName() + "'");
        }
        if (byName != null) {
            return new PapersDelightSupport(byName, "plugin name '" + PLUGIN_NAME + "'");
        }
        if (byJar != null) {
            return new PapersDelightSupport(byJar,
                    "package '" + PACKAGE + "' inside the jar of plugin '" + byJar.getName() + "'");
        }
        if (hasRecipeSection()) {
            return new PapersDelightSupport(null, "CraftEngine section '" + RECIPE_SECTION + "'");
        }
        return null;
    }

    /** The plugin the signals resolved to, if any. May be {@code null} for a section-only match. */
    @Nullable
    public Plugin plugin() {
        return this.plugin;
    }

    /** Human readable description of what matched, for the log and {@code /yuanguo status}. */
    public String signal() {
        return this.signal;
    }

    /** Whether the recipes this pack ships can actually be loaded. */
    public static boolean hasRecipeSection() {
        return hasSection(RECIPE_SECTION);
    }

    /**
     * The plugin's own main class sits in the PapersDelight package.
     *
     * <p>The comparison is on the package boundary, so a hypothetical
     * {@code dev.tako.papersdelightaddon} plugin does not match.</p>
     */
    private static boolean mainClassInPackage(Plugin plugin) {
        try {
            String name = plugin.getClass().getName();
            return name.equals(PACKAGE) || name.startsWith(PACKAGE + ".");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Whether a plugin exposes the PapersDelight API to its own class loader.
     *
     * <p>The class is looked up without initializing it, so this stays a pure presence test.</p>
     */
    private static boolean exposesApi(Plugin plugin) {
        try {
            Class.forName(API_CLASS, false, plugin.getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Whether the plugin's jar contains entries under the PapersDelight package. */
    private static boolean jarContainsPackage(Plugin plugin) {
        try {
            URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return false;
            }
            Path path = Paths.get(location.toURI());
            if (!Files.isRegularFile(path)) {
                return false;
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(PACKAGE_PATH)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    /**
     * Asks CraftEngine which config sections are registered.
     *
     * <p>CraftEngine exposes no accessor for this, so the pack manager's private section map is read
     * reflectively. The lookup is read only; if a future CraftEngine moves the field the check
     * degrades to {@code false} rather than throwing.</p>
     */
    public static boolean hasSection(String id) {
        return registeredSections().contains(id);
    }

    private static Set<String> registeredSections() {
        try {
            PackManager manager = CraftEngine.instance().packManager();
            if (manager == null) {
                return Set.of();
            }
            for (Class<?> type = manager.getClass(); type != null; type = type.getSuperclass()) {
                Field field = findSectionField(type);
                if (field == null) {
                    continue;
                }
                Object value = field.get(manager);
                if (value instanceof Map<?, ?> map) {
                    Set<String> ids = new LinkedHashSet<>();
                    for (Object key : map.keySet()) {
                        if (key != null) {
                            ids.add(String.valueOf(key));
                        }
                    }
                    return Collections.unmodifiableSet(ids);
                }
            }
        } catch (Throwable ignored) {
            // CraftEngine not ready, or the field moved. Either way there is nothing to report.
        }
        return Set.of();
    }

    private static Field findSectionField(Class<?> type) {
        for (String name : new String[] {"sectionParsers", "SECTION_PARSERS"}) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                // Try the next candidate name.
            }
        }
        return null;
    }
}