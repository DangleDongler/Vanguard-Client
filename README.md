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

AimAssist and TriggerBot work. The other combat, movement and render modules are **placeholders**: they declare their settings so the GUI has real content, but they don't do anything yet.

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
- Hit Select - in ground trades, lets the opponent swing first and counters right away, so you
  take less knockback. Hits anyway after a short wait.
- Weapons Only - only with a sword, axe, mace, spear or trident.
- Skip Shields - don't waste hits on a raised shield. Axes still hit, to disable it.
- Players / Mobs - what to attack.

It only swings at full charge, and it follows the server's crit rules exactly, including what the
server thinks about your sprinting (`ServerSprintTracker`). After a sprint hit, the server stops
your sprint without telling the client. So while you keep holding sprint, you move at sprint speed
but your falling hits still crit (the sprint-crit state).

Verified in the live client with an iron sword: a hit every 12 ticks (the true full-charge time)
for 6 damage on the ground, and 9-damage crits on every hit while jumping. It also landed P-crits
off a zombie's knockback without jumping, and hit a walking zombie at the edge of reach
(2.95-3.0 blocks). Crits Only, Spacing and Weapons Only also behaved as described. Hit Select and
Skip Shields need a real player opponent and were not tested live.

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
  util/                      aim math (RotationUtil, AimSpring), crit timing, server sprint tracking
  config/                    JSON persistence
  gui/render/                Render2D, the shape pipeline and shader glue, icons, colors
  gui/anim/                  time-based animations and easing curves
  gui/clickgui/              screen, sidebar, panels, module rows, search, theme
  gui/clickgui/widget/       one widget per setting type
  mixin/                     keyboard hook for binds; per-frame combat hook; attack hook and accessors
src/main/resources/assets/vanguard/
  shaders/core/shape.*       anti-aliased rounded shapes and soft shadows
  font/                      Inter (SIL OFL 1.1, see inter-license.txt)
```

### How rendering works

Minecraft 1.21.11 builds the GUI as a list of render states and draws them later, layered by their screen bounds. `Render2D` adds its own `GuiElementRenderState`s that use the `vanguard:pipeline/shape` pipeline. Rounded corners are quarter-circle quads, so the fragment shader only has to measure distance from the corner to get anti-aliased edges, rings and shadow falloff. The shape type and its parameter are packed into the texture coordinates, which lets every shape share one vertex format and batch together.
