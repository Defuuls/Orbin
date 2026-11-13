# Orbin brand

Orbin's identity is a **night theater** for imageboards: immersive, media-first, and night-mode native. The product dims the room and lets the board fill the frame.

This document replaces the prior orbital / aubergine system. Production launcher, splash and notification assets now use the card-stack mark on coral `#FF5D68` described below. In-app UI color tokens are the Coral ReaderTokens palette (`#C73E4E` light accent, `#FF8A96` dark accent).

## Core idea

**Dim the room. Let the board fill the frame.**

Orbin is a privacy-focused, read-only imageboard browser. The brand should feel cinematic, precise, and calm under pressure — not social, playful, or loud.

Voice is short and direct, slightly filmic: product copy speaks like a projectionist, not a social app.

## Mark

**Card stack.** Two offset rounded cards on a coral `#FF5D68` tile: a dark-coral `#B8323F` back card and a white front card with a play-triangle notch (and, at larger sizes, two thread lines). The mark reads as boards, threads and media. It uses no circles, rings, targets or letter O.

A second, small version of the same mark drops the thread lines and scales the front card and play triangle up 10%. It is used at 48px and under and for the monochrome themed-icon layer, so the silhouette still holds at 24dp.

### Mark principles

- Keep the silhouette simple enough to read at 24dp (use the small mark at that size).
- Icon coral is `#FF5D68`; the back card is `#B8323F`; the front card is white. Monochrome adaptive icons may be tinted by the system; the play triangle is a hole in that layer.
- Do not add gradients, inner shadows, outlines, circles, rings, or a letter O.
- The aperture / iris mark and the orbital / planetary mark are retired for new first-party surfaces.

Production sources: [`docs/assets/orbin-icon-mark.svg`](assets/orbin-icon-mark.svg) and [`docs/assets/orbin-icon-mark-small.svg`](assets/orbin-icon-mark-small.svg). Regenerated paths: `app/src/main/res/drawable/ic_launcher_foreground.xml` (foreground) and `app/src/main/res/drawable/ic_launcher_monochrome.xml` (monochrome). See [launcher-icons.md](launcher-icons.md).

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

- Search-forward header with the card-stack mark.
- Dense `/board` list rows: code, title, Fog description; Ember for subscribed.
- Thin separators; no heavy cards.

## Usage principles

1. **Immersion first.** If chrome and content compete, content wins.
2. **Night by default.** Design for Void ground; light mode is a concession, not the brand face.
3. **Rare heat.** Ember is scarce; Soft key handles everyday interactivity.
4. **No cosmic clutter.** Avoid stars, galaxies, rockets, and literal planets.
5. **Privacy by posture.** Observe; don't broadcast. Copy stays calm and product-led.

## Lockups

Pair the card-stack mark with a plain **Orbin** wordmark (semibold sans) only when space and context need the name. In immersive product chrome, the mark alone is preferred.

Showcase assets for the prior orbital system remain under `docs/assets/` until replaced; do not treat them as the current identity.
