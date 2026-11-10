# TikTok iOS reference audit

Reference checked on 2026-11-10 against the
[official US iPhone App Store listing](https://apps.apple.com/us/app/tiktok-videos-shop-live/id835599320).
The storefront identifies **47.7.0**, dated **2026-11-09**. On the same check,
[Apple's lookup response](https://itunes.apple.com/lookup?id=835599320&country=us)
still identified 47.6.0, dated 2026-11-02. The newer storefront version is the
version reference; the lookup response supplies the official screenshot URLs.

## Pinned visual sources

- [Discovery / STEM feed](https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/27/ce/2f/27ce2fc4-a338-95ab-3bbe-1b9062cf537d/e6733d76-8afb-4efa-b9a1-9937cdd227f7_STEM_iOS_2208.png/1176x2088bb.png)
- [Recipe feed with caption and sound footer](https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/25/77/8b/25778baf-1cdf-6732-6fd0-2ec81e1323f0/aebb5dbd-18b1-423e-b417-f6f7864e3404_Recipe_iOS_2208.png/1176x2088bb.png)
- [Home / discovery overview](https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/6c/a1/6a/6ca16a58-1937-7122-e65b-2d901f84fdbb/oEEEv6yBAvIC3s9IGZ1A83wu5TAjAsxiAjLig.png/1176x2088bb.png)

These official images show a full-height media viewport, overlaid feed tabs and
search, a filled white action column at the right, a caption at the lower left,
and five navigation positions in a black bar. The center action has a white
rounded face with cyan and pink edges. The Instagram floating capsule, story
ring rail, post header, and horizontal action row do not form this reference.

## Layout contract

The following point sizes are implementation targets derived from the
proportions of the displayed phone viewport at a nominal 390-point width.
They are not measurements of a raw 390-point iPhone capture.

| Area | Target and behavior |
| --- | --- |
| Home canvas | Black, full available width and height above navigation; one media/thread page per vertical swipe. Insets apply to overlay controls, rather than shrinking the media into a card. |
| Feed tabs | Centered near the top safe area; approximately 18sp, bold white selected text and quieter inactive text. The source/search controls remain actual working actions. |
| Media | Use the complete immersive page as the media surface; preserve the attachment aspect ratio, including black letterboxing for landscape video, and retain access to the complete image. Paging must pause the previous video and honor persisted playback settings. Images and text-only posts retain usable representations. |
| Right action column | Approximately 32dp filled white glyphs, 12–13sp counts, 48dp avatar, and a column center approximately 31dp from the right edge. Each interactive control retains at least a 48dp touch target. Active states use a clear accent. |
| Caption | Left-aligned white author/source and caption near the lower edge, with a dark readability gradient. Reserve the right column and bottom navigation so text and controls do not overlap. |
| Bottom navigation | Flush black full-width bar, nominal 52dp row plus the platform bottom safe area. Five positions; 24dp icons with approximately 11sp labels. No floating capsule, horizontal exterior margins, or selected gray pill. |
| Center action | Approximately 46×30dp visual badge, white face with cyan on the left and pink on the right, around 3dp offset, black plus, and a minimum 48dp interactive target. It must open the agreed real Orbin action. |
| Search | Light or dark flat search surface, compact gray search field, clear/back actions, and actual result navigation. No inactive fake search controls. |
| Settings | Settings and privacy hierarchy, centered title/back control, compact grouped rows, section labels, trailing values/chevrons, and working switches or choice subpages. Retain Orbin's actual settings. |
| Replies | Compact avatar/author/body columns and subdued metadata; real thread text, quote links, attachments, and reading position remain accessible. |
| Other destinations | Keep the five real Orbin destinations and all 91 provider-qualified board identities/icons. TikTok account, shopping, messaging, LIVE, and posting labels must not imply services Orbin does not provide. |
| Accessibility | Preserve VoiceOver/TalkBack labels, distinguish selection beyond color, and let large text grow or scroll rather than clip. Respect safe areas and narrow windows. |

The reference palette uses black media/navigation surfaces, white glyphs,
muted gray inactive controls, pink `#FE2C55`, and cyan `#25F4EE`. The accent
values are implementation targets for the visible TikTok color treatment.

## Functional source checks

TikTok's official documentation confirms
[For You, Following, and Friends feeds](https://support.tiktok.com/en/using-tiktok/exploring-videos/how-tiktok-recommends-content),
and explicitly notes that some accounts show
[Discover instead of Friends](https://support.tiktok.com/en/using-tiktok/exploring-videos/friends-tab).
The [Manage topics instructions](https://support.tiktok.com/en/account-and-privacy/account-privacy-settings/manage-topics)
place Settings and privacy under the profile menu. Those sources establish
navigation and hierarchy; they do not provide a complete settings screenshot
set. Their TikTok-specific options must not replace working Orbin features.

## Exactness and review boundary

The pinned sources are marketing composites, with an app image inside a phone
frame and presentation text outside it. They are not raw device captures and
do not show every screen, account variation, or regional rollout. Screenshot
parity can be established against a supplied raw capture at the same viewport,
safe-area configuration, font scale, and app state. These sources establish a
concrete visual contract but cannot certify universal pixel identity.

Shared-composable screenshot tests should cover the immersive Home page,
five-position navigation, working center action, action rail/caption clearance,
secondary destinations, light/dark settings, large text, and the existing board
icon atlases. Host captures in iOS mode must be described as host captures;
native simulator/device evidence must be identified separately.

## Typography

The shared Android/iOS interface bundles unmodified static TikTok Sans v4.000 faces
from the [official TikTok font release](https://github.com/tiktok/TikTokSans/releases/tag/v4.000),
including regular, medium, semibold, bold, and matching italics. The OFL-1.1 license,
source archive checksum, and per-file SHA256 values are packaged under
`ui-next/src/commonMain/composeResources/files/licenses/`. Native fallback remains
available for glyphs outside the bundled Latin-family coverage.
