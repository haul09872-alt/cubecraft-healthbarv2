package dev.cchb.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.cchb.CcHealthBarMod;
import dev.cchb.config.ModConfig;
import dev.cchb.server.ServerGate;
import dev.cchb.track.TargetTracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /cchb ... : config + a read-only probe that shows what the server actually sends. */
public final class HealthBarCommand {
    private HealthBarCommand() { }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("cchb")
                .executes(ctx -> help(ctx.getSource()))
                .then(literal("toggle").executes(ctx -> {
                    boolean now = !ModConfig.get().enabled;
                    msg(ctx.getSource(), "enabled = " + ModConfig.set("enabled", Boolean.toString(now)).endsWith("true"));
                    return 1;
                }))
                .then(literal("list").executes(ctx -> {
                    msg(ctx.getSource(), ModConfig.describe());
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    ModConfig.load();
                    ServerGate.reset();
                    TargetTracker.clear();
                    msg(ctx.getSource(), "Config reloaded.");
                    return 1;
                }))
                .then(literal("debug").executes(ctx -> {
                    boolean now = !ModConfig.get().debug;
                    ModConfig.set("debug", Boolean.toString(now));
                    msg(ctx.getSource(), "debug = " + now);
                    return 1;
                }))
                .then(literal("probe").executes(ctx -> probe(ctx.getSource())))
                .then(literal("set")
                        .then(argument("key", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ModConfig.keys(), b))
                                .then(argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            try {
                                                String r = ModConfig.set(
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"));
                                                ServerGate.reset();
                                                msg(ctx.getSource(), r);
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                msg(ctx.getSource(), e.getMessage());
                                                return 0;
                                            }
                                        })))));
    }

    private static int help(FabricClientCommandSource s) {
        msg(s, "/cchb toggle | list | set <name> <value> | reload | debug | probe\n"
                + "probe = prints what this server sends under player names (read-only, no packets sent).");
        return 1;
    }

    private static void msg(FabricClientCommandSource s, String text) {
        s.sendFeedback(Component.literal(text));
    }

    /** Read-only diagnostics: tells us how CubeCraft's own indicator is delivered to the client. */
    private static int probe(FabricClientCommandSource s) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            msg(s, "Join a server first.");
            return 0;
        }
        try {
            StringBuilder sb = new StringBuilder("[cchb probe]\n");
            ServerData sd = mc.getCurrentServer();
            sb.append("server: ").append(sd == null ? "(none)" : sd.ip)
                    .append(" -> gate ").append(ServerGate.active() ? "ACTIVE" : "inactive").append('\n');

            Scoreboard board = level.getScoreboard();
            Objective below = board.getDisplayObjective(DisplaySlot.BELOW_NAME);
            for (DisplaySlot slot : DisplaySlot.values()) {
                Objective o = board.getDisplayObjective(slot);
                if (o != null) {
                    sb.append("slot ").append(slot.name()).append(": '").append(o.getName())
                            .append("' criteria=").append(o.getCriteria().getName())
                            .append(" title=").append(o.getDisplayName().getString()).append('\n');
                }
            }
            if (below == null) sb.append("BELOW_NAME: no objective (CubeCraft's bar is not a below-name row)\n");

            AbstractClientPlayer best = null;
            double bd = Double.MAX_VALUE;
            for (AbstractClientPlayer p : level.players()) {
                if (p == mc.player) continue;
                double dd = p.distanceToSqr(mc.player);
                if (dd < bd) {
                    bd = dd;
                    best = p;
                }
            }
            if (best == null) {
                sb.append("No other player loaded nearby. Stand near an opponent (ideally right after a hit) and run again.");
            } else {
                sb.append("nearest: ").append(best.getScoreboardName())
                        .append(String.format(" (%.1f blocks)\n", Math.sqrt(bd)));
                sb.append("entity health=").append(best.getHealth()).append(" max=").append(best.getMaxHealth())
                        .append(" absorption=").append(best.getAbsorptionAmount()).append('\n');
                if (below != null) {
                    ReadOnlyScoreInfo info = board.getPlayerScoreInfo(best, below);
                    sb.append("below-name score: ").append(info == null ? "(none for this player)" : info.value()).append('\n');
                }
                PlayerTeam team = board.getPlayersTeam(best.getScoreboardName());
                if (team != null) {
                    sb.append("team '").append(team.getName()).append("' prefix='")
                            .append(team.getPlayerPrefix().getString()).append("' suffix='")
                            .append(team.getPlayerSuffix().getString()).append("' nametag=")
                            .append(team.getNameTagVisibility()).append('\n');
                }
                sb.append("passengers: ");
                if (best.getPassengers().isEmpty()) sb.append("none");
                for (Entity e : best.getPassengers()) sb.append(e.getType().getDescriptionId()).append(' ');
                sb.append("\ntext displays near head: ")
                        .append(level.getEntitiesOfClass(Display.TextDisplay.class,
                                best.getBoundingBox().inflate(1.5, 3.0, 1.5)).size());
            }
            String out = sb.toString();
            CcHealthBarMod.LOGGER.info(out);
            msg(s, out);
            return 1;
        } catch (Throwable t) {
            CcHealthBarMod.LOGGER.warn("probe failed", t);
            msg(s, "probe failed: " + t);
            return 0;
        }
    }
}
