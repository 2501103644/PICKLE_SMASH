# Gameplay recording review and ball/swing fixes

## What is visible in the supplied recording

- The Home menu remains drawn over the court for the duration of the clip.
- The characters and ball continue moving behind the menu, so the rally looks like menu background animation rather than a playable match.
- The ball repeatedly travels between the players, but the swing pose is difficult to associate with a precise paddle contact.
- The court layout and pixel-art assets are already in place; the main issue is the timing/ownership of rally animation and the screen state.

## Changes in this build

1. PLAY starts the `PLAYING` state and the menu overlay is removed during a match. The ball and players stop updating on Home, Avatar, and Credits.
2. The ball has one assigned receiver at any moment. Only the assigned receiver can make a hit.
3. The player swing animation is triggered only after a successful ball/paddle collision check. A close incoming ball may show the ready pose, but that pose is not a swing.
4. A successful contact immediately assigns the opponent as the next receiver. This prevents the same player from swinging multiple times on the same contact.
5. The ball initially aims toward the receiver's paddle and gently steers toward that player's changing paddle position while in flight.
6. The receiver moves toward a short predicted intercept point. The player remains within their court half and depth range.
7. The clouds remain animated independently of the match and menu UI.

## Verification and remaining visual check

- Compiled with Java 21 using `javac -encoding UTF-8 -d bin src/*.java`.
- Ran a 30-second logic simulation at 60 updates per second. The two players alternated 44 successful receiver changes/hits without the ball becoming unassigned.
- The exact visual hit radius and paddle pixel position should be checked in the VS Code game window. The collision radius is controlled by `PADDLE_CONTACT_RADIUS` in `CourtPlayer.update()` and is currently 12 logical pixels.
