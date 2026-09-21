package io.lunararcdevs.lunararc.common.permission;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Shared Bukkit permission semantics used by every loader integration. */
public final class LunarArcBukkitPermissions {
    private LunarArcBukkitPermissions() {}

    /** Returns an explicitly configured Bukkit permission for an online player, if one exists. */
    public static Optional<Boolean> explicitOnlinePermission(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isBlank()) return Optional.empty();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isPermissionSet(permission)) return Optional.empty();
        return Optional.of(player.hasPermission(permission));
    }

    public static boolean hasPermission(UUID playerId, String permission, boolean defaultValue) {
        Optional<Boolean> explicit = explicitOnlinePermission(playerId, permission);
        if (explicit.isPresent()) return explicit.get();

        Player online = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (online != null) return defaultValue;

        if (playerId == null) return defaultValue;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.isOp();
    }

    public static boolean hasPermission(UUID playerId, String permission) {
        return hasPermission(playerId, permission, false);
    }
}
