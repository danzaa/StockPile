# Stockpile project status

**Stockpile 0.4.0** supports Minecraft 26.2 and 26.3 on Fabric with Java 25.

| Feature | Implementation |
| --- | --- |
| Find item in-game | Dashboard action, client commands, expiring through-wall outlines and optional Chest Tracker / Where Is It provider. |
| Stale inventory warnings | Adjustable age threshold, paused/offline/import notices and optional planning exclusion while physical counts stay visible. |
| Build reservations | Persistent container-level claims; atomic backend/server revisions; owner checks; adjust, release, complete and shortage reporting. Browser-only imports use Web Locks. |
| Stock history | Bounded persistent observation events, source/dimension-aware trends and CSV export, with discoveries/deletions separated from observed changes. |
| Smelting and fuel planning | Version-specific furnace/blast recipes, shared ingredient/fuel budget, per-workstation heat, supported fuel selection, timing and lava-bucket returns. |
| Storage map and exclusions | Per-dimension chest map with clusters, height-aware selection, schematic source selection, direct highlighting and persistent chest exclusions. |
| Shared server storage | Explicit storage-only file exchange plus an opt-in Fabric server component with administrator grants, UUID membership, loaded-container polling and shared reservation authority. |

The [README](README.md) covers installation, feature scope, local builds, and live team playtesting.
