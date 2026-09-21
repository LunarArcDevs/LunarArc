package io.lunararcdevs.lunararc.quilt.permissions;

import io.lunararcdevs.lunararc.common.permission.LunarArcBukkitPermissions;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Registers LunarArc as the me.lucko:fabric-permissions-api backend on Quilt (run through its
 * Fabric compatibility layer, same as everywhere else in this module), mirroring
 * io.lunararcdevs.lunararc.fabric.permissions.FabricPermissionsApi.
 */
public final class QuiltPermissionsApi {
    private QuiltPermissionsApi() {}

    public static void register() {
        PermissionCheckEvent.EVENT.register((source, permission) -> {
            if (!(source instanceof CommandSourceStack stack)) return TriState.DEFAULT;
            Entity entity = stack.getEntity();
            if (!(entity instanceof ServerPlayer player)) return TriState.DEFAULT;
            return LunarArcBukkitPermissions.explicitOnlinePermission(player.getUUID(), permission)
                    .map(value -> value ? TriState.TRUE : TriState.FALSE)
                    .orElse(TriState.DEFAULT);
        });
    }
}
