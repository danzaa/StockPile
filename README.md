# Stockpile

A client-side **Minecraft Java 26.2 and 26.3 / Fabric** mod that remembers your opened chests and gives them a browser dashboard.

This checkout is **0.4.0-dev.9**, based on the last published 0.3.4. Development JARs are built locally; the stable download links below remain on 0.3.4. The same development JAR can be installed on a client or, optionally, a Fabric server. Installing it on a server does not share anything until an administrator creates explicit grants.

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
| `/stockpile find <item-id>` | Find remembered supplies in the joined world, print coordinates and highlight up to 32 nearby matching storage containers in the current dimension for 30 seconds. Example: `/stockpile find minecraft:stone`. |
| `/stockpile clearfind` | Clear Stockpile's current find highlight without changing tracking. |

`/stop` is registered as a client-side Stockpile alias. These handlers do not send server shutdown requests. Tracking stays paused across restarts until it is enabled again.

In **Stockpile settings**, you can toggle tracking, the startup chat message, the shortcut, automatic browser opening and the Chest Tracker button. **Key bindings...** opens Minecraft's controls; **Unbind shortcut** removes its assigned key. A disabled or unbound shortcut does not run Stockpile actions. `/stockpile open` remains available for an explicit browser launch. Commands remain available. The startup hint reads the current key name and refreshes after rebinding; turn the startup message off to suppress those hints.

Automatic browser opening starts OFF. `/stockpile open` explicitly opens the browser regardless of that preference. Chat links also offer **Copy link** for pasting the complete address into a browser. With automatic opening disabled, `/stockpile start` and the shortcut only print a clickable link. **Open browser** in the settings is an explicit one-time action. **Show dashboard link** needs a joined world because it sends a chat message.

Settings are saved in `config/stockpile/settings.json`. If **Mod Menu** for your Minecraft version is installed, its Stockpile configuration button opens the same screen. Mod Menu is optional and is not bundled. The optional integration compiles against the version listed in the table above.

## Switching worlds and servers

The local dashboard follows the world you join, including a new world with no remembered containers and sessions where tracking is paused. Saved worlds remain separate in the world selector. Choosing a saved world deliberately keeps that view during ordinary refreshes and displays a notice with **Show joined world**. Joining or reconnecting starts a fresh selection of the joined world and clears old world/dimension filters and item dialogs. Restarting Minecraft does not restore a previous connection as active.

## Inventory sources and placed shulkers

The website has separate **Inventory** and **Ender Chest** views. The inventory view includes chests, barrels and placed shulkers by default. Ender Chest contents are excluded until you explicitly select them. The four **Container sources** checkboxes also apply to container cards, quantities, low-stock targets and schematic comparisons. Stock targets are stored separately for storage, Ender Chest and deliberately combined sources. Player-carried inventory remains outside this release's scope.

Ender Chest contents belong to the player/world and are counted once, regardless of which dimension their latest access point is in. Selecting Ender Chest alongside other sources explicitly combines them.

Item rows show quantities **in placed shulkers**, and the detail view and container cards have a **PLACED SHULKER** label with coordinates. Packed-box paths retain the parent chest and slot. These are different locations: placing a box is not the same as keeping its item in a chest.

Your own inventory moves refresh automatically while a tracked container is open and at close. The local website polls for changes every 2.5 seconds. A client cannot detect hopper activity or another player's edits to a closed, unobserved chest; reopen it to refresh. Hosted snapshot imports still need a new export/import.

## Languages

Choose a language from the dashboard header. English, Hindi, Spanish, Arabic, French, Filipino (Tagalog), and Mongolian cover navigation and the main storage, planning, history and settings controls. Arabic uses a right-to-left layout. The dashboard remembers your choice in the browser. Minecraft uses its own language setting for supported Stockpile labels. Item names and some older details and diagnostic messages may still appear in English.

## Find item in-game (development)

In the connected local dashboard, open an item's details and choose **Highlight chest** beside a saved container. Return to Minecraft: a gold outline marks its storage block through terrain for 30 seconds by default. The dashboard shows coordinates and packed-shulker paths. Dashboard highlights report status in the browser. The outline fades in and out. A connected double chest uses one outline around both halves. The dashboard action respects **Include shulker contents**. The command `/stockpile find minecraft:diamond` searches all remembered container types in the joined world, including packed items and Ender Chest contents; it lists up to ten locations in chat, with all locations available in the dashboard.

