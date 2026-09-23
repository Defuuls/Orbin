# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [143-Honeydew] - 2026-09-23

### Fixed
- Stopped thread posts from requesting intrinsic height of HorizontalPager media, which crashed Compose with IllegalStateException on multi-attachment posts.

### Changed
- Restricted media playback to the thread reader and streamlined related settings.

## [142-Grape] - 2026-09-21

### Added
- Two new pale matte color themes: Banana (warm cream and harvest gold) and Apple (pale celadon and forest green).
- Exposed Color scheme selection in Display & Media settings with search index integration.

### Changed
- Streamlined feed interface by removing the floating top feed bar during scroll.
- Floating bottom navigation bar now automatically disappears when scrolling down and returns on scroll up by default.
- Defaulted Feed Grid and Images layouts to single-column full-size media presentation.

[Unreleased]: https://github.com/Defuuls/Orbin/compare/v143-Honeydew...HEAD
[143-Honeydew]: https://github.com/Defuuls/Orbin/compare/v142-Grape...v143-Honeydew
[142-Grape]: https://github.com/Defuuls/Orbin/compare/v141-Fig...v142-Grape
