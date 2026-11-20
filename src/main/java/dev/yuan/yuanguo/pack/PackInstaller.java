package dev.yuan.yuanguo.pack;

import dev.yuan.yuanguo.YuanGuoDelight;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the bundled content.
 *
 * <p>The jar carries a single archive, {@code pack.zip}. It holds the whole CraftEngine content
 * pack, including the cooking pot and cutting board recipes, which PapersDelight registers as the
 * {@code papersdelight_recipes} config section. CraftEngine only reads packs from
 * {@code plugins/CraftEngine/resources/<pack>}, so that is where it is unpacked.</p>
 *
 * <p>The pack is deployed during {@code onLoad}. CraftEngine loads its resources on enable, which
 * happens after this plugin's load phase, so a restart is enough to pick the files up.</p>
 *
 * <p>A fingerprint marker in this plugin's own data folder keeps an unchanged installation from
 * being rewritten on every start. When the bundled archive changes - a new build, edited content -
 * the deployment is refreshed and any file that the previous deployment created but the new archive
 * no longer contains is removed, because CraftEngine loads every yml below the pack folder and a
 * stale file would keep being parsed. Files an administrator added by hand are left alone: only the
 * names recorded in the manifest of the previous deployment are candidates for deletion. Note that
 * an update still overwrites the files it ships, so local edits to those files are not preserved.</p>
 */
public final class PackInstaller {

    private static final String CE_ARCHIVE = "pack.zip";
    private static final String CE_FOLDER = "YuanGuoDelight";
    private static final String CE_MARKER = "ce-pack.fingerprint";
    private static final String CE_MANIFEST = "ce-pack.files";

    /** Present in every CraftEngine pack; used to tell an intact installation from a removed one. */
    private static final String PACK_ENTRY = "pack.yml";

    private PackInstaller() {
    }

    public static void install(YuanGuoDelight plugin) {
        if (!plugin.settings().autoInstallPack()) {
            return;
        }
        installCraftEnginePack(plugin);
    }

    private static void installCraftEnginePack(YuanGuoDelight plugin) {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) {
            plugin.getLogger().warning("CraftEngine is not installed; the content pack was not deployed.");
            return;
        }
        Path target = craftEngine.getDataFolder().toPath().resolve("resources").resolve(CE_FOLDER);
        install(plugin, CE_ARCHIVE, target, CE_MARKER, CE_MANIFEST, "CraftEngine pack");
    }

    private static void install(YuanGuoDelight plugin, String archive, Path target, String markerName,
                                String manifestName, String label) {
        Path marker = plugin.getDataFolder().toPath().resolve(markerName);
        Path manifest = plugin.getDataFolder().toPath().resolve(manifestName);
        try {
            // Fingerprinting the archive rather than trusting the plugin version means a rebuilt
            // pack always replaces the deployed copy, even when the version string is unchanged.
            String fingerprint = fingerprint(plugin, archive);
            if (fingerprint == null) {
                return;
            }
            // The marker is written after a successful extraction, so it can outlive the files it
            // describes (a deleted pack folder, a wiped CraftEngine resources directory, ...).
            // Checking the pack itself as well means such an installation is repaired instead of
            // being skipped as up to date.
            boolean present = Files.isRegularFile(target.resolve(PACK_ENTRY));
            if (!plugin.settings().packOverwrite() && present && Files.exists(marker)) {
                String recorded = Files.readString(marker).trim();
                if (fingerprint.equals(recorded)) {
                    return;
                }
                plugin.getLogger().info("Updating the " + label + ".");
            } else if (!present) {
                plugin.getLogger().info("The " + label + " is not present in " + target + "; deploying it.");
            }
            List<String> extracted = extract(plugin, archive, target);
            if (extracted.isEmpty()) {
                return;
            }
            pruneStale(target, readManifest(manifest), new LinkedHashSet<>(extracted));
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, fingerprint);
            Files.write(manifest, extracted);
            plugin.getLogger().info("Installed the " + label + " (" + extracted.size() + " files) into "
                    + target + ". Run '/ce reload' or restart the server to load it.");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not install the " + label + " into " + target + ": "
                    + exception.getMessage());
        }
    }

    /** CRC32 and length of the bundled archive, or {@code null} when it cannot be read. */
    private static String fingerprint(YuanGuoDelight plugin, String archive) throws IOException {
        try (InputStream raw = plugin.getResource(archive)) {
            if (raw == null) {
                plugin.getLogger().warning("The bundled " + archive + " is missing from the jar.");
                return null;
            }
            CRC32 crc = new CRC32();
            long length = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = raw.read(buffer)) > 0) {
                crc.update(buffer, 0, read);
                length += read;
            }
            return Long.toHexString(crc.getValue()) + "-" + length;
        }
    }

    /** @return the relative names of every file written, sorted, for the manifest. */
    private static List<String> extract(YuanGuoDelight plugin, String archive, Path target) throws IOException {
        try (InputStream raw = plugin.getResource(archive)) {
            if (raw == null) {
                plugin.getLogger().warning("The bundled " + archive + " is missing from the jar.");
                return List.of();
            }
            List<String> names = new ArrayList<>();
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path destination = target.resolve(entry.getName()).normalize();
                    if (!destination.startsWith(target)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                    names.add(entry.getName().replace('\\', '/'));
                }
            }
            names.sort(Comparator.naturalOrder());
            return names;
        }
    }

    /** @return the file names recorded by the previous deployment, ignoring anything unusable. */
    private static Set<String> readManifest(Path manifest) {
        Set<String> names = new LinkedHashSet<>();
        if (!Files.isRegularFile(manifest)) {
            return names;
        }
        try {
            for (String line : Files.readAllLines(manifest)) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    names.add(name);
                }
            }
        } catch (IOException ignored) {
            // A corrupt manifest only means no stale file is removed this time.
        }
        return names;
    }

    /**
     * Removes files the previous deployment created that the new archive does not contain. Only the
     * names from the old manifest are considered, so files added by hand survive an update.
     */
    private static void pruneStale(Path target, Set<String> previous, Set<String> current) {
        for (String name : previous) {
            if (current.contains(name)) {
                continue;
            }
            Path path = target.resolve(name).normalize();
            if (!path.startsWith(target) || !Files.isRegularFile(path)) {
                continue;
            }
            try {
                Files.delete(path);
            } catch (IOException ignored) {
                // Keep going: one locked file must not stop the whole deployment.
            }
        }
        pruneEmptyDirectories(target);
    }

    private static void pruneEmptyDirectories(Path target) {
        if (!Files.isDirectory(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            List<Path> directories = walk.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path directory : directories) {
                if (directory.equals(target)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(directory)) {
                    if (children.findAny().isEmpty()) {
                        Files.deleteIfExists(directory);
                    }
                }
            }
        } catch (IOException ignored) {
            // Best effort only.
        }
    }
}