Finding works while tracking is paused. It requires the matching world and player to be joined. The dashboard reports other-dimension locations in the browser, and the find command reports them in chat. An unloaded chest can highlight if you approach it before the outline expires. A saved location is marked as possibly stale when its expected storage block is missing. Reopen a replacement chest to refresh its contents. A saved Ender Chest location marks your last access point, and any Ender Chest provides the same personal contents.

Use `/stockpile clearfind` to clear the outline, or run another find. A level change or disconnect also clears it. Imported snapshots and the hosted preview retain coordinates, but cannot trigger in-game actions.

With **Chest Tracker Port** installed, Stockpile also contributes loaded, current-dimension remembered locations to its embedded **Where Is It** item-ID searches (normally Y over an item). Chest Tracker's own search handlers still run, and its richer results take precedence at overlapping locations. Component-sensitive searches remain handled by Chest Tracker because Stockpile stores item IDs rather than full stacks. This bridge uses the optional search event and does not import or change Chest Tracker's memory banks. If its API is incompatible, Stockpile logs a warning and keeps its standalone finder available.

Automated checks cover matching, routing, HTTP validation and outline geometry. Disposable single-player tests on 26.2 and 26.3 verified the rendered outline, quiet chat and the Chest Tracker Port search bridge.

## Storage map, chest selection and exclusions (development)

Open **Storage map** to inspect remembered container coordinates. Stockpile takes a top-down snapshot of nearby loaded terrain once per dimension on every world join, even with the dashboard closed. Use **Capture terrain** to refresh the map at your current position. The browser keeps the last capture for each of up to eight world and dimension pairs. In ceiling dimensions, capture uses a slice near your current height. Choose a dimension, zoom, fit the chests, or drag the background to pan. Nearby chests cluster when zoomed out; stacked chests can be chosen individually by their Y coordinate in the list. The last observed Ender Chest access point is labeled when that source is enabled.

Click a chest or **Select** to start a custom build-source selection. Use **Box select** and drag, or hold Shift while dragging, to add all included chests inside the rectangle. Switch back to **Pan** to drag the map. Green markers are selected, blue markers are available and grey markers are excluded. **Use selection in Build planner** applies those containers to schematic comparisons, recipe/fuel planning and new reservations. Selection is saved per world in this browser and applies to the planner workspace; it does not place blocks or move items in Minecraft. **Use all included storage** restores the full source pool, and **Select none** leaves no supplies available to the plan. Existing reservations remain protected when the selection changes.

**Exclude chest** is available in Containers, Storage map and item-location details. It removes that chest's loose and packed supplies from counts and new build plans while retaining its saved record. Excluded chests remain visible in the map and Containers so you can choose **Include chest** later. Exclusions follow physical chest halves through double-chest changes. Connected exclusions are saved locally by the mod and shared across dashboard tabs; imported snapshots keep their preferences in this browser. For live team workspaces this is a personal filter, not a server-wide unshare action. Existing reservations and historical observations are retained; release or update affected reservations explicitly.

Manual team exports omit excluded chests and personal Ender Chests. A full inventory backup still contains saved records and exclusion settings. Repeated stacks inside the same packed shulker path are added together. Expand the packed-items dropdown to see each box slot and nested path. **Highlight chest** is placed at the top of each item location, and the map can also highlight an empty remembered chest.

Use **Highlight settings** in the sidebar or at the top of the Storage map. Preview an outline, select a preset or a custom color, set the duration to 5–120 seconds, and choose whether the outline appears through walls. The settings also apply to `/stockpile find`.

In a chest or shulker screen, the green **Stockpile** button opens the storage map directly. **Exclude** becomes available after Stockpile records the container in your personal workspace and changes to **Include** when the chest is excluded. For team storage, select the team workspace on the map to manage your own exclusions. Buttons sit outside the inventory slots when space permits.

In **Build planner**, **Build sources** shows whether the plan uses all included storage or a selected set of containers. Use the column menus in the material checklist to sort by name or quantity. The two checklist toggles sit together above the table. **Only show missing materials** filters the checklist. **Include shulker contents** changes which materials are counted. Source filters, crafting options, reservations and schematic chest controls have expandable panels.

