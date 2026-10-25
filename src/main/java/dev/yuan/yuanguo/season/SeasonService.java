package dev.yuan.yuanguo.season;

import dev.yuan.yuanguo.YuanGuoDelight;
import dev.yuan.yuanguo.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Soft bridge to RealisticSeasons.
 *
 * <p>Everything is resolved reflectively so the plugin keeps working (year round growth) when the
 * season plugin is missing, and so no hard compile time dependency is required.</p>
 *
 * <p>The API classes are looked up through the RealisticSeasons plugin's own class loader. A plain
 * {@code Class.forName} would use this plugin's loader, which cannot see another plugin's classes
 * under the Paper plugin loader, and would fail even though RealisticSeasons is installed.</p>
 */
public final class SeasonService implements Listener {

    private static final String PLUGIN_NAME = "RealisticSeasons";
    private static final String API_CLASS = "me.casperge.realisticseasons.api.SeasonsAPI";
    private static final String EVENT_CLASS = "me.casperge.realisticseasons.api.SeasonChangeEvent";
    private static final String SEASON_CLASS = "me.casperge.realisticseasons.season.Season";

    private static volatile SeasonService active;

    private final YuanGuoDelight plugin;
    /**
     * Cached seasons expire after this many milliseconds. The cache is refreshed by
     * {@code SeasonChangeEvent}, but the event class name belongs to another plugin: if it ever
     * moves, an entry without an expiry would pin the world to the season it had on first use.
     */
    private static final long CACHE_TTL_MILLIS = 30_000L;

    private final Map<World, CachedSeason> cache = new ConcurrentHashMap<>();

    private boolean enabled;
    private boolean detected;
    private Object api;
    private Method getSeasonMethod;
    private Method seasonNameMethod;

    public SeasonService(YuanGuoDelight plugin) {
        this.plugin = plugin;
    }

    /** @return the running service, or {@code null} when the season integration is unavailable. */
    public static SeasonService active() {
        SeasonService service = active;
        return service != null && service.isActive() ? service : null;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isDetected() {
        return this.detected;
    }

    public boolean isActive() {
        return this.enabled && this.detected && this.api != null;
    }

    public void start() {
        PluginSettings settings = this.plugin.settings();
        this.enabled = settings.seasonEnabled();

        Plugin seasonPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        boolean present = seasonPlugin != null && seasonPlugin.isEnabled();
        boolean shouldUse = this.enabled && present;
        if (settings.autoDetect() && present) {
            shouldUse = true;
            this.enabled = true;
        }
        if (!shouldUse) {
            this.detected = false;
            active = null;
            this.plugin.getLogger().info("RealisticSeasons not detected - fruits grow all year round.");
            return;
        }
        ClassLoader loader = seasonPlugin.getClass().getClassLoader();
        try {
            Class<?> apiClass = Class.forName(API_CLASS, true, loader);
            Method instance = apiClass.getMethod("getInstance");
            this.api = instance.invoke(null);
            if (this.api == null) {
                throw new IllegalStateException("SeasonsAPI.getInstance() returned null");
            }
            this.getSeasonMethod = apiClass.getMethod("getSeason", World.class);
            Class<?> seasonClass = Class.forName(SEASON_CLASS, false, loader);
            this.seasonNameMethod = seasonClass.getMethod("name");
            this.detected = true;
            active = this;
            registerChangeListener(loader);
            this.plugin.getLogger().info("RealisticSeasons detected - seasonal growth is active.");
        } catch (Throwable throwable) {
            this.detected = false;
            this.api = null;
            active = null;
            this.plugin.getLogger().warning("RealisticSeasons is installed but its API could not be hooked ("
                    + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())
                    + "); growing all year round.");
            this.plugin.getLogger().log(Level.FINE, "RealisticSeasons hook failure", throwable);
        }
    }

    public void stop() {
        active = null;
        this.cache.clear();
    }

    private void registerChangeListener(ClassLoader loader) {
        try {
            Class<?> eventClass = Class.forName(EVENT_CLASS, false, loader);
            if (!Event.class.isAssignableFrom(eventClass)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> typed = (Class<? extends Event>) eventClass;
            EventExecutor executor = (listener, event) -> {
                try {
                    Method getWorld = event.getClass().getMethod("getWorld");
                    World world = (World) getWorld.invoke(event);
                    if (world != null) {
                        Season season = readSeason(world);
                        if (season != null) {
                            this.cache.put(world, new CachedSeason(season, System.currentTimeMillis()));
                        } else {
                            this.cache.remove(world);
                        }
                        if (this.plugin.settings().notifySeasonChange()) {
                            this.plugin.getLogger().info("Season change in " + world.getName() + " -> " + season);
                        }
                    }
                } catch (Throwable ignored) {
                }
            };
            Bukkit.getPluginManager().registerEvent(typed, this, EventPriority.MONITOR, executor, this.plugin);
        } catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.FINE,
                    "SeasonChangeEvent is unavailable in this RealisticSeasons build.", throwable);
        }
    }

    /** @return the current season of the world, or {@code null} when it cannot be resolved. */
    public Season seasonOf(World world) {
        if (!isActive() || world == null) {
            return null;
        }
        CachedSeason cached = this.cache.get(world);
        if (cached != null && !cached.expired()) {
            return cached.season();
        }
        Season season = readSeason(world);
        if (season != null) {
            this.cache.put(world, new CachedSeason(season, System.currentTimeMillis()));
            if (this.plugin.settings().debug()) {
                this.plugin.getLogger().info("[debug] season of " + world.getName() + " is " + season);
            }
        } else {
            this.cache.remove(world);
            if (this.plugin.settings().debug()) {
                this.plugin.getLogger().warning("[debug] could not read the season of " + world.getName());
            }
        }
        return season;
    }

    /** A season read at a point in time, so a missed change event cannot freeze the world. */
    private record CachedSeason(Season season, long readAt) {

        boolean expired() {
            return System.currentTimeMillis() - this.readAt > CACHE_TTL_MILLIS;
        }
    }

    private Season readSeason(World world) {
        try {
            Object raw = this.getSeasonMethod.invoke(this.api, world);
            if (raw == null) {
                return null;
            }
            Object name = this.seasonNameMethod.invoke(raw);
            return Season.parse(String.valueOf(name));
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * @return {@code true} when plants requiring {@code required} may grow in {@code world} right now.
     */
    public boolean allows(World world, String required) {
        if (!isActive()) {
            return true;
        }
        Season requirement = Season.parse(required);
        if (requirement == Season.ALL) {
            return true;
        }
        Season current = seasonOf(world);
        if (current == null) {
            return true;
        }
        if (this.plugin.settings().restrictGrowth()) {
            return requirement.matches(current);
        }
        return true;
    }

    /** @return the growth speed multiplier that applies for {@code required} in {@code world}. */
    public double multiplier(World world, String required) {
        if (!isActive()) {
            return 1.0D;
        }
        Season requirement = Season.parse(required);
        if (requirement == Season.ALL) {
            return 1.0D;
        }
        Season current = seasonOf(world);
        if (current == null) {
            return 1.0D;
        }
        if (requirement.matches(current)) {
            return this.plugin.settings().inSeasonMultiplier();
        }
        // Out of season. With restrict-growth the behavior has already stopped the plant, so the
        // multiplier is never consulted; without it the plant keeps growing at this slower rate.
        return this.plugin.settings().outOfSeasonMultiplier();
    }

    public String describe(World world) {
        if (!isActive()) {
            return "disabled";
        }
        Season season = seasonOf(world);
        return season == null ? "unknown" : season.name().toLowerCase(Locale.ROOT);
    }
}
