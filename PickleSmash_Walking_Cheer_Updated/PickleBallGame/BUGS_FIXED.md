# Pickle Smash — Current Fix Log

## Live Home-screen court demo

- Court characters and the pickleball move automatically when the application opens.
- The players stay within their court halves and follow the current ball receiver position.
- The ball steers toward the receiving player's moving paddle position.
- A swing animation starts only after valid ball/paddle contact.
- Clouds move independently across the sky.

## Walking and spectator animations

- Added four-frame walking cycles for both players. The walk frames advance while a player moves and return to idle when movement stops.
- Swing has priority over walking, so a hit is visually clear and does not continue cycling through walking frames.
- Added idle, cheer, clap, and wave audience sprites.
- Left and right spectator groups are intentionally offset in their pose cycle to make the bleachers feel more natural.
- New PNG assets are loaded by `AssetLoader` using their exact names in `assets/`.

## Verification performed

- All Java source files compile successfully with Java 21.
- Smoke test confirmed all eight walking frames and all four crowd pose files are found by `AssetLoader`.
- Sprite animation state test passed for idle → walk → ready → swing → walk → idle.
- A headless Swing render smoke test produced a Home Screen preview with the player and crowd animation assets loaded.

The final visual feel and exact sprite placement should still be checked in the user's VS Code window at the target screen resolution.
