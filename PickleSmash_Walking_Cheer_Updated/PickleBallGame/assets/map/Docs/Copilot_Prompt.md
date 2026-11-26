# GitHub Copilot Agent Mode Prompt — Rebuild the Pickle Smash Town Map

You are working inside my existing **Pickle Smash** Java project. I want you to build a walkable, top-down 16-bit pixel-art town map based on the reference images and asset files in `assets/map/`.

## First: inspect the existing project

1. Inspect the repository structure, Java version, entry point, game loop, rendering panel, input handling, player class, camera code, and current asset-loading code.
2. Reuse the existing packages and classes where practical. Do not delete or rewrite working gameplay systems. Do not create duplicate main classes.
3. If the project already has a camera or map system, extend it rather than creating a competing one. Explain briefly which files you will add or modify before editing.

## Asset locations

- `assets/map/Atlases/` contains the complete transparent PNG atlases.
- `assets/map/Sprites/Atlas_01/`, `Atlas_02/`, and `Atlas_03/` contain extracted transparent PNG sprites.
- `assets/map/Indexes/` are visual contact sheets with the sprite filenames printed above each crop.
- `assets/map/References/Generated_16bit_Map_Reference.png` is the main layout/style reference.
- `assets/map/References/Original_Map_Reference_Image_2.png` is the original map reference.
- `assets/map/Docs/map_layout_spec.json` contains the approximate tile dimensions, road routes, district regions and landmark positions.
- `assets/map/Docs/sprite_manifest.csv` lists extracted sprite paths and the coordinates where they came from in the source atlas.

## Target map layout

Create a single connected town map that follows the reference image. Use these zones and preserve their relative positions:

1. **Upper-left — Training Campus:** blue-roof training building and blue pickleball court, surrounded by a fence, trees and lamps.
2. **Upper-center — Barangay Level:** barangay hall with Philippine flag, small blue court and nearby houses.
3. **Upper-right — Residential Strip:** colorful homes and a few office buildings along a tree-lined road.
4. **Middle-left — Market/Sari-Sari:** sari-sari shop, small homes and storefronts.
5. **Middle-center — Fountain Plaza:** square paved plaza with a round fountain, benches, lamps, hedges and trees.
6. **Middle-right — City Level:** large modern blue sports complex with pickleball decoration and office towers.
7. **Lower-left — Residential Homes:** small houses, gardens, palms and fences.
8. **Lower-middle — Church and Farm:** church, wooden house, vegetable plots, fences and dirt paths.
9. **Lower-right — Provincial Level:** large teal-roof open sports pavilion with blue court, benches, water tower, windmill, fenced grass and cows.
10. **Far-right — River Feature:** blue river, rocks, waterfall and wooden bridge.

Connect the districts with dark gray roads, sidewalks, dashed lane markings, turning corners, T-junctions/intersections and white pedestrian crossings. Make roads normal town-road width, not oversized highways. Keep enough clear walking paths between every important building.

## Technical requirements

- Use Java Swing rendering if that is what the repository currently uses. Fit the implementation into the current engine rather than changing frameworks.
- Build the map as a tile-based world, not a single flattened screenshot. Start with a 64-column by 36-row world using 32-pixel ground tiles unless the existing engine has a different tile size.
- Keep terrain/road tiles, decorative props, landmark buildings, collision data and interactive locations in separate layers or managers.
- Use `ImageIO` to load PNGs from the classpath/resource folder or the project's existing asset loader. Handle missing files with a clear error message and a safe fallback.
- Preserve transparent pixels. Use nearest-neighbor scaling for all pixel-art assets.
- Draw order: ground and road tiles first; sidewalks and plaza next; fences/trees/props next; buildings and foreground objects last. If needed, use y-sorting for objects so the player can walk behind/in front of trees and buildings correctly.
- Add camera-follow behavior so the camera tracks the player and clamps to the map edges. Keep the player near the screen center when possible.
- Player movement should remain responsive. Do not let the player walk through building walls, fences, large trees, rocks or water. Roads, sidewalks, plaza, courts and dirt paths should be walkable.
- For large building art, create intentional collision rectangles around the building footprint/walls. Do not make the whole transparent sprite rectangle solid if that blocks the entrance or path.
- Keep the map and player coordinates in world space; apply camera translation only during rendering.
- Add a debug toggle that draws tile grid, collision rectangles, and district bounds.
- Do not hard-code absolute Windows paths. Use relative resource paths.

## Asset selection workflow

1. Open the index images to identify which numbered sprite crop matches each object.
2. Use the individual PNG crop whenever possible. If an object is grouped with nearby props, use the atlas crop as a single decorative cluster or make a careful crop from the original atlas.
3. Do not invent asset filenames. Confirm that each referenced file exists.
4. Create a centralized asset registry with named constants/keys such as `training_building`, `barangay_hall`, `city_level_complex`, `provincial_pavilion`, `sari_sari_shop`, `fountain`, `church`, `house_red_roof`, `tree_large`, `palm_tree`, `street_lamp`, `bench`, `fence`, `road_straight`, `road_corner`, `road_intersection`, `crosswalk`, `grass_tile`, `sidewalk_tile`, `water_tile`, `bridge` and `waterfall`. Map each key to a real PNG path from the pack.

## OOP and data-structure design

Use simple beginner-friendly OOP and explain the code with comments. A suitable structure is:

- `MapWorld` or the existing map class: owns dimensions, layers and world rendering.
- `TileMap`: stores tile IDs in a 2D array or `ArrayList`-based layer.
- `GameObject`: base class for positioned world objects, if this pattern already exists in the project.
- `Building`, `Decoration`, `Tree`, `RoadSign`, `Landmark`: subclasses or focused classes as appropriate.
- `AssetManager`: loads and caches images once.
- `CollisionManager`: checks the player against solid world objects.
- `Camera`: follows the player and clamps to map bounds.
- `MapDefinition`: stores district placements and object positions separately from rendering logic.

Use encapsulation (private fields and getters/setters where needed), inheritance only where it removes duplication, and `ArrayList` for lists of decorative objects/buildings. A 2D array is fine for the tile grid. Avoid overengineering.

## Deliverables

1. A working map rendered in the existing game window.
2. Camera-follow movement around the connected map.
3. Correct building/prop placement matching the reference layout.
4. Walkable roads, sidewalks, courts, plaza and paths, with collision on obstacles.
5. A debug mode for tile/collision visualization.
6. A list of all new/modified Java files and exactly where each asset folder must be placed.
7. Beginner-friendly instructions to run the project in VS Code.

## Quality checks before finishing

- Compile the project and fix all errors introduced by your changes.
- Verify all asset paths and report missing assets rather than silently skipping them.
- Check that the camera does not show outside the map.
- Check that each district connects by road or path.
- Check that the player can reach the main landmarks and cannot cross solid obstacles.
- Keep the visual scale and bright 16-bit pixel-art look consistent with the supplied reference.

Make the implementation in small, testable steps. Do not stop at a mockup or pseudocode: integrate the map into the existing runnable project.
