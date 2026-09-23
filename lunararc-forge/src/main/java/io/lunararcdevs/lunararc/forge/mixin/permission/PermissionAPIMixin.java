package io.lunararcdevs.lunararc.forge.mixin.permission;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.handler.DefaultPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import io.lunararcdevs.lunararc.common.permission.LunarArcBukkitPermissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DefaultPermissionHandler.class, remap = false)
public abstract class PermissionAPIMixin {
    @Inject(method = "getPermission", at = @At("HEAD"), cancellable = true)
    private <T> void lunararc$useBukkitPermission(
            ServerPlayer player,
            PermissionNode<T> node,
            PermissionDynamicContext<?>[] context,
            CallbackInfoReturnable<T> cir) {
        if (node.getType() != PermissionTypes.BOOLEAN) {
            return;
        }
        LunarArcBukkitPermissions.explicitOnlinePermission(playerUuid(player), node.getNodeName()).ifPresent(value -> {
            @SuppressWarnings("unchecked")
            T resolved = (T) value;
            cir.setReturnValue(resolved);
        });
    }

    // Loom compiles Forge against SRG names; Entity#getUUID is a real Mojang name that never
    // resolves directly here, so go through the reflection bridge instead.
    private static java.util.UUID playerUuid(ServerPlayer player) {
        try {
            return (java.util.UUID) io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge
                    .getMethod(player.getClass(), "getUUID", new Class<?>[0]).invoke(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve the player's UUID", e);
        }
    }
}
