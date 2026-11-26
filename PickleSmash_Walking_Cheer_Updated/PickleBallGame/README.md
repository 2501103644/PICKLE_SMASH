# PICKLE SMASH — Home Screen Live Court Demo

This is the current Home Screen development build. It keeps the menu visible while a small automatic pickleball rally plays in the background.

## Features in this build

- Home Screen menu: PLAY, AVATAR, CREDITS, and EXIT.
- Automatic court demonstration as soon as the program opens.
- Players stay within their own half of the blue court and move toward the ball.
- Four-frame walking animations play only while a player is moving.
- Paddle swing animation plays only after a confirmed ball-and-paddle contact.
- Ball flight steers toward the receiver's current paddle position.
- Pixel clouds move independently across the sky.
- The background spectators, buildings, court markings, and net stay still.
- Avatar presets tint the red player's jersey, including its walking frames.

## Project layout

- `src/PickleBallHomeScreen.java` — main Swing window, live demo loop, menu states, player movement, walk/swing animation states, ball physics, and moving clouds.
- `src/PickleSmashCityMap.java` — walkable town map opened from PLAY; keeps the Home screen character and adds camera-follow movement, collisions, landmark prompts, and map debugging.
- `src/TileMap.java` — separate 64 x 36 ground, road, sidewalk, and water layers using 32-pixel tiles.
- `src/MapAssetRegistry.java` — named map asset keys and their paths inside the supplied map pack.
- `assets/map/` — supplied map pack. Keep its `Atlases/`, `Sprites/`, `Indexes/`, `References/`, and `Docs/` directories in this exact location; do not flatten or rename them.
- `src/Main.java` — Pickle Smash application launcher; starts the home screen on Swing's event thread.
- `src/AssetLoader.java` — image loading, sprite sheet slicing, transparency processing, and jersey tinting.
- `assets/court_background.png` — city pickleball court background.
- `assets/spritesheet.png` — original idle, ready, swing, and ball frames.
- `assets/walk_red_0.png` to `walk_red_3.png` — red player walking cycle.
- `assets/walk_blue_0.png` to `walk_blue_3.png` — blue player walking cycle.
- `assets/ui_elements.png` — original title/UI artwork sheet; the title is rendered as “PICKLE SMASH” in code.
- `ANIMATION_UPDATES.md` — how the new animation system works and how to tune it.

## Run in VS Code

1. Extract the ZIP file.
2. Open the inner `PickleBallGame` folder in VS Code.
3. Open the terminal in that folder.
4. Compile:
   ```bash
   javac -encoding UTF-8 -d bin src/*.java
   ```
5. Run:
   ```bash
   java -cp bin Main
   ```

## Controls

- Home: UP/DOWN or W/S to select; ENTER or SPACE to activate.
- Avatar: LEFT/RIGHT or A/D to change preset; ENTER or ESC to return.
- City map: WASD or arrow keys to walk; hold SHIFT to jog. Use the mouse wheel or +/- to zoom, M for full-map view, and E or SPACE near a landmark to inspect it. ESC returns to Home (or closes an open landmark panel first).
- City map: press 1–4 to warp between districts, F3 to toggle tile/collision/district debug overlays, and F12 to export a 2048 x 1152 PNG.

## Notes

The court preview runs automatically behind Home, Avatar, and Credits. PLAY opens the map in `PickleSmashCityMap.java`. Buildings, fences, trees, and river water block movement; roads, sidewalks, courts, plaza, dirt paths, and the river bridge remain walkable. The map pack must be placed at `PickleBallGame/assets/map/` beside `assets/spritesheet.png`.
