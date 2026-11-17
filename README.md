# Stockpile

A client-side **Minecraft Java 26.2 and 26.3 / Fabric** mod that remembers your opened chests and gives them a browser dashboard.

## Start here

1. Install **Fabric Loader 0.20.1 or newer** and **Fabric API matching your Minecraft version** (26.2 or 26.3).
2. Place the matching Stockpile JAR from the table below in your instance's `mods` folder. Replace any older Stockpile JAR; install only one. The launcher supplies Java 25; no development tools are required to use the mod.
3. Launch Minecraft, join a world, and open a few storage containers.
4. Run **`/stockpile open`** to open the dashboard now. You can also run **`/stockpile start`**, then click **Open dashboard** or **Copy link** in chat. The shortcut prints the same link; it defaults to B and can be rebound in Minecraft's controls. Automatic browser opening defaults to OFF.
5. Open **Build planner** and upload a schematic. The checklist compares its direct placement materials with your remembered storage.

| Minecraft | Stockpile JAR | Fabric API used for compilation | Optional Mod Menu |
| --- | --- | --- | --- |
| 26.2 | `stockpile-0.3.4+26.2.jar` | `0.161.0+26.2` | `20.0.3` |
| 26.3 | `stockpile-0.3.4+26.3.jar` | `0.166.2+26.3` | `21.0.1` |

Each JAR declares its own Minecraft target. Commands, settings, saved inventory and dashboard features work the same way in both builds. Existing `config/stockpile` data remains readable.

Stockpile is standalone. Chest Tracker Port and Litematica do not need to be installed. This project was inspired by Chest Tracker's workflow; it does not include its code. An optional S button in Chest Tracker Port's 26.2/26.3 main GUI opens Stockpile controls. Both mods can keep their own container memories.

**Build status:** the Java code compiles separately against the actual Minecraft 26.2 and 26.3 clients and their matching Fabric APIs. Inventory calculations, format parsing, persistence and HTTP access controls have automated checks, and the browser upload flow was tested. A Minecraft session playtest is still required before treating the mod as release-ready.

## Issues, support and suggestions

