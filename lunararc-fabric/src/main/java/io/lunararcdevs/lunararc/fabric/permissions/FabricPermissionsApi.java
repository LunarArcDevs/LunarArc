package io.lunararcdevs.lunararc.fabric.permissions;

import io.lunararcdevs.lunararc.common.permission.LunarArcBukkitPermissions;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FabricPermissionsApi {
    private FabricPermissionsApi() {}

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