Tick **Collected** beside each material once you have the full quantity shown under **Needed**. The checklist records your progress in this browser for each build and world. If you increase the number of build copies beyond the quantity you checked, the row shows as unchecked until you collect the additional materials. The stock and shortage columns still show what Stockpile observed in storage.

## Schematic chest (development)

In a single-player world, open a regular or double chest and check **Build chest** below its slots. This becomes the destination for that world. Select source chests on **Storage map**, open **Schematic chest** in **Build planner**, then choose **Review fill** and confirm. To supply the materials by hand, leave the map selection empty and confirm **Check contents**. Use the planner dropdown to override the destination for a particular build.

The fill uses live inventories in loaded chunks in the current dimension. It tops up the listed quantities for the chosen number of builds, respects stack limits and reports shortages. Reserved supplies stay in place. Filled shulker boxes stay packed. Chunk loads, crafting, fuel use and transfers in multiplayer or LAN worlds are outside this fill operation.

**Auto-clear on completion** is on by default. After you confirm a build, Stockpile unchecks **Build chest** once the chest contains all the required materials, including materials you add later. The materials stay in the chest. Turn off auto-clear in the planner or with **Clear on completion** in the in-game Stockpile settings to keep the chest checked. **Enable schematic chest** controls the feature separately. Both settings are saved for all worlds on that installation.

## Freshness, reservations and stock history (development)

**Freshness controls** appear above each view. Choose an age threshold (default 24 hours), then optionally exclude old observations from build planning. Inventory quantities remain visible. Paused tracking, imported snapshots and offline team workspaces are labeled. Age is a warning, not proof that contents changed; reopen personal storage to refresh it. Server-shared storage refreshes only while its chunks are loaded.

In **Build planner**, **Reserve available materials** claims the feasible part of a project. The checklist distinguishes physical stock, stock reserved elsewhere, and free stock. Reservations cover specific container/item/loose-or-packed quantities, so overlapping source and dimension filters cannot allocate them twice. Recipe plans reserve actual ingredients and fuel, not hypothetical outputs. Updating replaces that project's claims atomically; **Release** frees them, and **Complete** records completion and frees them without consuming items in Minecraft. Stock decreases below claims produce a shortage warning; a reservation cannot guarantee that another player will leave the chest untouched.

Connected reservations live in the local backend and coordinate browser tabs with an inventory revision check. Automatic team reservations are enforced by the Minecraft server and owned by the authenticated player's UUID. Other teammates cannot edit them. Imported/manual browser workspaces keep local reservations, coordinated with Web Locks; browsers without that API must use the connected dashboard for reservations. Project definitions still live in the browser, so the reservation panel also lists claims whose original browser project was removed. Export project material lists separately for backup.

**Stock history** records discoveries, observed quantity changes, schematic chest transfers, replacements and forgotten records. Its 1/7/30-day summary uses observed changes in the selected source and dimension filters. Schematic chest transfers appear in the event log and are excluded from rates. Manual moves, automation, other players and delayed refreshes can affect observed increases and decreases. Export the event log as CSV. History starts when this feature is installed. Retention is bounded to 30 days, at most 1,000 events / 10,000 item changes per world and 40,000 changes across worlds. Large single observations are marked partial after 512 item types.

## Shared storage and optional server component (development)

| Where Stockpile is installed | What it supports |
| --- | --- |
| Client only | Track containers you open, use the local dashboard, and exchange team snapshots by file. Works when the server runs without Stockpile. |
| Clients and Fabric server | Receive live team counts for granted storage and coordinate shared reservations. The server operator uses `/stockpileteam` to manage access. |

Use **Shared storage** in the sidebar for explicit manual file exchange. Agree on a team code and server label, then export the selected world's storage-only team file and send it to teammates yourself. Import their files with **Import inventory**. Matching files merge by physical container coordinates; newer observations replace older ones, including double-chest deduplication. Personal Ender Chests and reservations are excluded from team exports. For a manual team workspace, select the matching Minecraft recipes version explicitly. Manual files are snapshots, carry coordinates and names, and have no live synchronization or global reservation authority. Clear stale/moved container records deliberately. The connected dashboard persists manual team workspaces locally; the hosted dashboard keeps them in browser storage. Up to ten manual workspaces are supported. Connected team-file imports are limited to 2 MB and 1,000 containers per file.

