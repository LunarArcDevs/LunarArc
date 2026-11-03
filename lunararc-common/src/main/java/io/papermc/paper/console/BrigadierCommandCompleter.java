package io.papermc.paper.console;

import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import net.minecraft.server.dedicated.DedicatedServer;

public class BrigadierCommandCompleter {
    private final DedicatedServer server;

    public BrigadierCommandCompleter(DedicatedServer server) { this.server = server; }

    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates, List<Completion> completions) {
        java.util.Set<String> values = new java.util.HashSet<>();
        if (server.overworld() != null && io.papermc.paper.configuration.GlobalConfiguration.get().console.enableBrigadierCompletions) {
            var dispatcher = server.getCommands().getDispatcher();
            var parsed = dispatcher.parse(line.line(), server.createCommandSourceStack());
            var suggestions = dispatcher.getCompletionSuggestions(parsed, line.cursor()).join();
            int wordStart = line.cursor() - line.wordCursor();
            for (var suggestion : suggestions.getList()) {
                String applied = suggestion.apply(line.line());
                int end = suggestion.getRange().getStart() + suggestion.getText().length();
                String value = applied.substring(Math.min(wordStart, suggestion.getRange().getStart()), end);
                String tooltip = suggestion.getTooltip() == null ? null : suggestion.getTooltip().getString();
                candidates.add(new Candidate(value, value, null, tooltip, null, null, false));
                values.add(suggestion.getText());
            }
        }
        for (Completion completion : completions) {
            String value = completion.suggestion();
            if (value.isEmpty() || !values.add(value)) continue;
            String tooltip = completion.tooltip() == null ? null
                    : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(completion.tooltip());
            candidates.add(new Candidate(value, value, null, tooltip, null, null, false));
        }
        this.fireTabCompleteEvent(line, candidates);
    }

    /** Bukkit's TabCompleteEvent for the console: plugins may remove or add completions, or cancel them. */
    private void fireTabCompleteEvent(ParsedLine line, List<Candidate> candidates) {
        if (org.bukkit.event.server.TabCompleteEvent.getHandlerList().getRegisteredListeners().length == 0 || org.bukkit.Bukkit.getServer() == null) return;
        List<String> values = new java.util.ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) values.add(candidate.value());
        org.bukkit.event.server.TabCompleteEvent event;
        try {
            // plugins expect the event on the server thread, not on the console reader thread
            event = server.submit(() -> {
                org.bukkit.event.server.TabCompleteEvent fired = new org.bukkit.event.server.TabCompleteEvent(
                        org.bukkit.Bukkit.getConsoleSender(), line.line(), values);
                org.bukkit.Bukkit.getPluginManager().callEvent(fired);
                return fired;
            }).join();
        } catch (RuntimeException failure) {
            return;
        }
        if (event.isCancelled()) {
            candidates.clear();
            return;
        }
        java.util.Set<String> kept = new java.util.HashSet<>(event.getCompletions());
        candidates.removeIf(candidate -> !kept.contains(candidate.value()));
        java.util.Set<String> present = new java.util.HashSet<>();
        for (Candidate candidate : candidates) present.add(candidate.value());
        for (String added : event.getCompletions()) {
            if (!added.isEmpty() && present.add(added)) candidates.add(new Candidate(added, added, null, null, null, null, false));
        }
    }
}
