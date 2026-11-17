Stockpile assets

The chest logo and square UI symbols are original pixel artwork written as SVG and PNG assets.

Inventory item images are taken from, or rendered from textures and block model definitions in, the official Minecraft Java 26.3 client. Minecraft assets are copyright Mojang Studios / Microsoft and are not covered by this project’s MIT license. This is an unofficial Minecraft tool, not approved by or associated with Mojang or Microsoft.

To reproduce the icons: python3 scripts/make-icons.py /path/to/minecraft-26.3.jar
Requires Pillow and NumPy. Assets are already packaged; these packages are not needed to install or run Stockpile.

Stair icons are rendered at 128 × 128 from the official block elements and textures, including the stairs model's GUI rotation of [30, 135, 0] and scale of 0.625. The renderer preserves face UVs, culls back faces and resolves overlapping stair faces with a depth buffer. Texture sampling stays nearest-neighbor. To regenerate only the stair icons, run: python3 scripts/make-stair-icons.py /path/to/minecraft-26.3.jar

Vanilla recipe catalogs are extracted from the official Minecraft 26.2 and 26.3 recipe and item-tag data by `scripts/make-recipes.py`. Minecraft data and assets remain under their original ownership; the project MIT license covers Stockpile code.