For automatic updates, the server operator installs the matching Stockpile development JAR and Fabric API in the **Fabric server's** `mods` folder and restarts it. Each player uses Stockpile on their own client. A client-only install supports manual team files. Run `/stockpile team` to open the Shared storage setup and check whether Minecraft exposes the administrator commands to your account.

An operator can run these in game. In the server console, omit the initial slash.

```text
/stockpileteam create builders
/stockpileteam add builders PlayerName
/stockpileteam share builders 100 64 -25
/stockpileteam unshare builders 100 64 -25
/stockpileteam remove builders PlayerName
```

Minecraft shows `/stockpileteam` only to users with administrator command permission. For an integrated single-player world, enable commands to use it in chat. Names resolve to online players, and UUIDs work for offline players. Coordinates use the command source's dimension. Use Minecraft's `execute in` for other dimensions. Only chests, barrels and placed shulkers can be shared. A double chest grants both halves, and a physical block can belong to only one team. Team membership grants access to counts, locations, history and reservation names. Physical container access still follows Minecraft's rules.

Authorized clients receive separate **Team** workspaces automatically over their existing Minecraft connection. The server observes explicitly granted storage about every five seconds at 20 TPS, including hopper/teammate changes to closed containers in loaded chunks. It never force-loads chunks. Server reservations use the same stock budget across teammates, with revision checks and owner validation. Personal tracking can remain paused while team updates continue. Recognized shared containers are removed from the personal storage view to avoid counting the same supplies in both workspaces; preexisting personal reservations against them become shortages and should be released/recreated in the team workspace. After unsharing, reopen a container to remember it personally again.

Grants and shared inventory are stored atomically under the server world's `stockpile/shared-teams.json` and `stockpile/shared-inventory.json`. Limits are 32 teams, 128 UUID members and 128 physical storage blocks per team. Shared packed scans are bounded to 256 packed entries per container and visibly marked incomplete if truncated. Messages are compressed with wire and expanded-size limits; reservation requests are rate-limited. There is no remote HTTP service, new login, public dashboard port, or automatic upload. Local HTTP tokens are never sent to teammates. Removing a member also releases that member’s team reservations. Revocation stops new updates and removes the current cached team workspace when the client receives the roster; it cannot recall files or copies a teammate previously exported. Offline caches are marked disconnected after 15 seconds without team updates.

## Chest Tracker GUI compatibility

With Chest Tracker Port for your Minecraft version installed, Stockpile adds a small **S** button to the main Chest Tracker screen. It moves beside the panel when the header is crowded. It opens Stockpile settings and dashboard actions. You can disable this button in Stockpile settings.

Stockpile maintains its own memory and uses Fabric screen events for this optional integration. The shortcut and a Stockpile-only item lookup were tested in game with Chest Tracker Port 2.8.4 on both supported Minecraft versions.

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

The **hosted dashboard** provides example data and lets you import an exported inventory JSON. Use **Export inventory** in the connected local dashboard, then **Import inventory** in the hosted page. Hosted imports are snapshots and do **not** sync automatically. There is no cloud upload service or phone pairing. Development builds add explicit team sharing and an optional server component that observes only granted storage; the stable 0.3.4 release remains snapshot-only.

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
- Schematic checks cover direct placement items. Recipe substitutions can also use vanilla crafting, stonecutting and optionally furnace/blast-furnace cooking with fuel planning. They do not verify existing blocks at a placement, count entities or apply custom server recipes. Separate projects can use explicit reservations in development builds.
- Waterlogged blocks, fluids, block-entity extras, overlapping regions and unsupported blocks produce review notes. The hosted preview uses vanilla block-to-item estimates; the connected dashboard also uses the game's block/item registry. Some complex modded blocks may still need manual mapping.
- Legacy numeric-ID `.schematic` files and randomized structure palettes are unsupported. Re-export in a supported format.
- Limits: 25 MB per schematic file, 64 MB expanded NBT and 8 million block positions. Large parsing jobs run in a worker with a time limit.

## Recipe substitutions

In **Build planner**, **Recipe substitutions: ON/OFF** switches between finished materials in storage and materials you can craft. It starts OFF and remembers your choice in this browser. **Have** remains the physical stock count; **Can craft** is additional coverage, and **Missing** is what the selected plan cannot supply. Crafting never runs in Minecraft automatically.

