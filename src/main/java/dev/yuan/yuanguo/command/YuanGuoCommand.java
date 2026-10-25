package dev.yuan.yuanguo.command;

import dev.yuan.yuanguo.YuanGuoDelight;
import dev.yuan.yuanguo.compat.PapersDelightSupport;
import dev.yuan.yuanguo.effect.CustomEffect;
import dev.yuan.yuanguo.effect.EffectService;
import dev.yuan.yuanguo.effect.EffectState;
import dev.yuan.yuanguo.effect.FoodEffect;
import dev.yuan.yuanguo.season.SeasonService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /yuanguo} administration command.
 *
 * <p>Paper plugins cannot declare commands in {@code paper-plugin.yml}, so this implements the
 * Brigadier {@link BasicCommand} and is handed to {@code JavaPlugin#registerCommand}.</p>
 */
public final class YuanGuoCommand implements BasicCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#7FD9A8");
    private static final TextColor TEXT = TextColor.fromHexString("#E6E6E6");
    private static final TextColor MUTED = TextColor.fromHexString("#8C949C");
    private static final TextColor DIM = TextColor.fromHexString("#4A5058");
    private static final TextColor GOOD = TextColor.fromHexString("#7BE38A");
    private static final TextColor GOLD = TextColor.fromHexString("#FFD479");
    private static final TextColor BAD = TextColor.fromHexString("#FF7B7B");
    private static final TextColor AUTUMN = TextColor.fromHexString("#E8A25C");
    private static final TextColor WINTER = TextColor.fromHexString("#8FC7FF");

    private static final List<String> SUBCOMMANDS = List.of("reload", "status", "season", "effects");
    private static final String RULE = "──────────────────────────────";

    private final YuanGuoDelight plugin;

    public YuanGuoCommand(YuanGuoDelight plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            overview(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "season" -> season(sender);
            case "effects" -> effects(sender);
            default -> unknown(sender, args[0]);
        }
    }

    private void overview(CommandSender sender) {
        header(sender, "命令总览");
        send(sender, field("可用命令", Component.text(SUBCOMMANDS.size() + " 项", TEXT)));
        send(sender, Component.empty());
        entry(sender, "/yuanguo reload", "重新加载配置、季节联动与状态效果");
        entry(sender, "/yuanguo status", "查看插件与季节联动的运行状态");
        entry(sender, "/yuanguo season", "查看当前世界的季节");
        entry(sender, "/yuanguo effects", "查看自身状态效果与主手物品效果");
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        this.plugin.reloadEverything();
        header(sender, "重载");
        send(sender, field("配置", Component.text("✔ 已重新加载", GOOD)));
        send(sender, field("季节联动", Component.text("✔ 已重启", GOOD)));
        send(sender, field("状态效果", Component.text("✔ 已重启", GOOD)));
    }

    private void status(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        SeasonService seasons = this.plugin.seasons();
        header(sender, "运行状态");
        send(sender, field("农夫乐事", this.plugin.papersDelight() == null
                ? Component.text("✘ 未找到", BAD)
                : Component.text("✔ " + this.plugin.papersDelight().signal(), GOOD)));
        send(sender, field("配方章节", flag(PapersDelightSupport.hasRecipeSection())));
        send(sender, field("状态效果", flag(this.plugin.settings().effectsEnabled())));
        send(sender, field("计时条", flag(this.plugin.settings().effectBossBar())));
        send(sender, field("季节联动", flag(this.plugin.settings().seasonEnabled())));
        send(sender, field("自动检测", flag(this.plugin.settings().autoDetect())));
        send(sender, field("限制生长", flag(this.plugin.settings().restrictGrowth())));
        send(sender, field("季节插件", flag(seasons != null && seasons.isDetected())));
        send(sender, field("联动生效", flag(seasons != null && seasons.isActive())));
        send(sender, field("生长倍率", Component.text("当季 x" + this.plugin.settings().inSeasonMultiplier(), TEXT)
                .append(Component.text("  非当季 x" + this.plugin.settings().outOfSeasonMultiplier(), MUTED))));
        if (sender instanceof Player player && seasons != null && seasons.isActive()) {
            send(sender, Component.empty());
            send(sender, field("当前世界", Component.text(player.getWorld().getName(), TEXT)));
            send(sender, field("当前季节", seasonName(seasons.describe(player.getWorld()))));
        }
    }

    private void season(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            header(sender, "季节");
            send(sender, field("提示", Component.text("该子命令只能由玩家执行", MUTED)));
            return;
        }
        SeasonService seasons = this.plugin.seasons();
        header(sender, "季节");
        send(sender, field("当前世界", Component.text(player.getWorld().getName(), TEXT)));
        if (seasons == null || !seasons.isActive()) {
            send(sender, field("当前季节", Component.text("✘ 季节联动未启用，果实全年生长", MUTED)));
            return;
        }
        send(sender, field("当前季节", seasonName(seasons.describe(player.getWorld()))));
        send(sender, field("生长倍率", Component.text("当季 x" + this.plugin.settings().inSeasonMultiplier(), TEXT)
                .append(Component.text("  非当季 x" + this.plugin.settings().outOfSeasonMultiplier(), MUTED))));
    }

    /** Lists the effects the player carries and the ones the held item would grant. */
    private void effects(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            header(sender, "状态效果");
            send(sender, field("提示", Component.text("该子命令只能由玩家执行", MUTED)));
            return;
        }
        EffectService effects = this.plugin.effects();
        header(sender, "状态效果");
        if (effects == null) {
            send(sender, field("状态效果", Component.text("✘ 系统未启用", BAD)));
            return;
        }
        Map<CustomEffect, EffectState.Active> active = effects.state().of(player);
        send(sender, field("当前生效", active.isEmpty()
                ? Component.text("无", MUTED)
                : Component.text(active.size() + " 项", TEXT)));
        for (Map.Entry<CustomEffect, EffectState.Active> entry : active.entrySet()) {
            send(sender, effectLine(entry.getKey(), entry.getValue()));
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        List<FoodEffect> granted = effects.effectsOfItem(held);
        send(sender, Component.empty());
        send(sender, field("主手物品", held.getType().isAir()
                ? Component.text("空", MUTED)
                : held.displayName()));
        if (granted.isEmpty()) {
            send(sender, Component.text("  │ ", DIM)
                    .append(Component.text("该物品不会给予状态效果", MUTED)));
        } else {
            for (FoodEffect effect : granted) {
                send(sender, foodEffectLine(effect));
            }
        }
    }

    private void unknown(CommandSender sender, String input) {
        header(sender, "命令总览");
        send(sender, field("未知子命令", Component.text(input, BAD)));
        send(sender, field("可用命令", Component.text(String.join("  ", SUBCOMMANDS), MUTED)));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("yuanguo.admin")) {
            return true;
        }
        header(sender, "权限不足");
        send(sender, field("需要权限", Component.text("yuanguo.admin", GOLD)));
        return false;
    }

    // ------------------------------------------------------------------ presentation

    private static void header(CommandSender sender, String subtitle) {
        send(sender, Component.text("◆ ", ACCENT)
                .append(Component.text("Yuan来是果", TEXT, TextDecoration.BOLD))
                .append(Component.text("  ·  " + subtitle, ACCENT)));
        send(sender, Component.text("  " + RULE, DIM));
    }

    private static Component field(String label, Component value) {
        return Component.text("  │ ", DIM)
                .append(Component.text(pad(label), MUTED))
                .append(value);
    }

    /** Pads a label to a fixed column, counting CJK characters as two columns wide. */
    private static String pad(String label) {
        int width = 0;
        for (int index = 0; index < label.length(); index++) {
            char character = label.charAt(index);
            width += character > 0x2E80 ? 2 : 1;
        }
        return label + " ".repeat(Math.max(1, 11 - width));
    }

    private static void entry(CommandSender sender, String command, String description) {
        Component line = Component.text("  │ ", DIM)
                .append(Component.text("▸ ", ACCENT))
                .append(Component.text(command, GOLD))
                .append(Component.text("   " + description, MUTED))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("点击执行 " + command, TEXT)));
        send(sender, line);
    }

    private static Component flag(boolean enabled) {
        return enabled ? Component.text("✔ 已启用", GOOD) : Component.text("✘ 已关闭", MUTED);
    }

    private static Component effectLine(CustomEffect effect, EffectState.Active active) {
        Component name = Component.translatable(effect.nameKey())
                .color(effect.harmful() ? BAD : GOLD)
                .decoration(TextDecoration.BOLD, true);
        Component line = Component.text("  │ ", DIM)
                .append(Component.text("▸ ", ACCENT))
                .append(name)
                .append(Component.text(" " + roman(active.amplifier() + 1), MUTED))
                .append(Component.text("   ", DIM))
                .append(progress(active.remainingTicks(), active.totalTicks()))
                .append(Component.text(" " + clock(active.remainingTicks()), TEXT));
        return line.hoverEvent(HoverEvent.showText(
                Component.translatable(effect.descriptionKey()).color(TEXT)));
    }

    private static Component foodEffectLine(FoodEffect effect) {
        Component name;
        if (effect.isVanilla()) {
            PotionEffectType type = effect.type();
            name = type == null ? Component.text(effect.id(), GOLD)
                    : Component.translatable(type.translationKey()).color(GOLD);
        } else {
            CustomEffect custom = effect.custom();
            name = custom == null ? Component.text(effect.id(), GOLD)
                    : Component.translatable(custom.nameKey()).color(GOLD);
        }
        Component line = Component.text("  │ ", DIM)
                .append(Component.text("▸ ", ACCENT))
                .append(name)
                .append(Component.text(" " + roman(effect.amplifier() + 1), MUTED))
                .append(Component.text("  ·  ", DIM))
                .append(Component.text(seconds(effect.duration()) + " 秒", TEXT));
        if (effect.chance() < 1.0D) {
            line = line.append(Component.text("  ·  ", DIM))
                    .append(Component.text(Math.round(effect.chance() * 100.0D) + "%", MUTED));
        }
        return line;
    }

    private static Component progress(int remaining, int total) {
        int segments = 10;
        int filled = total <= 0 ? 0
                : (int) Math.round(segments * Math.max(0.0D, Math.min(1.0D, remaining / (double) total)));
        Component bar = Component.empty();
        for (int index = 0; index < segments; index++) {
            bar = bar.append(Component.text("●", index < filled ? ACCENT : DIM));
        }
        return bar;
    }

    private static Component seasonName(String raw) {
        if (raw == null) {
            return Component.text("未知", MUTED);
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "spring" -> Component.text("❀ 春季", GOOD);
            case "summer" -> Component.text("☀ 夏季", GOLD);
            case "fall", "autumn" -> Component.text("❖ 秋季", AUTUMN);
            case "winter" -> Component.text("❄ 冬季", WINTER);
            default -> Component.text("未知", MUTED);
        };
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
    }

    private static int seconds(int ticks) {
        return Math.max(0, ticks) / 20;
    }

    private static String clock(int ticks) {
        int seconds = seconds(ticks);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    /** Sends through Audience explicitly, so the deprecated Bungee overload is never selected. */
    private static void send(CommandSender sender, Component message) {
        ((net.kyori.adventure.audience.Audience) sender).sendMessage(message);
    }

    // ------------------------------------------------------------------ brigadier

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String option : SUBCOMMANDS) {
                if (option.startsWith(prefix)) {
                    options.add(option);
                }
            }
            return options;
        }
        return List.of();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return true;
    }
}