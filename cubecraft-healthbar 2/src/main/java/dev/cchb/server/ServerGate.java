package dev.cchb.server;

import dev.cchb.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/**
 * Decides whether the mod may render at all. Uses only the address the player connected to
 * (no packets, no display-name checks). Anything uncertain => inactive.
 */
public final class ServerGate {
    private static String lastIp;
    private static boolean lastResult;

    private ServerGate() { }

    public static void reset() {
        lastIp = null;
        lastResult = false;
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ServerData sd = mc.getCurrentServer();
        if (sd == null) return false;               // singleplayer / no server
        ModConfig c = ModConfig.get();
        if (c.devAllowAnyServer) return true;

        String ip = sd.ip;
        if (ip == null) return false;
        if (ip.equals(lastIp)) return lastResult;   // cheap per-frame path

        lastIp = ip;
        lastResult = matches(normalize(ip), c);
        return lastResult;
    }

    static String normalize(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("[")) return s;            // IPv6 literal: never matches
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && s.indexOf(':') == colon) s = s.substring(0, colon);   // strip :port
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static boolean matches(String host, ModConfig c) {
        if (host.isEmpty()) return false;
        if (host.equals("cubecraft.net") || host.endsWith(".cubecraft.net")) return true;
        for (String extra : c.extraServerHosts) {
            if (extra != null && normalize(extra).equals(host)) return true;
        }
        return false;
    }
}
