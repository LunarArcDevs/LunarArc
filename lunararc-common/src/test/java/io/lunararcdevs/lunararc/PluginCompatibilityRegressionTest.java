package io.lunararcdevs.lunararc;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.scheduler.CraftPaperSchedulers;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PluginCompatibilityRegressionTest {
    public static void main(String[] args) throws Exception {
        SableCompatibilityRegressionTest.run();
        PaperHelperRegressionTest.run();
        PaperConfigurationRegressionTest.run();
        PluginJarLifecycleRegressionTest.run();
        PluginTransformRegressionTest.run();
        PaperPerformanceRegressionTest.run();
        MappingEnvironmentRegressionTest.run();
        io.lunararcdevs.lunararc.common.server.EssentialsAliasRegressionTest.run();
        io.lunararcdevs.lunararc.common.server.RegistryAccessRegressionTest.run();
        io.lunararcdevs.lunararc.common.server.MavenLibraryResolverRegressionTest.run();
        io.papermc.paper.plugin.provider.configuration.LegacyPaperMetaLoadOrderRegressionTest.run();
        AtomicBoolean enabled = new AtomicBoolean(true);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, values) -> switch (method.getName()) {
                    case "isEnabled" -> enabled.get();
                    case "getName", "toString" -> "RegressionPlugin";
                    case "getLogger" -> Logger.getLogger("RegressionPlugin");
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == values[0];
                    default -> null;
                });
        CraftScheduler bukkit = new CraftScheduler();
        verifySchedulerBatching(plugin);
        CraftPaperSchedulers paper = new CraftPaperSchedulers(bukkit);
        try {
            AtomicInteger calls = new AtomicInteger();
            Future<Integer> cancelled = bukkit.callSyncMethod(plugin, calls::incrementAndGet);
            check(cancelled.cancel(false), "future cancellation must succeed before execution");
            bukkit.mainThreadHeartbeat(1);
            check(calls.get() == 0, "cancelled callable executed");
            Future<Integer> removed = bukkit.callSyncMethod(plugin, calls::incrementAndGet);
            bukkit.cancelTasks(plugin);
            check(removed.isCancelled(), "task cancellation did not cancel its future");
            Future<Integer> completed = bukkit.callSyncMethod(plugin, () -> 42);
            bukkit.mainThreadHeartbeat(2);
            check(completed.get(1, TimeUnit.SECONDS) == 42, "callable result was lost");

            CountDownLatch delayed = new CountDownLatch(1);
            bukkit.runTaskLaterAsynchronously(plugin, delayed::countDown, 2);
            check(!delayed.await(200, TimeUnit.MILLISECONDS), "async delay advanced without server ticks");
            bukkit.mainThreadHeartbeat(3);
            check(delayed.getCount() == 1, "async task dispatched before its tick deadline");
            bukkit.mainThreadHeartbeat(4);
            check(delayed.await(2, TimeUnit.SECONDS), "async task missed its tick deadline");

            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            var repeating = bukkit.runTaskTimerAsynchronously(plugin, () -> {
                started.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, 1, 1);
            try {
                bukkit.mainThreadHeartbeat(5);
                bukkit.mainThreadHeartbeat(6);
                check(started.await(2, TimeUnit.SECONDS), "repeat waited for the preceding callback to finish");
                check(bukkit.getActiveWorkers().stream().filter(worker -> worker.getTaskId() == repeating.getTaskId()).count() == 2,
                        "overlapping workers were lost");
            } finally {
                repeating.cancel();
                release.countDown();
            }

            var task = paper.async().runDelayed(plugin, ignored -> calls.incrementAndGet(), 1, TimeUnit.DAYS);
            task.cancel();
            var field = paper.async().getClass().getDeclaredField("tasks");
            field.setAccessible(true);
            check(((Set<?>) field.get(paper.async())).isEmpty(), "cancelled Paper task retained by scheduler");
            paper.async().runDelayed(plugin, ignored -> calls.incrementAndGet(), 1, TimeUnit.DAYS);
            paper.async().cancelTasks(plugin);
            check(((Set<?>) field.get(paper.async())).isEmpty(), "bulk cancellation retained Paper tasks");
            CountDownLatch forbidden = new CountDownLatch(1);
            paper.async().runDelayed(plugin, ignored -> forbidden.countDown(), 50, TimeUnit.MILLISECONDS);
            enabled.set(false);
            check(!forbidden.await(150, TimeUnit.MILLISECONDS), "disabled plugin callback executed");

            CraftInventory inventory = new CraftInventory(null, 3, null, net.kyori.adventure.text.Component.text("Test"));
            inventory.setItem(0, new Stack(64));
            check(!inventory.contains(new Stack(64), 2), "one stack counted as two stacks");
            check(!inventory.contains(new Stack(32), 1), "stack amount ignored by contains");
            check(inventory.containsAtLeast(new Stack(32), 64), "containsAtLeast stopped counting individual items");
            inventory.setItem(1, new Stack(64));
            check(inventory.contains(new Stack(64), 2), "two exact stacks not found");
            check(inventory.containsAtLeast(new Stack(1), 128), "quantities not summed across stacks");
            check(io.lunararcdevs.lunararc.common.server.LunarArcPluginFixManager.translationEntries(null).isEmpty(),
                    "empty translations did not fall back");
            check(io.lunararcdevs.lunararc.common.server.LunarArcPluginFixManager.translationEntries(
                    java.util.Map.of("key", "value")).size() == 1, "valid translations lost");
            var missingRecipe = net.minecraft.resources.ResourceLocation.parse("farmingforblockheads:market/missing");
            var validRecipe = net.minecraft.resources.ResourceLocation.parse("farmingforblockheads:market/valid");
            var unrelated = net.minecraft.resources.ResourceLocation.parse("example:market/missing");
            var missing = com.google.gson.JsonParser.parseString(
                    "{\"type\":\"farmingforblockheads:market\",\"result\":{\"item\":\"absent:sapling\"}}");
            var valid = com.google.gson.JsonParser.parseString(
                    "{\"type\":\"farmingforblockheads:market\",\"result\":{\"item\":\"minecraft:oak_sapling\"}}");
            var recipes = java.util.Map.of(missingRecipe, missing, validRecipe, valid, unrelated, missing);
            var filtered = io.lunararcdevs.lunararc.common.compat.MarketRecipeFilter.filter(recipes,
                    id -> id.getNamespace().equals("minecraft"));
            check(!filtered.containsKey(missingRecipe), "unavailable optional market recipe retained");
            check(filtered.containsKey(validRecipe) && filtered.containsKey(unrelated), "other recipes removed");
            check(recipes.size() == 3, "recipe input mutated");
            enabled.set(true);
            CountDownLatch shutdownStarted = new CountDownLatch(2);
            CountDownLatch interrupted = new CountDownLatch(2);
            Runnable waitForShutdown = () -> {
                shutdownStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            };
            bukkit.runTaskAsynchronously(plugin, waitForShutdown);
            bukkit.mainThreadHeartbeat(7);
            paper.async().runNow(plugin, ignored -> waitForShutdown.run());
            check(shutdownStarted.await(2, TimeUnit.SECONDS), "shutdown test workers did not start");
            paper.beginShutdown();
            bukkit.beginShutdown();
            check(interrupted.await(2, TimeUnit.SECONDS), "both schedulers must interrupt running work");
            paper.awaitShutdown();
            bukkit.awaitShutdown();
            System.out.println("Plugin compatibility regressions passed");
        } finally {
            paper.shutdown();
            bukkit.shutdown();
        }
    }

    private static void verifySchedulerBatching(Plugin plugin) throws Exception {
        CraftScheduler scheduler = new CraftScheduler();
        try {
            AtomicInteger order = new AtomicInteger();
            AtomicBoolean ordered = new AtomicBoolean(true);
            for (int i = 0; i < 10_000; i++) {
                int expected = i;
                scheduler.runTask(plugin, () -> {
                    if (order.getAndIncrement() != expected) ordered.set(false);
                });
            }
            scheduler.mainThreadHeartbeat(1);
            check(order.get() == 10_000 && scheduler.getPendingTasks().isEmpty(), "batch tasks were lost or retained");
            check(ordered.get(), "batch task order changed");
            AtomicBoolean nested = new AtomicBoolean();
            AtomicBoolean submitted = new AtomicBoolean();
            scheduler.runTask(plugin, () -> {
                Thread submitter = new Thread(() -> scheduler.runTask(plugin, () -> nested.set(true)));
                submitter.start();
                try {
                    submitter.join(2000);
                    submitted.set(!submitter.isAlive());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            });
            scheduler.mainThreadHeartbeat(2);
            check(submitted.get(), "scheduler held its collection lock while executing plugin code");
            check(!nested.get(), "new task entered the current heartbeat snapshot");
            scheduler.mainThreadHeartbeat(3);
            check(nested.get(), "new task missed the next heartbeat");
        } finally {
            scheduler.shutdown();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Stack extends ItemStack {
        private final int amount;

        Stack(int amount) {
            super();
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof Stack;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stack stack && getAmount() == stack.getAmount();
        }

        @Override
        public int hashCode() {
            return getAmount();
        }
    }
}
