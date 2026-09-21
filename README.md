![Logo](.github/lunararclogo.jpg)

[![Downloads count](https://img.shields.io/github/downloads/LunarArcDevs/LunarArc/total?style=for-the-badge)](https://lunararc.lunararcdevs.com)  ![License](https://img.shields.io/github/license/LunarArcDevs/LunarArc?style=for-the-badge) ![GitHub forks](https://img.shields.io/github/forks/LunarArcDevs/LunarArc?style=for-the-badge&logo=github)


An experimental hybrid Minecraft server compatibility layer. **Forge/NeoForge/Fabric/Quilt**, while LunarArc layers Bukkit/Spigot/Paper plugin compatibility on top with concrete Craft-style adapters and targeted mixins. Paper is an API/behaviour contract and merge reference, not a second shaded server runtime.

LunarArc also provides native [EssentialsX](https://essentialsx.net) integration for modded blocks and items. Modded registry entries are automatically exposed to EssentialsX, allowing commands such as `/give`, `/item`, and `/i` to work with modded content **out of the box, with no additional integration plugin required**.


|        Release        |  Forge  | NeoForge |  Fabric  |  QuiltMC  | Status |                                                                                                                                              Build                                                           
|:--------------------:|:-------:|:--------:|:--------:|:--------:|:------:|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| Trial Zenith (1.21.1) | 52.1.16 |21.1.250 |  0.19.5  |  0.30.1  | ACTIVE | [![1.21.1 Status](https://img.shields.io/github/actions/workflow/status/LunarArcDevs/LunarArc/gradle.yml?branch=Trial-Zenith&style=for-the-badge)](https://github.com/LunarArcDevs/LunarArc/actions?query=branch%3ATrial-Zenith) |

## Installing

 Download the jar.  
 Launch with command `java -jar lunararc.jar nogui`. 
   The `nogui` argument will disable the server control panel.

Read our document for more information.

## Support

Discord Server [Inivte Link](https://discord.gg/JDP3CMfBef)

## License

This project is licensed under [GPL v3](LICENSE).

## Sponsor

[![](.github/bisecthosting.webp)](https://bisecthosting.com/AMPZ)

Get 25% off hosting server with promocode AMPZ at [BisectHosting](https://bisecthosting.com/AMPZ).

[![](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com)

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET
applications. YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.


## Credits & Upstream Projects

LunarArc uses the following upstream projects as runtime platforms, API contracts, or implementation references:

- **[Arclight](https://github.com/IzzelAliz/Arclight)** - Primary architecture reference for the hybrid server structure, loader-specific mixins, lifecycle hooks, event coexistence, and concrete Bukkit/Craft integration.
- **[Paper](https://github.com/PaperMC/Paper)** - Bukkit/Paper API and server-behavior contract used to match Paper 1.21.1 plugin compatibility.
- **[Minecraft Forge](https://github.com/MinecraftForge/MinecraftForge)** — Forge runtime and loader APIs used by LunarArc's Forge module.
- **[NeoForge](https://github.com/neoforged/NeoForge)** - NeoForge runtime and loader APIs used by LunarArc's NeoForge module.
- **[Fabric](https://github.com/FabricMC/fabric)**  - Fabric loader/API lifecycle and integration hooks used by LunarArc's Fabric module.
- **[Quilt](https://github.com/QuiltMC/quilt-loader)** - Quilt runtime/loader support used by LunarArc's Quilt module.
- **[SpongePowered Mixin](https://github.com/SpongePowered/Mixin)** - Bytecode mixin framework used for targeted hooks and bridge state on the real loader-owned Minecraft classes.
