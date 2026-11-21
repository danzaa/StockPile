# Stockpile

Stockpile is a Fabric mod for **Minecraft Java 26.2 and 26.3**. Open storage in Minecraft, then use the local browser dashboard to find items, see what you have, and gather the materials for your next build.

This branch is **0.4.0-dev.9**, a development build based on the 0.3.4 source. Published builds are listed on [GitHub Releases](https://github.com/danzaa/StockPile/releases).

## Install and start

1. Set up **Java 25**, **Fabric Loader 0.20.1 or newer**, and **Fabric API** for your Minecraft version. Many launchers manage the Java runtime for you.
2. Put one matching Stockpile JAR in your instance's `mods` folder. Replace any older Stockpile JAR. Development JARs can be built [locally](#build-and-test-locally).
3. Launch Minecraft, join a world or server, and open a few chests. Run **`/stockpile open`** to open the dashboard.
4. Open **Build planner** and upload a schematic or material list. Try [Tiny Workshop](examples/tiny-workshop.litematic) or the [example material list](examples/material-list.json).

| Minecraft | Development JAR | Fabric API used to build |
| --- | --- | --- |
| 26.2 | `stockpile-0.4.0-dev.9+26.2.jar` | `0.161.0+26.2` |
| 26.3 | `stockpile-0.4.0-dev.9+26.3.jar` | `0.166.2+26.3` |

Mod Menu and [Chest Tracker Port](https://modrinth.com/mod/chest-tracker-port) are optional. Litematica is only needed if you use it to create or work with schematics in-game.

## Find and organize your storage

- **Inventory** groups items from remembered chests, barrels, and placed shulker boxes. Open an item's details to see container coordinates and grouped packed-shulker contents. The **Ender Chest** view shows your personal Ender Chest inventory separately.
- **Highlight chest** in item details or on the Storage map shows the location in Minecraft. Highlights fade in and out and draw one outline around a double chest. **Highlight settings** controls color, duration, and whether the outline appears through walls.
- **Storage map** captures nearby terrain from loaded chunks once per dimension each session. Use **Capture terrain** to update it. Click chests or drag with **Box select** to choose several as build sources. Use **Exclude** in a chest or shulker screen or on the dashboard to keep a container out of storage counts and build planning.
- **Freshness controls** flag older observations. Reopen a container to refresh it, or turn on **Exclude old observations from build planning**. Use **Remove saved container** in Containers to forget moved or broken storage.
- **Stock history** shows observed gains and losses over 1, 7, or 30 days and exports the event log as CSV. Schematic chest transfers appear in the log separately from gains and losses.
- The dashboard follows the world or server you join, and each saved workspace has its own inventory. The world picker lets you return to an older snapshot.

With Chest Tracker Port installed, its main screen has a Stockpile shortcut. Its **Where Is It** item-ID search can also find compatible Stockpile locations in loaded chunks in the current dimension.

## Plan a build

1. Upload a `.litematic`, Sponge `.schem`, structure `.nbt`, or an ID-based JSON or CSV material list. CSV needs **Item ID** and **Total**, **Count**, or **Required** columns. Display-name-only exports need item IDs first.
2. Under **Build sources**, use all included storage or choose specific chests on the map. Pick the number of copies and, if useful, limit by dimension or include packed shulker contents.
3. The material table shows what you need, physical stock, quantities reserved elsewhere, free stock, and shortages. Sort with the column menus. Tick **Collected** when you have the full quantity for that row. Checkmarks are saved in this browser per build and world, and apply to the quantity you checked.
4. Enable **Recipe substitutions** to plan vanilla crafting and stonecutting. **Smelting & fuel** adds furnace and blast furnace recipes, fuel requirements, and ordered cooking steps. Use **Build reservations** to set aside actual items and ingredients so another project cannot count the same supplies.
5. Export material shortages or the ordered crafting plan as CSV when you need a copy.

Material estimates cover the schematic's direct placement items. Review any notes about unsupported blocks, overlapping regions, fluids, or block data. Player inventory, existing blocks at the placement site, and custom recipes are outside the estimates. Schematic uploads are limited to 25 MB per file, 64 MB of expanded NBT, and 8 million block positions.

### Stage materials in a build chest

In a private single-player world, open a regular or double chest and tick **Build chest** below its slots. In **Build planner**, open **Schematic chest**, choose **Review fill**, and confirm. With sources selected on the Storage map, Stockpile moves loose materials from eligible storage in loaded chunks in your current dimension. It respects reservations and available space. For a chest you fill by hand, clear the source selection and confirm **Check contents**.

**Auto-clear on completion** starts on. After you confirm a build, the chest's checkbox clears when it holds every required quantity, including items you add later. You can change this in the web planner or with **Clear on completion** in Minecraft's Stockpile settings. Your materials stay in the chest.

## Shared storage

| Setup | How it works |
| --- | --- |
| **Client only** | Track your own opened storage on any server. To share counts manually, export a team file from **Shared storage** and give it to teammates. Import files with the same team code and server label to merge observations, with the newest observation winning at each location. |
| **Client and Fabric server** | The server operator installs Stockpile and Fabric API on the server. Each player installs Stockpile on their client. Operators grant access to specific storage, and members get a live team workspace with automatic updates for granted containers in loaded chunks and coordinated reservations. |

An operator can use these commands after installing the server component. In the server console, omit the leading slash.

```text
/stockpileteam create builders
/stockpileteam add builders PlayerName
/stockpileteam share builders 100 64 -25
/stockpileteam unshare builders 100 64 -25
/stockpileteam remove builders PlayerName
```

Use `/stockpile team` on the client to see the setup guide and connection status. Team exports and server grants exclude personal Ender Chests. Manual team files include saved counts and coordinates, so share them with the team you intend.

## Commands and settings

| Command | What it does |
| --- | --- |
| `/stockpile` or `/stockpile settings` | Open settings. |
| `/stockpile open` | Open the dashboard and show a link in chat. |
| `/stockpile link` | Show the dashboard link. |
| `/stockpile start` and `/stockpile stop` | Resume or pause personal tracking. |
| `/stockpile find minecraft:stone` | Locate and highlight remembered stone storage in the current dimension. |
| `/stockpile clearfind` | Clear the current outline. |

The default dashboard shortcut is **B**. Change it in Minecraft's key bindings. Automatic browser opening is off by default. The dashboard language picker supports English, Hindi, Spanish, Arabic, French, Filipino, and Mongolian. Minecraft uses its own language setting.

## Local data and snapshots

Stockpile saves the inventory and settings inside your Minecraft instance at `config/stockpile/`. The local dashboard connects over loopback using a token generated for each launch. Personal counts are recorded while supported storage is open, so a closed chest can have changed since its last observation.

The standalone dashboard can import an inventory export as a snapshot. Use the connected dashboard for live refresh and in-game controls. Build lists, manual checkmarks, and browser preferences are stored in the browser. Use **Export inventory** to back up saved counts, or try importing the [example inventory](examples/example-inventory.json).

## Build and test locally

Use **JDK 25**, **Node.js 22+**, and **Python 3**. Run these from the repository root. Set `JAVA_HOME` if the helper cannot find JDK 25.

```sh
sh scripts/dev.sh check
sh scripts/dev.sh test
sh scripts/dev.sh build 26.2
sh scripts/dev.sh build 26.3
```

Both builds write JARs to `build/libs/`. Run the targets sequentially. For backend smoke tests, run `sh scripts/dev.sh standalone 26.2` followed by `sh scripts/dev.sh backend 26.2`, then repeat with `26.3`. To launch a separate development client, use `sh scripts/dev.sh client 26.2` or `sh scripts/dev.sh client 26.3`.

For an in-game check, switch worlds, highlight both halves of a double chest, create a reservation, and confirm that the Build chest checkbox clears when the last required item arrives. Test live team access, sharing, and revocation on a separate server with multiple clients.

## Support and license

Report bugs or suggest features from **Stockpile settings > Issues, support & suggestions**. [GitHub issues](https://github.com/danzaa/StockPile/issues/new/choose)

Stockpile's original code and artwork are [MIT licensed](LICENSE). Minecraft assets retain their separate rights. See [NOTICE](NOTICE) and [ASSETS.md](ASSETS.md) for attribution. Mojang and Microsoft do not endorse or sponsor Stockpile.
