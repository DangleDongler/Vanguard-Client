# Vanguard

A Fabric utility client for anarchy servers, built for **Minecraft 26.2** (the version 2b2t runs).

![ClickGUI](docs/clickgui.png)

## Status

The foundation and the **ClickGUI** are done:

- Draggable, collapsible, scrollable category panels, with positions saved between sessions
- A settings widget for every setting type: toggle switch, slider, mode dropdown, color picker (saturation/value, hue, alpha, copy/paste hex) and keybind
- Settings that only show when relevant (for example, Render Color only while Render is on)
- Module search: start typing anywhere
- Custom anti-aliased rendering for rounded panels, soft shadows and icons, plus the bundled Inter font (or Minecraft's font)
- Its own scale setting, so the menu looks the same at any Minecraft GUI scale
- Accent color or rainbow, background blur and dim, adjustable animation speed, hover descriptions
- A module/setting framework and a JSON config (`.minecraft/vanguard/config.json`)

The combat, movement and render modules are **placeholders**: they declare their settings so the GUI has real content, but they don't do anything yet.

## Controls

| Action | Input |
| --- | --- |
| Open or close the ClickGUI | Right Shift (the ClickGUI's own bind) |
| Toggle a module | Left-click |
| Show a module's settings | Right-click |
| Move a panel | Drag its header |
| Collapse a panel | Right-click its header |
| Search | Type; Escape clears, then closes |
| Reset a slider or color | Right-click it |
| Bind a key | Click the bind box, press a key (Backspace or Delete to unbind, Escape to cancel) |

## Building

You need a JDK 21 or newer to start Gradle. Gradle downloads JDK 25 on its own, since Minecraft 26.2 and Loom need it.

```sh
./gradlew build        # jar in build/libs/
./gradlew test         # unit tests
./gradlew runClient    # dev client with the mod loaded
```

The jar bundles the two Fabric API modules it needs (`fabric-api-base` and `fabric-resource-loader-v1`), so you only need Fabric Loader installed to use it.

## Layout

```
src/main/java/dev/vanguard/
  Vanguard.java              entrypoint and singletons
  module/                    Module, Category, ModuleManager, module classes
  setting/                   Bool, Number, Enum, Color and Keybind settings
  config/                    JSON persistence
  gui/render/                Render2D, the shape pipeline and shader glue, colors
  gui/anim/                  time-based animations and easing curves
  gui/clickgui/              screen, panels, module rows, search, theme
  gui/clickgui/widget/       one widget per setting type
  mixin/                     keyboard hook for module binds
src/main/resources/assets/vanguard/
  shaders/core/shape.*       anti-aliased rounded shapes and soft shadows
  font/                      Inter (SIL OFL 1.1, see inter-license.txt)
```

### How rendering works

Minecraft 26.2 builds the GUI as a list of render states and draws them later, layered by their screen bounds. `Render2D` adds its own `GuiElementRenderState`s that use the `vanguard:pipeline/shape` pipeline. Rounded corners are quarter-circle quads, so the fragment shader only has to measure distance from the corner to get anti-aliased edges, rings and shadow falloff. The shape type and its parameter are packed into the texture coordinates, which lets every shape share one vertex format and batch together.
