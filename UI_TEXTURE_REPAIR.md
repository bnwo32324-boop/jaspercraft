# Magenta / black interface panels — what they are and what now happens

Client build `uifix1` (`classes.js?v=20260922-uifix1`), deployed 2026-09-22.

## What you were seeing

Flat magenta is Minecraft's missing-texture placeholder. Flat black is a texture object
that exists but whose pixels never arrived. Both mean one thing: a texture the interface
needs failed to load, or its GL object was regenerated without its contents — and the
engine **remembers the failure**, so the panel stays wrong until the page is reloaded.

Item and block icons kept working throughout because they come from the block atlas, which
the world renderer re-uploads constantly. Container backgrounds (`gui/container/*.png`) are
loaded once, on demand, the first time you open that screen — so they are exactly the ones
a transient failure strands. The settings screen escaped because it is mostly solid-colour
rectangles plus the widget texture, which loads at startup.

## The fix already existed — the client just never noticed it was needed

`Minecraft.refreshResources()` drops every texture and loads it all again, in place, with no
page reload. It is what **F3+T** runs, and it works in this client today. That is the manual
answer, and it is instant.

What was missing was noticing. Three things now raise a flag:

- `SimpleTexture.loadTexture` failing — a GUI texture that could not be read.
- `AbstractTexture` logging *"Tried to regenerate a missing texture!"* — a GL texture object
  rebuilt with nothing in it, which is the black case.
- `webglcontextrestored` on the canvas — a restored drawing context has no textures in it at
  all, whatever the engine still believes, and this is the one case nothing reports.

The next time a container screen draws, `refreshResources()` runs by itself.

## Guards

- **400 ms settle.** One failure is usually a burst; the repair waits for it to finish so a
  broken screen costs one reload, not twenty.
- **20 second cooldown.** If an asset is permanently broken this cannot become a reload loop.
  A restored drawing context bypasses the cooldown, because that one always deserves a repair.
- The repair skips a single frame of the screen being drawn. Nothing else is interrupted.

## From the browser console

```js
JasprUiStatus()   // { reports, repairs, lastReason, pending, wired, lastRepair }
JasprRepairUI()   // force a repair on the next container screen
```

## Honest limit

This is a fault I could not reproduce here — it needs a real GPU and a browser, and it
happens occasionally. The three detectors cover every path where the engine *admits* a
texture is gone. If it ever recurs and does **not** heal itself, check `JasprUiStatus()`:

- `reports` greater than 0 means the client saw the failure and the repair should have run.
- `reports` at 0 means nothing was reported — the textures are fine and something is leaving
  the GL state dirty instead (a wrong texture unit or program bound at draw time). That is a
  different fault with a different fix, and knowing which it is settles it in one step.

Either way F3+T remains the instant manual repair.
