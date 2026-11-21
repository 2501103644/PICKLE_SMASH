# Pickle Smash — Walking & Crowd Animation Update

This update keeps the project in its Home Screen / live-preview stage. The rally still runs automatically behind the Home, Avatar, and Credits screens. No PLAY press is required to see the court demo.

## Player walking animation

- Added four separate walking frames for the red player and four for the blue player.
- The walk cycle advances every 0.12 seconds while the player is actually changing position.
- When a player stops, the animation returns to the idle pose.
- A ready pose can replace the walk pose when the ball is approaching.
- A confirmed paddle hit has the highest priority, so the swing pose interrupts the walk cycle and plays only after contact.
- The blue walking frames are oriented to match the original blue idle artwork; the existing right-player horizontal flip turns the player toward the net.
- Red jersey tinting also applies to the red walk frames when changing avatar presets.

## Crowd animation

The animated crowd overlay has been removed. Spectators painted into the court background remain static.

## New assets

`assets/` now contains:

- `walk_red_0.png` through `walk_red_3.png`
- `walk_blue_0.png` through `walk_blue_3.png`

The images are already cropped and saved as transparent PNGs. Keep the file names unchanged because `PickleBallHomeScreen.java` loads them by name.

## Tuning the animation

In `SpriteAnimator.update(double dt)`, change `0.12` to make player steps faster or slower. A smaller number makes the walk cycle faster.


## Run in VS Code

Open the inner `PickleBallGame` folder, then run:

```bash
javac -encoding UTF-8 -d bin src/*.java
java -cp bin PickleBallHomeScreen
```
