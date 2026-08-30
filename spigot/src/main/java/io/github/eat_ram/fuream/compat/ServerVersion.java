package io.github.eat_ram.fuream.compat;

import org.bukkit.Bukkit;

/** Runtime Minecraft/Bukkit version without linking version-specific API classes. */
public final class ServerVersion implements Comparable<ServerVersion> {
    public static final ServerVersion CURRENT = parse(currentVersionString());

    public final int major;
    public final int minor;
    public final int patch;

    public ServerVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static ServerVersion parse(String value) {
        int[] parts = new int[] {0, 0, 0};
        int part = 0;
        int current = -1;
        for (int i = 0; i < value.length() && part < parts.length; i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                current = Math.max(0, current) * 10 + (c - '0');
            } else if (current >= 0) {
                parts[part++] = current;
                current = -1;
                if (part > 0 && c != '.') break;
            }
        }
        if (current >= 0 && part < parts.length) parts[part] = current;
        return new ServerVersion(parts[0], parts[1], parts[2]);
    }

    private static String currentVersionString() {
        try {
            String value = Bukkit.getBukkitVersion();
            return value == null ? "0.0" : value;
        } catch (Throwable ignored) {
            return "0.0";
        }
    }

    public boolean atLeast(int major, int minor) {
        return compareTo(new ServerVersion(major, minor, 0)) >= 0;
    }

    public boolean atLeast(int major, int minor, int patch) {
        return compareTo(new ServerVersion(major, minor, patch)) >= 0;
    }

    public boolean atMost(int major, int minor) {
        return compareTo(new ServerVersion(major, minor, Integer.MAX_VALUE)) <= 0;
    }

    public String adapterFamily() {
        if (atMost(1, 12)) return "legacy-numeric";
        if (!atLeast(1, 17)) return "flattening-obfuscated";
        if (!atLeast(1, 20, 5)) return "mojang-named";
        return "components";
    }

    @Override
    public int compareTo(ServerVersion other) {
        if (major != other.major) return major < other.major ? -1 : 1;
        if (minor != other.minor) return minor < other.minor ? -1 : 1;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + (patch == 0 ? "" : "." + patch);
    }

}
