package dev.yuan.yuanguo;

import dev.yuan.yuanguo.behavior.BehaviorRegistration;
import dev.yuan.yuanguo.command.YuanGuoCommand;
import dev.yuan.yuanguo.compat.PapersDelightSupport;
import dev.yuan.yuanguo.config.PluginConfig;
import dev.yuan.yuanguo.config.PluginSettings;
import dev.yuan.yuanguo.effect.EffectRegistry;
import dev.yuan.yuanguo.effect.EffectService;
import dev.yuan.yuanguo.pack.PackInstaller;
import dev.yuan.yuanguo.season.SeasonService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Yuan来是果 (Fruits Delight) ported to CraftEngine.
 *
 * <p>The content itself lives in the bundled CraftEngine pack under {@code pack/}. This plugin only
 * supplies what a data pack cannot express: season aware growth, fruit ripening, tree generation and
 * the helper commands around them.</p>
 */
public final class YuanGuoDelight extends JavaPlugin {

    private static YuanGuoDelight instance;

    private PluginConfig pluginConfig;
    private SeasonService seasonService;
    private EffectService effectService;
    private PapersDelightSupport papersDelight;

    public static YuanGuoDelight instance() {
        return instance;
    }

    public static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }

    @Override
    public void onLoad() {
        instance = this;
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.load();
        this.papersDelight = PapersDelightSupport.detect();
        if (this.papersDelight == null) {
            // Farmer's Delight is a hard requirement. Nothing is deployed and no behavior is
            // registered, so a server without it keeps a clean CraftEngine installation.
            getLogger().severe("PapersDelight (Farmer's Delight) is required by YuanGuoDelight, but it was not found.");
            getLogger().severe("Install PapersDelight, then restart the server. YuanGuoDelight will stay disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Detected PapersDelight through " + this.papersDelight.signal() + ".");
        // Deploy the content pack now: CraftEngine enables before this plugin, and it reads its
        // resource folders when it loads, so the files have to be in place before that happens.
        PackInstaller.install(this);
        BehaviorRegistration.registerAll(this);
        // NyanaFluid is optional. Its fluids are declared by the content pack (configuration/fluids.yml),
        // so this is only a presence report; the cauldron chain itself works on items either way.
        if (dev.yuan.yuanguo.cauldron.FluidSupport.available()) {
            getLogger().info("NyanaFluid found: the jam fluids and jug containers are available.");
        } else {
            getLogger().info("NyanaFluid was not found: the jam fluids and jug containers are unavailable.");
        }
    }

    @Override
    public void onEnable() {
        // The section registry is only complete now, so re-run the detection and let it override
        // whatever the load phase concluded.
        PapersDelightSupport detected = PapersDelightSupport.detect();
        if (detected != null) {
            this.papersDelight = detected;
        }
        if (this.papersDelight == null) {
            getLogger().severe("PapersDelight is required by YuanGuoDelight, but it was not found.");
            getLogger().severe("Install PapersDelight together with the Farmer's Delight content pack, then restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!PapersDelightSupport.hasRecipeSection()) {
            getLogger().warning("PapersDelight was found, but the '" + PapersDelightSupport.RECIPE_SECTION
                    + "' config section is not registered, so the cooking pot and cutting board recipes"
                    + " will not load. Check that the content pack is in"
                    + " plugins/CraftEngine/resources/ and run '/ce reload'.");
        }
        this.seasonService = new SeasonService(this);
        this.seasonService.start();

        // The effect table is generated with the pack, so it is read from the jar and the service
        // is registered even when the effects are disabled: the listeners stay cheap and the
        // command can still report the state.
        this.effectService = new EffectService(this, EffectRegistry.load(this));
        getServer().getPluginManager().registerEvents(this.effectService, this);
        this.effectService.start();

        // Feasts are answered at CraftEngine's earliest interaction hook; see the listener javadoc.
        getServer().getPluginManager().registerEvents(
                new dev.yuan.yuanguo.behavior.FeastInteractListener(), this);
        // Saplings are planted into vanilla flower pots by hand; see the listener javadoc.
        getServer().getPluginManager().registerEvents(
                new dev.yuan.yuanguo.behavior.PottedSaplingListener(), this);
        // Paper plugins must register commands in code; paper-plugin.yml has no commands block.
        if (settings().cauldron()) {
            getServer().getPluginManager().registerEvents(
                    new dev.yuan.yuanguo.cauldron.CauldronListener(), this);
        }

        // Paper plugins must register commands in code; paper-plugin.yml has no commands block.
        registerCommand("yuanguo", "Yuan来是果管理命令", new YuanGuoCommand(this));

        PluginSettings settings = settings();
        getLogger().info("YuanGuoDelight enabled (season integration: "
                + (settings.seasonEnabled() ? "enabled" : "disabled")
                + ", auto-detect: " + settings.autoDetect() + ").");
    }

    @Override
    public void onDisable() {
        if (this.effectService != null) {
            this.effectService.stop();
        }
        if (this.seasonService != null) {
            this.seasonService.stop();
        }
        getLogger().info("YuanGuoDelight disabled.");
    }

    public PluginSettings settings() {
        return this.pluginConfig.settings();
    }

    public SeasonService seasons() {
        return this.seasonService;
    }

    public EffectService effects() {
        return this.effectService;
    }

    /** How PapersDelight was located, or {@code null} when it is absent. */
    public PapersDelightSupport papersDelight() {
        return this.papersDelight;
    }

    /** Re-reads {@code config.yml} and restarts the season bridge. */
    public void reloadEverything() {
        this.pluginConfig.load();
        if (this.seasonService != null) {
            this.seasonService.stop();
            // The bridge registers a season change listener through the plugin manager, which is
            // not tied to the service instance: unregister it before dropping the old service, or
            // every reload leaves another dead listener behind.
            HandlerList.unregisterAll(this.seasonService);
        }
        this.seasonService = new SeasonService(this);
        this.seasonService.start();
        if (this.effectService != null) {
            this.effectService.stop();
            HandlerList.unregisterAll(this.effectService);
            this.effectService = new EffectService(this, EffectRegistry.load(this));
            getServer().getPluginManager().registerEvents(this.effectService, this);
            this.effectService.start();
        }
    }
}
