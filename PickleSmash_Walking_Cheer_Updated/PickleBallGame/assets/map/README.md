# Pickle Smash - Map Asset Pack

This pack contains the generated 16-bit pixel-art map assets based on the town-map reference. It is arranged for use in your Java game project with VS Code / GitHub Copilot.

## What is included

- `Atlases/` — three complete transparent PNG sprite atlases. Keep these as the master source images.
- `Sprites/Atlas_01/` — automatically isolated sprite crops from the first atlas (buildings, roads, trees and props).
- `Sprites/Atlas_02/` — automatically isolated sprite crops from the second atlas (terrain, road parts, districts, courts, river elements and decorations).
- `Sprites/Atlas_03/` — automatically isolated sprite crops from the third atlas (landmarks, NPC sprites and decorations).
- `Indexes/` — numbered contact sheets showing the extracted crop files. Use these to identify the sprite you need.
- `References/` — the style reference, original map reference, and generated 16-bit map reference.
- `Docs/sprite_manifest.csv` — file paths and source-atlas crop coordinates for every extracted sprite.
- `Docs/map_layout_spec.json` — a 64 x 36 tile coordinate guide and district/road placement plan.
- `Docs/Copilot_Prompt.md` — a ready-to-paste instruction for GitHub Copilot Agent Mode.

## Important notes

1. The PNG atlases and extracted sprite crops preserve transparency (alpha channel).
2. Automatic sprite isolation uses connected visible pixel regions. Where props touch or overlap in the generated sheet, they may remain grouped in one crop. This is why the original atlases and index pages are also included.
3. The map layout JSON is an approximate reconstruction guide, not a prebuilt collision map or an exact pixel-by-pixel tile trace.
4. For crisp pixel art, use nearest-neighbor image scaling. Do not resize sprites with bilinear or bicubic interpolation.
5. For a walkable world, draw roads/terrain first, then buildings and props, and keep collision data separate from the visual layer.

## Recommended project folder

Copy this pack's `Atlases`, `Sprites`, `References`, and `Docs` folders into your project's `assets/map/` directory. Keep the folder names unchanged so the Copilot prompt can refer to them.
