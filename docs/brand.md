# Orbin brand

![Orbin brand showcase](assets/orbin-brand-showcase.png)

Orbin's identity is built around an orbital mark: a planetary sphere and trajectory ring that suggests motion and navigation. The mark works cleanly across Android adaptive icons, monochrome themed icons, splash screens, documentation, and UI surfaces.

## Core idea

**Built to browse. Not to perform.**

Orbin is a privacy-focused, read-only imageboard browser. The brand should feel observant, precise, quiet, and fast rather than social, expressive, or noisy.

## Mark anatomy

- **Trajectory wedge:** adds forward direction and speed to the orbital path.
- **Broken orbit:** establishes the readable O silhouette while keeping the form open.
- **Satellite node:** creates an ownable detail that helps the icon remain recognizable at small sizes.
- **Open counter:** protects legibility as the mark scales down.

The production Android vector lives at `app/src/main/res/drawable/ic_launcher_orbit_foreground.xml`.

## Color system

| Role | Value | Usage |
| --- | --- | --- |
| Aubergine | `#4B2E63` | Deep primary identity and dark surfaces |
| Mauve | `#B776A7` | Warm tonal accents and secondary surfaces |
| Blush | `#F6E8E6` | Light background and high-contrast fields |
| Lilac | `#C9B4E6` | Interactive chips and soft containers |
| Plum | `#8A5C8F` | Active states and board accents |
| Eggplant | `#2E1B42` | Primary brand ground and splash surface |

The logo should remain white on dark in first-party brand material. Android may tint the monochrome source automatically for themed icons.

## Usage principles

1. **Quiet confidence.** Favor strong contrast, spacious layouts, and restrained typography over decorative effects.
2. **Motion without chaos.** Orbit-inspired lines and directional cuts can support the identity, but avoid stars, galaxies, rockets, or literal planet illustrations.
3. **Privacy by posture.** The brand observes rather than broadcasts. Copy should be direct, calm, and product-led.
4. **Keep the silhouette intact.** Do not remove the trajectory wedge or satellite node when using the full mark.
5. **Protect small-size clarity.** Avoid outlines, gradients, shadows inside the logo, or added details that disappear at launcher scale.

## Recommended lockups

For repository and product surfaces, pair the icon with a plain **Orbin** wordmark rather than inventing a decorative type treatment. Use bold or semibold sans-serif typography and let the symbol carry the distinctive character.

The primary showcase asset is `docs/assets/orbin-brand-showcase.svg`.
