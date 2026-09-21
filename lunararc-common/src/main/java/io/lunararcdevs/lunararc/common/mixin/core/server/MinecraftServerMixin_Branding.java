package io.lunararcdevs.lunararc.common.mixin.core.server;

import io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo;
import net.minecraft.SystemReport;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin_Branding {

    private static final Logger LUNARARC_BRANDING_LOGGER = LoggerFactory.getLogger("LunarArc");

    @Inject(method = "fillSystemReport", at = @At("RETURN"), require = 0)
    private void lunararc$brandSystemReport(SystemReport report, CallbackInfoReturnable<SystemReport> cir) {
        // Supplier form, as vanilla uses for its own entries: a detail that throws while a crash
        // report is being assembled is recorded as the failure instead of replacing the crash.
        report.setDetail("LunarArc", LunarArcVersionInfo::brandingLine);
        report.setDetail("LunarArc Paper API", LunarArcVersionInfo::paperApiVersion);
    }

    @Inject(method = "runServer", at = @At("HEAD"), require = 0)
    private void lunararc$announceBuild(CallbackInfo ci) {
        if (!io.lunararcdevs.lunararc.common.LunarArcDebug.enabledChannels().equals("none")) {
            LUNARARC_BRANDING_LOGGER.info("{} | debug channels: {}", LunarArcVersionInfo.brandingLine(),
                    io.lunararcdevs.lunararc.common.LunarArcDebug.enabledChannels());
        } else {
            LUNARARC_BRANDING_LOGGER.info("{}", LunarArcVersionInfo.brandingLine());
        }
        LUNARARC_BRANDING_LOGGER.info("This build is named \"{}\"", LunarArcVersionInfo.buildName());
    }
}