The plan uses vanilla crafting-table, stonecutting, smelting and blasting recipes extracted from the official 26.2 and 26.3 clients. New mod exports identify the running Minecraft version automatically. For older exports, select the matching **Minecraft recipes** version. `python3 scripts/make-recipes.py` reproduces the bundled recipe catalogs from the ignored official client JARs.

All directly required materials are reserved first. Other ingredients share a single stock budget across this build. Chained recipes, tags such as mixed planks, output batch sizes and leftovers are accounted for. The ordered **Crafting plan** lists actual quantities and workstations and can be exported as CSV. Material CSV exports keep stored and craftable amounts in separate columns.

Access to the listed workstations is assumed. Enable **Smelting & fuel** separately from recipe substitutions, choose whether blast furnaces are available, and select allowed fuels. Defaults allow coal and charcoal. Supported additional fuels are coal blocks, blaze rods, dried kelp blocks and lava buckets; valuable fuels are not used unless selected. Fuel, ingredients and directly required items share one stock budget. Burn-time leftovers are reused only at the same workstation and assume uninterrupted processing; idle heat loss, parallel-furnace distribution and custom cooking components are not estimated. Processing steps show workstation time, fuel and returned lava-bucket containers. Fuel must already be in the planned pool; the planner does not synthesize a new fuel just to bootstrap a cooking operation. Smithing, smoking, campfires, component-dependent/special recipes, general crafting remainders and custom server/modpack recipes remain excluded. The bounded search returns one feasible plan, which may be conservative when several recipe choices compete for supplies. A notice appears when search limits are reached. Use explicit project reservations to prevent independent builds from counting the same physical supplies.

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

For local development, use the helper below from the repository root. It uses JDK 25 from `JAVA_HOME`, a registered macOS JDK, or Prism Launcher's `java-runtime-epsilon` JDK. It validates the compiler version and keeps Gradle downloads in the ignored `.gradle-user-home/` directory unless `GRADLE_USER_HOME` is already set. Set `JAVA_HOME` explicitly if your JDK is elsewhere. Node.js 22+ and Python 3 are also needed for the automated checks; no npm install is required.

```sh
sh scripts/dev.sh check
sh scripts/dev.sh test
sh scripts/dev.sh build 26.2
sh scripts/dev.sh build 26.3
```

Run builds sequentially: Loom and Gradle share cache locks even though compiled output is separated by Minecraft version.

To run the Java smoke checks, build with the standalone compiler first so it generates the test classpath, then test that same target. Standalone intermediates live in `build/standalone-targets/<version>/`, separately from Gradle's `build/targets/<version>/`; both write JARs to `build/libs/`.

```sh
sh scripts/dev.sh standalone 26.2
sh scripts/dev.sh backend 26.2
sh scripts/dev.sh standalone 26.3
sh scripts/dev.sh backend 26.3
```

Optionally set `STOCKPILE_WHEREISIT_JAR` to a matching Where Is It JAR extracted from Chest Tracker Port before running the backend checks. This checks the installed binary API signatures without modifying the launcher instance. Full event integration requires Fabric's runtime and the in-game playtest; it cannot be exercised by the plain-JVM smoke harness.

For an interactive development client, run `sh scripts/dev.sh client 26.2` or `sh scripts/dev.sh client 26.3` on a desktop with working graphics. Use a disposable test world and the playtest checklists below. This launches Loom's development instance, separate from your normal launcher instance. To test in your own launcher instead, copy only the matching JAR from `build/libs/` into a separate test instance with the matching Minecraft, Fabric Loader and Fabric API versions. Optional Mod Menu and Chest Tracker compatibility should be tested with their matching versions installed separately.

The helper only runs local checks, builds and the development client. It does not copy JARs into existing Minecraft instances, push commits, publish releases or deploy the dashboard. The commands below remain available for direct tool use.

Requires a JDK 25 and an internet connection for dependency retrieval.

```sh
./gradlew build -Pminecraft_version=26.2
./gradlew build -Pminecraft_version=26.3
```

