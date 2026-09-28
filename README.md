# Vanguard

A Fabric utility client for anarchy servers, built for **Minecraft 1.21.11**.

![ClickGUI](docs/clickgui.png)

## Status

The foundation and the **ClickGUI** are done:

- A docked sidebar that works as a tab manager. Each category tab opens or closes its panel, and the Settings tab holds the ClickGUI's own options. The sidebar also has search, how many modules are on in each category, and your skin, name and server
- Open panels arrange themselves in a grid beside the sidebar and glide into place as tabs open and close. The whole area scrolls when it overflows
- Module rows with an on/off switch, the bound key, and a settings arrow that appears on hover. Settings open in an inset card
- A settings widget for every setting type: toggle switch, slider, mode dropdown, color picker (saturation/value, hue, alpha, copy/paste hex) and keybind
- Settings that only show when relevant (for example, Render Color only while Render is on)
- Module search: start typing anywhere. Only categories with a match stay open until you clear it
- Custom anti-aliased rendering for rounded shapes, soft shadows and line icons, plus the bundled Inter font (or Minecraft's font)
- Its own scale setting, so the menu looks the same at any Minecraft GUI scale
- Accent color or rainbow, background blur and dim, adjustable animation speed, hover descriptions
- A module/setting framework and a JSON config (`.minecraft/vanguard/config.json`), which also remembers open tabs and expanded modules

AimAssist, TriggerBot and ShieldBreaker work. The other combat, movement and render modules are **placeholders**: they declare their settings so the GUI has real content, but they don't do anything yet.

## Aim Assist

Steers the real camera toward the best target every frame (like console aim assist) instead of
snapping, so you keep control while it helps. It hooks the player's own look rotation
(Entity#turn), so the assist is visible on screen and flows with your mouse.

Settings (Combat tab):

- Speed - how quickly your aim is pulled onto the target.
- Aim At - Closest (only helps when your crosshair is off their hitbox), Head, or Body.
- Vertical - also help up and down. Off: left and right only.
- Range / Field of View - how close a target has to be, and how far from your crosshair.
- Priority - which target to pick when several are in view: Crosshair, Nearest, or Lowest Health.
- Players / Mobs - what to help aim at.
- Through Walls - also aim at targets hidden behind blocks.

How it stays smooth:

- It aims where the target is drawn this frame. Entities only move 20 times a second, so aiming at
  their raw positions makes the camera move in steps between frames.
- The motion is a critically damped spring (`AimSpring`): it speeds up and settles without
  overshooting, and moves the same at any frame rate.
- It follows a strafing target's own motion instead of trailing it, and fades in and out at the
  edges of range and field of view rather than starting or stopping abruptly.
- It doesn't chase hops. Vertical aim only corrects when your crosshair is off the hitbox, and a
  target bouncing up to a jump's height (their jumps, or the knockback from your own hits) counts as
  still standing where they were. Higher launches and drops are still tracked. With TriggerBot on,
  chasing the knockback hop used to swing the camera about 9 degrees down and back after every hit;
  now it holds within 0.1 degrees.

Measured in the live client, strafing past a target at ~50 fps: the frame-to-frame change in turn
speed dropped from 29% of the turn speed (the old version saw-toothed every game tick) to 4%.

## Trigger Bot

Attacks when your crosshair is on a target and the hit is worth taking. It checks every frame,
right after the camera turns, and attacks through the same code as a real click, so the hit goes
out the instant the crosshair lands or the weapon finishes charging.

Settings (Combat tab):

- Crits - Priority (default): in the air, holds a charged hit until you start falling so it crits;
  on the ground, hits right away. That covers jump crits and P-crits (crits off the upward
  knockback of being hit). Crits Only: never hits unless it crits. Off: hits as soon as charged.
- Spacing - how far into your reach a target must be. 100% hits the moment they step into reach
  (outspacing); lower waits for them to come closer.
- Server Position - also aims at where the server says the target is right now (on by default).
  See below.
- Hit Select - in ground trades, waits for the opponent to swing (hit or miss, read from the
  server's swing packet), then counters right away. Hits anyway after a short wait.
- Weapons Only - only with a sword, axe, mace, spear or trident.
- Skip Shields - don't waste hits on a raised shield. Axes still hit, to disable it.
- Players / Mobs - what to attack.

It only swings at full charge, and it follows the server's crit rules exactly, including what the
server thinks about your sprinting (`ServerSprintTracker`). After a sprint hit, the server stops
your sprint without telling the client. So while you keep holding sprint, you move at sprint speed
but your falling hits still crit (the sprint-crit state).

Server Position: your game draws other players and mobs a little behind their real position,
because each position update is eased in over three ticks. The exact latest position from the
server is kept as well, and the trigger bot checks your crosshair and reach against both. The
server judges reach from its own position, so against a target walking in, hits land about a tick
sooner (measured: 22.6 vs 23.6 ticks between hits on a zombie walking back in after knockback).

Hit Select: every attack, hit or miss, sends a swing packet, and it spends that player's charge.
Countering right after it also trims your next knockback. A landed sprint hit cuts the knockback
speed the server still holds for you by 40%, and that leftover adds to the next knockback you take.

Verified in the live client with an iron sword: a hit every 12 ticks (the true full-charge time)
for 6 damage on the ground, and 9-damage crits on every hit while jumping. It also landed P-crits
off a zombie's knockback without jumping, and hit a walking zombie at the edge of reach
(2.95-3.0 blocks). Crits Only, Spacing and Weapons Only also behaved as described. Hit Select's
swing and hurt timing was tested against a zombie, including swings that missed. Against players
it is only reasoned from the code, as is Skip Shields.

## Shield Breaker

Breaks raised shields with an axe from your hotbar, then switches back to what you were holding.

Settings (Combat tab):

- Automatic - breaks a raised shield by itself as soon as your crosshair is on it. Off: only when
  you attack (your own clicks, or TriggerBot's).
- Swap Back - switch back to what you were holding right after the hit.

How shields work in 1.21.11, from the game code (`Shields`):

- A shield only blocks once it has been up for 5 ticks, and only hits from within 90 degrees of
  where the holder's head is facing.
- A blocked hit from any axe puts all of the holder's shields on a 5 second (100 tick) cooldown and
  lowers them. It needs no charge, has no randomness, and works even while they're still immune
  from a previous hit.

So it breaks the shield the moment it becomes active. Any hit resets your charge anyway, so that's
the fastest way to your next full-strength hit (12 ticks later with a sword, well inside the 100
tick window). It only swaps when the server would really block you. Before the shield is fully up,
or from behind, your normal hit lands, so it doesn't swap. It picks the hotbar axe with the most
durability left, waits for the result before retrying the same player, and doesn't undo a slot
change you made yourself.

The axe is selected right before the attack is sent, the same order the game uses when you press a
hotbar key and click in the same tick. The slot comes back on the next tick. Both your client and
the server see the item change, so your charge stays in sync with the server's.

Tested against a Carpet fake player holding a shield up continuously, in a production Fabric
install (loader 0.18.4). The server saw each break as an iron axe hit that the shield blocked, which
then disabled it. The bot re-raised it every 105 ticks (the 100 tick cooldown plus the 5 tick
delay), and it was broken again straight away. Between breaks, TriggerBot's sword hits landed:
136 damage in 15 seconds against a shield that was always being raised. No swaps happen from behind
the shield, with no axe in the hotbar, or while idle with Automatic off. With it off, one click on
the shield became an axe hit that broke it.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.17.3 or newer for Minecraft 1.21.11.
2. Put `vanguard-0.1.0+1.21.11.jar` in your `.minecraft/mods` folder. Fabric API isn't required;
   the two modules Vanguard needs are bundled inside the jar.
3. Launch the Fabric 1.21.11 profile and press Right Shift in game.

## Controls

| Action | Input |
| --- | --- |
| Open or close the ClickGUI | Right Shift (the ClickGUI's own bind) |
| Open or close a category | Click its tab in the sidebar, or the × on a panel's header |
| Toggle a module | Click its row |
| Show a module's settings | Click the arrow that appears on hover, or right-click the row |
| Search | Type; Escape clears, then closes |
| Scroll | Mouse wheel over the panels |
| Reset a slider or color | Right-click it |
| Bind a key | Click the bind box, press a key (Backspace or Delete to unbind, Escape to cancel) |

## Building

You need a JDK 21 or newer to start Gradle. Gradle downloads JDK 25 on its own to run Loom; the mod itself is compiled for Java 21, like Minecraft 1.21.11.

```sh
./gradlew build        # jar in build/libs/
./gradlew test         # unit tests
./gradlew runClient    # dev client with the mod loaded
```

## Layout

```
src/main/java/dev/vanguard/
  Vanguard.java              entrypoint and singletons
  module/                    Module, Category, ModuleManager, module classes
  setting/                   Bool, Number, Enum, Color and Keybind settings
  module/modules/combat/     AimAssist, TriggerBot + placeholders
  util/                      aim math (RotationUtil, AimSpring), crit timing, server sprint tracking,
                             crosshair picking with server positions, shield rules
  config/                    JSON persistence
  gui/render/                Render2D, the shape pipeline and shader glue, icons, colors
  gui/anim/                  time-based animations and easing curves
  gui/clickgui/              screen, sidebar, panels, module rows, search, theme
  gui/clickgui/widget/       one widget per setting type
  mixin/                     keyboard hook for binds; per-frame combat hook; attack, tick and packet
                             hooks; accessors
src/main/resources/assets/vanguard/
  shaders/core/shape.*       anti-aliased rounded shapes and soft shadows
  font/                      Inter (SIL OFL 1.1, see inter-license.txt)
```

### How rendering works

Minecraft 1.21.11 builds the GUI as a list of render states and draws them later, layered by their screen bounds. `Render2D` adds its own `GuiElementRenderState`s that use the `vanguard:pipeline/shape` pipeline. Rounded corners are quarter-circle quads, so the fragment shader only has to measure distance from the corner to get anti-aliased edges, rings and shadow falloff. The shape type and its parameter are packed into the texture coordinates, which lets every shape share one vertex format and batch together.
