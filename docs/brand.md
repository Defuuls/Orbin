# Orbin brand

Orbin's identity is a **night theater** for imageboards: immersive, media-first, and night-mode native. The product dims the room and lets the board fill the frame.

This document replaces the prior orbital / aubergine system. Until production launcher assets are swapped, treat the aperture mark and void palette below as the source of truth for new UI and brand work.

## Core idea

**Dim the room. Let the board fill the frame.**

Orbin is a privacy-focused, read-only imageboard browser. The brand should feel cinematic, precise, and calm under pressure — not social, playful, or loud.

Voice is short and direct, slightly filmic: product copy speaks like a projectionist, not a social app.

## Mark

**Aperture / lens iris.** An open circle formed by geometric iris blades with a clear central counter. White on void black. Ownable at launcher scale without planets, stars, orbits, or literal camera hardware chrome.

### Mark principles

- Keep the silhouette simple enough to read at 24dp.
- Prefer pure white `#E8E4DC` (or `#FFFFFF`) on Void; monochrome adaptive icons may be tinted by the system.
- Do not add gradients, inner shadows, outlines, or decorative rings.
- Retire the orbital / planetary mark for new first-party surfaces.

Production vector path (to be updated when the icon lands): `app/src/main/res/drawable/ic_launcher_orbit_foreground.xml` (rename when the asset is swapped).

## Color system

Dark is the default identity, not an alternate theme.

| Role | Value | Usage |
| --- | --- | --- |
| Void | `#0A0A0C` | Primary ground, splash, chrome |
| Stage | `#12141A` | Elevated surfaces under media / captions |
| Fog | `#8B919C` | Secondary text, meta, board tags |
| Beam | `#E8E4DC` | Primary text and light accents |
| Ember | `#C45C26` | Rare focus: unread, new, subscribed |
| Soft key | `#3D5A80` | Selection, active nav, quote links |

Aubergine, mauve, blush, lilac, plum, and eggplant are **retired** as identity colors. Dynamic color and optional user themes may still recolor the shell, but first-party brand material stays on this palette.

## Typography

- UI: tight geometric sans (Inter / Roboto Flex or platform equivalent).
- Titles: slightly condensed; section labels may use generous tracking.
- High contrast Beam on Void; Fog for secondary lines.
- Prefer smaller, denser type over large decorative titles.

## Surfaces and chrome

- Near-flat matte. Soft tonal lifts instead of shadowed cards.
- Media bleeds; chrome shrinks. Favor edge-to-edge imagery over boxed chrome.
- Selection is a quiet Soft key or Ember signal — never loud fills.
- Bottom navigation is recessed and preferably icon-only on compact widths.

## Screen implications

### Feed

- Two-column adaptive catalog; tight gutters.
- Mark-only (or mark + sparse) header; minimize wordmark and actions.
- Cards are media-first with a compact caption strip (title, `/board`, Ember unread).
- Uncropped previews retain aspect character inside the grid.

### Thread reader

- Continuous reel on Void; thin separators instead of post cards.
- OP media leads full-width; replies are text-first with small thumbs.
- Greentext: classic green with `>` prefix.
- Quote links (`>>NN`): Soft key, clearly interactive (underline or chip).
- Ember reserved for "new" / watched signals.

### Media / Downloads

- Dense multi-column wall; captions off thumbs by default.
- Downloads | Wall switch with Soft key active indicator.
- Archive energy: more image, less chrome.

### Boards

- Search-forward header with the aperture mark.
- Dense `/board` list rows: code, title, Fog description; Ember for subscribed.
- Thin separators; no heavy cards.

## Usage principles

1. **Immersion first.** If chrome and content compete, content wins.
2. **Night by default.** Design for Void ground; light mode is a concession, not the brand face.
3. **Rare heat.** Ember is scarce; Soft key handles everyday interactivity.
4. **No cosmic clutter.** Avoid stars, galaxies, rockets, and literal planets.
5. **Privacy by posture.** Observe; don't broadcast. Copy stays calm and product-led.

## Lockups

Pair the aperture mark with a plain **Orbin** wordmark (semibold sans) only when space and context need the name. In immersive product chrome, the mark alone is preferred.

Showcase assets for the prior orbital system remain under `docs/assets/` until replaced; do not treat them as the current identity.
