package io.papermc.paper.plugin.provider.configuration;

import io.papermc.paper.plugin.provider.configuration.type.PluginDependencyLifeCycle;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

public final class LegacyPaperMetaLoadOrderRegressionTest {
    private LegacyPaperMetaLoadOrderRegressionTest() {}

    public static void run() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root();
        node.node("load-before").appendListNode().node("name").set("BeforeMe");
        node.node("load-after").appendListNode().node("name").set("AfterMe");
        node.node("dependencies").appendListNode().act(n -> {
            n.node("name").set("RequiredDep");
            n.node("required").set(true);
        });

        LegacyPaperMeta.migrate(node);

        ConfigurationNode server = node.node("dependencies", PluginDependencyLifeCycle.SERVER);
        String before = server.node("BeforeMe", "load", "name").getString();
        if (!"BEFORE".equals(before)) {
            throw new AssertionError("load-before entry did not migrate to LoadOrder.BEFORE: " + before);
        }
        String after = server.node("AfterMe", "load", "name").getString();
        if (!"AFTER".equals(after)) {
            throw new AssertionError("load-after entry did not migrate to LoadOrder.AFTER: " + after);
        }
        boolean required = server.node("RequiredDep", "required").getBoolean();
        if (!required) {
            throw new AssertionError("required dependency entry did not migrate with required=true");
        }
    }
}
