# Settings Guide

This page describes the current settings model for **v121 (Yuki)**. Settings take effect immediately
unless a control explicitly says otherwise. Local preference storage is encrypted.

The exact wording and grouping can evolve with the interface, so this guide focuses on what each
control changes rather than preserving old screen choreography.

## Site / provider

When more than one provider is available, the active-provider control selects which imageboard
engine Orbin browses. Current builds include Vichan/4chan-compatible and LynxChan/BBW Chan
providers.

## Content & Feed

| Setting | What it does |
| --- | --- |
| Personalized home feed | Enables the subscribed/personalized feed experience. |
| Subscriptions | Chooses the boards that contribute to your feed and related subscribed-board surfaces. |
| All boards | Opens the complete board browser for the active provider. |
| All media | Opens the cross-board media wall. |
| Deep scan for reply media | Extends All media by walking threads for reply attachments. Slower and more network-intensive. |
| Built-in content filter | Describes Orbin's non-optional built-in safety filtering. |
| Hidden tags | Removes matching content from applicable browsing surfaces. |
| Muted tags | Keeps matching content visible but de-emphasized. |
| Hide NSFW boards | Removes NSFW boards from applicable lists/pickers. |
| Hide text-only threads | Removes threads without displayable media. |
| Refresh feed on return | Controls how stale cached feed data may be before an automatic refresh. |
| Threads per board | Limits each subscribed board's contribution to the combined feed. |
| Run setup again | Reopens onboarding/setup choices. |

### Feed/catalog layout

Grid is the primary feed and board-catalog presentation. **List is no longer a selectable layout.**
The grid uses a larger minimum card width and stronger title hierarchy for compact-phone readability.
An **Images** view remains available for media-first browsing.

Old saved List state is migrated behaviorally by rendering Grid, so no manual preference cleanup is
required after upgrading.

## Notifications

| Setting | What it does |
| --- | --- |
| Thread watch notifications | Notifies when watched threads gain replies. |
| Quiet hours | Suppresses watch notifications during the configured local-time window. |

## Appearance

| Setting | What it does |
| --- | --- |
| Color theme | Chooses an Orbin/imageboard-inspired palette. |
| Theme mode | Light, dark, or system behavior. |
| Dynamic color | Uses supported Material You colors from the device. |
| AMOLED black | Uses pure black surfaces in dark mode where supported. |
| Open threads as | Controls the navigation presentation used to open a thread. |
| Full-screen feed | Reduces/hides surrounding chrome to maximize feed space. |
| Font size | Changes Orbin's global text scale. |
| Thumbnail/grid size controls | Adjust media/card density on surfaces that expose size control. |

The current grid design deliberately refuses extremely narrow thread cards. This means changing size
may alter column count sooner than older releases did, especially on small phones.

## Media & Playback

| Setting | What it does |
| --- | --- |
| Show media | Filters applicable surfaces to all media, images, or videos. |
| Autoplay videos | Enables automatic playback where supported. |
| Autoplay videos in feed | Allows the active feed preview video to play inline; feed autoplay starts muted. |
| Mute by default | Controls initial audio state in normal playback contexts. |
| Fullscreen video | Allows edge-to-edge video playback. |
| Media carousel options | Controls multi-attachment presentation where exposed. |
| Thumbnail size | Changes attachment/media-wall density where applicable. |

## Privacy & Network

| Setting | What it does |
| --- | --- |
| Biometric/app lock | Requires device authentication before protected Orbin content is shown. |
| Save recent searches | Controls local recent-search history. |
| DNS resolver | Chooses the encrypted DNS resolver. If a network blocks it, Orbin can report system-resolver fallback. |
| User agent | Controls the configured HTTP user-agent behavior where exposed. |
| Internal updater | Enables Orbin's release-metadata update check. Installation remains manual. |

Application traffic is HTTPS-only. Private app state is excluded from Android cloud backup/device
transfer.

## Storage & Backup

| Setting | What it does |
| --- | --- |
| Saved media folder | Chooses where downloaded media and exported thread-link text files are written. Default: `Downloads/Orbin`. |
| Folder structure | Organizes downloads flat, by board, by thread, or board then thread. |
| Export/backup | Writes portable app configuration/data to a user-selected file. |
| Import/restore | Merges a supported backup into existing state rather than blindly replacing it. |

**Important:** portable backup files and saved-link `.txt` exports are plaintext. The live app
database/preferences are encrypted, but portable exports are intentionally readable outside Orbin.

## Accessibility behavior

Orbin's interface is tested with increased font scales, screen-reader semantics, and screenshot
baselines. On compact screens, readability takes priority over maximizing the number of grid columns.

## Developer note

If this page and the app disagree, treat the shipped UI and repository source as authoritative and
update this document in the same PR. `docs/wiki/` is mirrored automatically to the public GitHub
Wiki after changes reach `main`.