Open **Stockpile settings > Issues, support & suggestions** to visit the [GitHub issue chooser](https://github.com/danzaa/StockPile/issues/new/choose). Choose **Report a bug**, **Get support** or **Suggest a feature**. This button also works from Mod Menu before joining a world. The dashboard footer has a **GitHub support** link.

The mod metadata links to the [source repository](https://github.com/danzaa/StockPile) and [issue tracker](https://github.com/danzaa/StockPile/issues), so compatible mod menus and launchers can show those links. These links are included starting with Stockpile 0.3.4.

## Controls and commands

| Command | Result |
| --- | --- |
| `/stockpile` or `/stockpile settings` | Open the in-game settings screen. |
| `/stockpile open` | Open the browser now and print a link with a copy option, keeping tracking and auto-open preferences unchanged. |
| `/stockpile start` | Resume tracking, save that preference and print a clickable dashboard link. |
| `/stockpile stop` or `/stop` | Pause Stockpile locally; keep the last snapshot accessible. |
| `/stockpile link` | Print the dashboard link without changing tracking or opening a browser automatically. |

`/stop` is registered as a client-side Stockpile alias. These handlers do not send server shutdown requests. Tracking stays paused across restarts until it is enabled again.

In **Stockpile settings**, you can toggle tracking, the startup chat message, the shortcut, automatic browser opening and the Chest Tracker button. **Key bindings...** opens Minecraft's controls; **Unbind shortcut** removes its assigned key. A disabled or unbound shortcut does not run Stockpile actions. `/stockpile open` remains available for an explicit browser launch. Commands remain available. The startup hint reads the current key name and refreshes after rebinding; turn the startup message off to suppress those hints.

Automatic browser opening starts OFF. `/stockpile open` explicitly opens the browser regardless of that preference. Chat links also offer **Copy link** for pasting the complete address into a browser. With automatic opening disabled, `/stockpile start` and the shortcut only print a clickable link. **Open browser** in the settings is an explicit one-time action. **Show dashboard link** needs a joined world because it sends a chat message.

Settings are saved in `config/stockpile/settings.json`. If **Mod Menu** for your Minecraft version is installed, its Stockpile configuration button opens the same screen. Mod Menu is optional and is not bundled. The optional integration compiles against the version listed in the table above.

## Inventory sources and placed shulkers

The website has separate **Inventory** and **Ender Chest** views. The inventory view includes chests, barrels and placed shulkers by default. Ender Chest contents are excluded until you explicitly select them. The four **Container sources** checkboxes also apply to container cards, quantities, low-stock targets and schematic comparisons. Stock targets are stored separately for storage, Ender Chest and deliberately combined sources. Player-carried inventory remains outside this release's scope.

Ender Chest contents belong to the player/world and are counted once, regardless of which dimension their latest access point is in. Selecting Ender Chest alongside other sources explicitly combines them.

Item rows show quantities **in placed shulkers**, and the detail view and container cards have a **PLACED SHULKER** label with coordinates. Packed-box paths retain the parent chest and slot. These are different locations: placing a box is not the same as keeping its item in a chest.

Your own inventory moves refresh automatically while a tracked container is open and at close. The local website polls for changes every 2.5 seconds. A client cannot detect hopper activity or another player's edits to a closed, unobserved chest; reopen it to refresh. Hosted snapshot imports still need a new export/import.

## Chest Tracker GUI compatibility

With Chest Tracker Port for your Minecraft version installed, Stockpile adds a small **S** button to the main Chest Tracker screen when there is room beside its existing header controls. It opens Stockpile settings and dashboard actions. You can disable this button in Stockpile settings. Existing Chest Tracker key bindings and memory-bank controls are unchanged.

This is an optional GUI hook and coexistence integration. Existing Chest Tracker memory banks are not imported or modified. Stockpile still observes the ordinary server container updates and remembers its own snapshots. The hook uses Fabric screen events and does not make Chest Tracker a required dependency. The game GUI and this optional integration still need an in-game playtest.

## Updated shulker tracking

Replace the previous JAR with the 0.3.4 JAR matching your Minecraft version, then reopen the parent chests to rescan their shulker contents. Old inventory exports remain readable, but contain no packed-item data. Export a fresh snapshot after rescanning. Material totals include packed contents by default; the checkbox excludes them when you want loose items only.

If a previously opened placed shulker is packed into a chest, Stockpile removes the old placed record after observing the block removal in a loaded chunk. For changes made while you were away, revisit the old location or remove its saved record to avoid stale counts. Nested modded shulkers have depth and size limits; incomplete scans are flagged.

## What works

- Browser inventory search, quantity totals, container locations and dimension filters.
- Per-world low-stock targets. Below the target is low; at least twice the target is plenty.
- Last-observed timestamps, empty-container snapshots and removing old records.
- Items inside shulker boxes stored in chests, barrels and Ender Chests, with per-box paths.
- A switch to include or exclude shulker contents from counts and build checks.
- Removes a previously scanned placed-shulker record when its removal is observed in a loaded chunk.
- Double-chest deduplication and one Ender Chest record per player/world.
- Local updates while you use a tracked chest, plus persistence between game sessions.
- `.litematic` versions 4–7, Sponge `.schem` versions 2/3, vanilla single-palette `.nbt` structures and explicit JSON/CSV material lists.
- Multi-region files, packed palette indices across long boundaries, double slabs, lower door halves, bed feet, layered snow, candles and common wall-block item aliases.
- Missing-material CSV exports, build-copy multipliers, and multiple saved projects.

## Local dashboard and hosted preview

The **local dashboard** runs on the same computer as Minecraft. It uses a loopback address on the first available port from `19482` to `19492`, with a random per-launch access token. Run `/stockpile start` and click its link after restarting Minecraft so your browser receives the new token. Tokens are passed in the URL fragment and then retained only in that tab's session storage. The API rejects other hosts, foreign origins and requests without the token.

The **hosted dashboard** provides example data and lets you import an exported inventory JSON. Use **Export inventory** in the connected local dashboard, then **Import inventory** in the hosted page. Hosted imports are snapshots and do **not** sync automatically. This release has no cloud upload service, phone pairing, team sharing or background server scan.

Files are processed locally in the browser. Targets, imported snapshots and build lists are saved in that browser's local storage. Export important data before clearing browser storage. Browser-local preferences do not transfer between the hosted and local dashboard.

## How tracking works

Stockpile remembers supported storage blocks when you interact with them, waits for a complete server-supplied container inventory, and snapshots the container slots. Player inventory slots are excluded. Server updates, periodic client snapshots while the menu is open and the final close snapshot keep the local view current. World identifiers include a hash of the world/server identity and the player's UUID, so worlds and accounts do not share counts.

Supported blocks are vanilla chest variants (including double chests), barrels, shulker boxes and Ender Chests. Other containers, minecart inventories, server virtual inventories and unusual modded menus are outside the initial supported scope.

Inventory is saved to `config/stockpile/inventory.json` inside the selected Minecraft instance. Writes are serialized and atomic where the filesystem supports it. Unreadable existing data is preserved as a timestamped backup.

## Limits that affect material checks

- A client cannot see unopened containers or changes to closed chests. Reopen them to refresh. Changes made by friends, hoppers and automation can make saved counts stale.
- A moved or destroyed container can leave an old record. Remove it in **Containers**, then open the new location.
- Counts group stacks by item ID. Names, enchantments and other components do not define separate material types.
- Player inventory, bundles and schematic container contents are excluded. Shulker-box item contents are tracked and counted, with their parent chest and slot shown in item details.
- Schematic checks cover direct placement items. Recipe substitutions can also use vanilla crafting and stonecutting. They do not verify existing blocks at a placement, reserve materials between separate builds, count entities, calculate smelting fuel or apply custom server recipes.
- Waterlogged blocks, fluids, block-entity extras, overlapping regions and unsupported blocks produce review notes. The hosted preview uses vanilla block-to-item estimates; the connected dashboard also uses the game's block/item registry. Some complex modded blocks may still need manual mapping.
- Legacy numeric-ID `.schematic` files and randomized structure palettes are unsupported. Re-export in a supported format.
- Limits: 25 MB per schematic file, 64 MB expanded NBT and 8 million block positions. Large parsing jobs run in a worker with a time limit.

## Recipe substitutions

In **Build planner**, **Recipe substitutions: ON/OFF** switches between finished materials in storage and materials you can craft. It starts OFF and remembers your choice in this browser. **Have** remains the physical stock count; **Can craft** is additional coverage, and **Missing** is what the selected plan cannot supply. Crafting never runs in Minecraft automatically.

The plan uses vanilla crafting-table and stonecutting recipes extracted from the official 26.2 and 26.3 clients. New mod exports identify the running Minecraft version automatically. For older exports, select the matching **Minecraft recipes** version. `python3 scripts/make-recipes.py` reproduces the bundled recipe catalogs from the ignored official client JARs.

All directly required materials are reserved first. Other ingredients share a single stock budget across this build. Chained recipes, tags such as mixed planks, output batch sizes and leftovers are accounted for. The ordered **Crafting plan** lists actual quantities and workstations and can be exported as CSV. Material CSV exports keep stored and craftable amounts in separate columns.

Access to the listed workstations is assumed. Smelting, blasting, fuel, smithing, component-dependent/special recipes, recipe remainders and custom server or modpack recipes are not estimated. The bounded search returns one feasible plan, which may be conservative when several recipe choices compete for supplies. A notice appears when search limits are reached. Separate builds are checked independently.

## Material-list formats

JSON:

```json
{
  "name": "Stone path",
  "materials": [
    {"id": "minecraft:stone_bricks", "count": 192},
    {"id": "minecraft:lantern", "count": 12}
  ]
}
```

CSV requires namespaced item IDs and a `Total`, `count` or `Required` column:

```csv
Item ID,Total
minecraft:stone_bricks,192
minecraft:lantern,12
```

Display-name-only Litematica CSV exports need conversion to item IDs; raw `.litematic` upload is preferable. Use `examples/tiny-workshop.litematic` to try the reader and `examples/example-inventory.json` to test inventory import.

## Build and check

Requires a JDK 25 and an internet connection for dependency retrieval.

```sh
./gradlew build -Pminecraft_version=26.2
./gradlew build -Pminecraft_version=26.3
```

Outputs are `build/libs/stockpile-0.3.4+26.2.jar` and `build/libs/stockpile-0.3.4+26.3.jar`. The default target remains 26.3. Per-version build directories isolate compiled classes and Fabric modules. `minecraft-targets.json` pins each target's dependencies, and `src/versions/<version>/java` contains the small shulker-content API adapter. Both supported Minecraft versions use unobfuscated names, so no remapping step is needed. The dashboard is packaged from `dist/` into the mod.

In restricted build containers where Loom cannot probe Unix-domain sockets, `python3 scripts/build-standalone.py --minecraft-version 26.2` (or `26.3`) compiles the same sources against checksum-verified official game dependencies with JDK 25 and packages the same resources. It does not launch Minecraft. Set `JAVA_HOME` to the JDK if needed. Downloaded game binaries stay in the ignored build cache and are not distributed.

Browser logic and format checks (Node.js 22+):

```sh
npm test
```

After each standalone build, run `python3 scripts/test-backend.py --minecraft-version 26.2` (or `26.3` to match the build). It checks saving/reloading, world isolation, double-chest replacement, local HTTP assets, the complete browser module dependency graph, authentication, origin checks, shulker data components, settings persistence, local command routing and dynamic clickable chat messages and the actual settings-screen render lifecycle without OpenGL. The screen regression reproduces the old double-blur exception against the released build and passes the fixed implementation.

## First in-game playtest

1. Open a single chest containing 64 stone. Check that the dashboard shows 64, with the correct coordinates.
2. Move 16 stone to your inventory. Close the chest. The total should become 48.
3. Empty the chest. It should remain listed as an empty container and contribute zero items.
4. Open either side of a double chest. Open the other side. The total should remain unchanged.
5. Open two Ender Chests. Your personal contents should be counted once.
6. Quit and rejoin. Run `/stockpile start`, click its link and verify that the inventory survived.
7. Join another world or server and confirm its inventory stays separate.
8. Upload `tiny-workshop.litematic`; verify its six item types and compare the checklist with your storage.

## Sources

- [Fabric 26.3 development changes](https://fabricmc.net/2026/09/15/263.html)
- [Fabric's 26.3 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [Chest Tracker Port](https://modrinth.com/mod/chest-tracker-port)
- [Litematica upstream](https://github.com/maruohon/litematica)
- [Sponge schematic format specification](https://github.com/SpongePowered/Schematic-Specification)

MIT licensed. The mod logo is original pixel artwork. Inventory icons use Minecraft 26.3 item textures and block models. Minecraft and Fabric dependencies are not bundled in the distributed mod.

## Browser loading fix in 0.3.3

The 0.3.2 local server returned 404 for the new `recipes.js` and `recipes-data.js` imports. That prevented the browser application from starting. The server now serves both files, and the HTTP regression follows the actual application and worker import graph to catch missing modules in future builds.

`/stockpile open` provides a direct browser-opening command. Every dashboard chat link also has a **Copy link** action. The browser-opening preference remains OFF by default.

## Settings render fix in 0.3.2

The vanilla screen wrapper already draws the background before calling a screen's render method. Stockpile no longer draws that blurred background a second time. Chat-triggered settings requests now open at the end of the client tick instead of changing the screen inside the chat click handler.

## Additional 0.3 playtest on each supported Minecraft version

1. Run `/stockpile start`. Confirm that a clickable link appears and that the browser stays closed with Auto-open browser OFF.
2. Rebind the shortcut to another key. Confirm the next hint uses the new key. Disable the shortcut and confirm it takes no action.
3. Turn Startup message OFF, rejoin and confirm no Stockpile join hint appears.
4. Run both `/stop` and `/stockpile stop`. Confirm tracking pauses, the website says paused and counts remain unchanged until `/stockpile start`.
5. Open a chest, move items to your inventory and close it. Confirm the web quantity updates within a few seconds.
6. Open a placed shulker, check its PLACED label, then pick it up and put it in a chest. Verify there is one current source for its contents after rescanning the parent chest.
7. Open an Ender Chest. Check its dedicated view and confirm its items are excluded from storage until the Ender Chest checkbox is selected.
8. Open Chest Tracker's main GUI and use its S button, then verify its original controls still work. Test Mod Menu's Stockpile settings button if installed.