Development outputs are `build/libs/stockpile-0.4.0-dev.9+26.2.jar` and `build/libs/stockpile-0.4.0-dev.9+26.3.jar`. The default target remains 26.3. Per-version build directories isolate compiled classes and Fabric modules. `minecraft-targets.json` pins each target's dependencies, and `src/versions/<version>/java` contains the small shulker-content API adapter. Both supported Minecraft versions use unobfuscated names, so no remapping step is needed. The dashboard is packaged from `dist/` into the mod.

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
7. Leave a world with remembered storage and join another world or server without opening a container. Confirm the dashboard automatically selects the joined world and shows its own counts or an empty inventory. Repeat with tracking paused and after reconnecting, then return to the first world and verify its remembered counts remain separate.
8. Upload `tiny-workshop.litematic`; verify its six item types and compare the checklist with your storage.

### Find-item playtest on 26.2 and 26.3

1. Open a chest containing stone, a double chest and a placed shulker. Run `/stockpile find minecraft:stone`; verify coordinates and gold outlines, including both double-chest halves.
2. Put diamonds in a shulker inside a chest and reopen the parent chest. Find diamonds from dashboard item details; check the parent chest is outlined and chat retains the packed-box path. Exclude shulker contents and verify packed-only diamonds no longer offer that location.
3. Pause tracking and repeat a find. Verify `/stockpile clearfind`, a new search and the 30-second timeout clear the previous highlight.
4. Find a saved container outside loaded chunks, then approach it before timeout. Check the unloaded warning and eventual outline. Find a container in another dimension; check the dimension message and absence of an outline in the current one. Change dimensions while an outline is active; it must clear.
5. Break a remembered chest. Find it again; check that the missing block is reported and is not highlighted. Select another saved world in the dashboard; its action must refuse to target the currently joined world.
6. Find an Ender Chest item. Confirm the message describes the last access point and does not imply the contents belong only to that physical chest.
7. Repeat with the matching Chest Tracker Port installed: Y-search an item remembered only by Stockpile, then an item remembered by both mods. Verify the search highlights results, Chest Tracker's controls and labels still work, and component-sensitive searches do not gain guessed Stockpile matches. Repeat without Chest Tracker Port.

## Sources

- [Fabric 26.3 development changes](https://fabricmc.net/2026/09/15/263.html)
- [Fabric's 26.3 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.3)
- [Chest Tracker Port](https://modrinth.com/mod/chest-tracker-port)
- [Litematica upstream](https://github.com/maruohon/litematica)
- [Sponge schematic format specification](https://github.com/SpongePowered/Schematic-Specification)

Stockpile’s original code and artwork are available under the [MIT License](LICENSE). Minecraft textures and models used for inventory icons retain their separate rights. See [NOTICE](NOTICE) for asset and optional-mod attribution. Stockpile is an independent project. Mojang and Microsoft do not approve, endorse, or sponsor it.

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

## Full development playtest checklist

Run on both Minecraft targets before publishing:

1. Set freshness to one hour and import an older observation. Verify warnings, unchanged physical counts and exclusion from planning only when selected. Reopen a personal chest to refresh it.
2. Upload two builds sharing stone and recipe ingredients. Reserve one in tab A, then try the conflicting plan in tab B. Refresh after a conflict, adjust the quantity, and verify release/completion. Change container source and dimension filters; claims must still apply to overlapping supplies. Reduce actual stock and verify the shortage warning.
3. Open a chest, change its contents, close it, and inspect history. Confirm first discovery and forgotten-record events do not appear as consumption. Export CSV, restart Minecraft and verify retained observations/reservations.
4. Enable recipes, smelting and a supported fuel. Check a furnace and blast-furnace plan against actual vanilla cooking, insufficient-fuel shortages, batch heat reuse, protected directly required fuel, and returned lava buckets. Test a plan requiring both workstations; heat must not transfer between them.
5. Install the optional component on a disposable test server. Create a team with two authorized clients and one nonmember. Share a chest/double chest and check hopper updates in loaded chunks without reopening. Confirm unloaded chunks remain stale, Ender Chests are rejected, and nonmembers receive no team workspace. Reserve competing builds from both authorized clients and verify exactly one conflicting reservation succeeds.
6. Revoke a member, unshare a container and reconnect. Verify new updates stop, cached live workspaces are removed on roster refresh, offline workspaces are labeled and ownership checks prevent editing another teammate's reservation. Keep personal and team counts separate.
7. Export/import manual team files with agreeing labels, older timestamps, overlapping chest halves and a different server label. Confirm newest observations win, Ender Chests are omitted, and manual snapshots are visibly distinct from live team storage.

Automated checks do not replace this game-session verification. No release or server deployment is performed by the local build helper.
