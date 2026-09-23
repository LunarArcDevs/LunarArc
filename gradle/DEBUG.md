## Optional diagnostics

LunarArc tracing and startup/shutdown timing reports are disabled by default. Put the JVM option before `-jar` in your launch script:

```text
java -Dlunararc.debug=timing -jar lunararc-neoforge.jar nogui
```

Choose `timing`, `plugin`, `reflect`, `remap`, `classload`, `entity`, `fluid`, `command`, or `interact`. Combine channels with commas, such as `-Dlunararc.debug=timing,plugin`, or use `-Dlunararc.debug=all` for every channel. Restart to apply changes. The old `debugall` flags do not enable diagnostics.

Timing summaries appear in the console. Detailed traces go to `logs/lunararc-debug.log`; plugin diagnostics go to `logs/lunararc-plugin-debug.log`. Ordinary warnings and errors remain visible without debugging. Java-version rejection warnings are reported once per plugin JAR per server run.

Two more diagnostics run unconditionally, with no debug flag needed: a player command taking 250ms or longer to execute logs a warning naming the command and its elapsed time, and a plugin's `TabCompleter` throwing an exception logs a warning instead of silently dropping suggestions.