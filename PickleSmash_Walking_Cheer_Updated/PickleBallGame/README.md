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
   java -cp bin PickleBallHomeScreen
   ```

## Controls

- Home: UP/DOWN or W/S to select; ENTER or SPACE to activate.
- Avatar: LEFT/RIGHT or A/D to change preset; ENTER or ESC to return.
- Match: ESC returns to Home.

## Notes

The court preview runs automatically behind Home, Avatar, and Credits. PLAY resets the rally and enters the current match screen. The actual game mode is still under development; this build is intended to let you see and refine the Home Screen presentation and animated court preview.
