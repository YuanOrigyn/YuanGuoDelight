package dev.yuan.yuanguo.pack;

import dev.yuan.yuanguo.YuanGuoDelight;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * <p>The pack is deployed during {@code onLoad}. CraftEngine enables before this plugin, so writing
 * the files here means they are already on disk when CraftEngine loads its resources.</p>
 *
 * <p>A fingerprint marker in this plugin's own data folder keeps administrator edits intact: an
 * existing installation is only rewritten when the bundled archive changes, unless overwriting is
 * forced. The marker alone is not trusted though - if the pack folder was deleted or emptied while
 * the marker survived, the content is written again.</p>
 */
public final class PackInstaller {

    private static final String CE_ARCHIVE = "pack.zip";
    private static final String CE_FOLDER = "YuanGuoDelight";
    private static final String CE_MARKER = "ce-pack.fingerprint";

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
        install(plugin, CE_ARCHIVE, target, CE_MARKER, "CraftEngine pack");
    }

    private static void install(YuanGuoDelight plugin, String archive, Path target, String markerName,
                                String label) {
        Path marker = plugin.getDataFolder().toPath().resolve(markerName);
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
            int extracted = extract(plugin, archive, target);
            if (extracted <= 0) {
                return;
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, fingerprint);
            plugin.getLogger().info("Installed the " + label + " (" + extracted + " files) into " + target
                    + ". Run '/ce reload' or restart the server to load it.");
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

    private static int extract(YuanGuoDelight plugin, String archive, Path target) throws IOException {
        try (InputStream raw = plugin.getResource(archive)) {
            if (raw == null) {
                plugin.getLogger().warning("The bundled " + archive + " is missing from the jar.");
                return 0;
            }
            int count = 0;
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
                    count++;
                }
            }
            return count;
        }
    }
}