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

The combat, movement and render modules are **placeholders**: they declare their settings so the GUI has real content, but they don't do anything yet.

## Aim Assist

The first fully working module. It steers the real camera toward the best target every frame
(like console aim assist) instead of snapping, so you keep control while it helps. It hooks the
player's own look rotation (Entity#turn), so the assist is visible on screen and flows with your
mouse rather than sending separate rotation packets.

Settings (Combat tab):

- Target - how to pick between targets: Crosshair, Distance, Health, or Smart (a weighted blend).
- Aim Point - Eyes, Body, or Nearest (tracks the closest part of the hitbox).
- Range / FOV - only assist within this distance and this cone of your crosshair.
- Players / Hostiles / Passives - which entity kinds to help against.
- Horizontal / Vertical - pull strength per axis (frame-rate independent).
- Max Speed - caps how fast the camera turns, so it stays human-plausible.
- Deadzone - stops assisting once you're this close, leaving micro-aim to you.
- Slowdown - reduces your own mouse sensitivity near a target (aim magnetism).
- Prediction - leads a strafing target by its smoothed velocity.
- Jitter / Reaction - smoothed randomness and a delay before locking a new target.
- While Attacking - only assist while holding attack.
- Require Visible - ignore targets you can't see (line-of-sight raycast).

Verified on 1.21.11 with both the dev client and the release jar in a production Fabric install.
Starting at 0/0 (yaw/pitch) with a pig about 34 degrees to the side, the view eased onto it:
0.0/0.0 -> 17.5/6.2 -> 30.5/13.3 over about two seconds, stopping on the pig's hitbox.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 1.21.11.
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
  module/modules/combat/     AimAssist (first working module) + placeholders
  util/RotationUtil          aim math: yaw/pitch to a point, closest hitbox point
  config/                    JSON persistence
  gui/render/                Render2D, the shape pipeline and shader glue, icons, colors
  gui/anim/                  time-based animations and easing curves
  gui/clickgui/              screen, sidebar, panels, module rows, search, theme
  gui/clickgui/widget/       one widget per setting type
  mixin/                     keyboard hook for binds; camera-turn hook for aim assist
src/main/resources/assets/vanguard/
  shaders/core/shape.*       anti-aliased rounded shapes and soft shadows
  font/                      Inter (SIL OFL 1.1, see inter-license.txt)
```

### How rendering works

Minecraft 1.21.11 builds the GUI as a list of render states and draws them later, layered by their screen bounds. `Render2D` adds its own `GuiElementRenderState`s that use the `vanguard:pipeline/shape` pipeline. Rounded corners are quarter-circle quads, so the fragment shader only has to measure distance from the corner to get anti-aliased edges, rings and shadow falloff. The shape type and its parameter are packed into the texture coordinates, which lets every shape share one vertex format and batch together.
