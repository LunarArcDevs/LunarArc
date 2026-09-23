package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.tag.PostFlattenTagRegistrar;
import io.papermc.paper.tag.PreFlattenTagRegistrar;

/** Service-loaded 1.21.1 lifecycle type provider using concrete handler lists. */
public final class LunarArcLifecycleEventTypeProvider implements LifecycleEventTypeProvider {
    private final TagEventTypeProvider tags = new TagProvider();

    /** See {@link io.lunararcdevs.lunararc.common.server.LunarArcPaperServiceBootstrap}. */
    public static void ensureInstalled() {
        io.lunararcdevs.lunararc.common.server.LunarArcPaperServiceBootstrap.seedIfBroken(
                LifecycleEventTypeProvider::provider,
                "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventTypeProvider", "INSTANCE",
                java.util.Optional.of(new LunarArcLifecycleEventTypeProvider()));
    }

    @Override
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Monitorable<O, E> monitor(
            String name, Class<? extends O> ownerClass) {
        return new MonitorableLifecycleEventType<>(name, ownerClass);
    }

    @Override
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Prioritizable<O, E> prioritized(
            String name, Class<? extends O> ownerClass) {
        return new PrioritizableLifecycleEventType.Simple<>(name, ownerClass);
    }

    @Override
    public TagEventTypeProvider tagProvider() {
        return tags;
    }

    private static final class TagProvider implements TagEventTypeProvider {
        @Override
        public <T> LifecycleEventType.Prioritizable<BootstrapContext, ReloadableRegistrarEvent<PreFlattenTagRegistrar<T>>> preFlatten(
                RegistryKey<T> key) {
            return new PrioritizableLifecycleEventType.Simple<>("pre_flatten:" + key, BootstrapContext.class);
        }

        @Override
        public <T> LifecycleEventType.Prioritizable<BootstrapContext, ReloadableRegistrarEvent<PostFlattenTagRegistrar<T>>> postFlatten(
                RegistryKey<T> key) {
            return new PrioritizableLifecycleEventType.Simple<>("post_flatten:" + key, BootstrapContext.class);
        }
    }
}
