# Reader performance journeys

The suite measures real `MainActivity` cold/warm startup and populated production reader
renderers. `hotStartup` remains available for physical-device runs; hosted emulators can resume
without launching an Activity and produce no HOT startup sample.

`ReaderBenchmarkActivity` exists only in the baseline-profile plugin's `benchmarkRelease` and
`nonMinifiedRelease` target variants. Its Kotlin and local vector thumbnail are bound from
`app/src/benchmark`; small manifest overlays use the standard `app/src/benchmarkRelease` and
`app/src/nonMinifiedRelease` paths. The AGP `onVariants` API appends these through
`variant.sources.manifests.addStaticManifestFile` after the baseline-profile plugin copies the
release source-set manifest; conventional file locations alone are insufficient. Normal debug and
release builds do not contain this Activity or fixture. The launcher and production first-run flow
are unchanged.

The fixture prepares 200 catalog rows, posts, and files once per Activity on `Dispatchers.Default`
before publishing content. It uses the real shared `BoardScreen`, `ThreadScreen`, `ReaderTwoPane`,
and prepared `ReaderPostComment` renderer, including links, greentext, hidden spoilers, and the
selection container. It retains production lazy states and stable keys and a local thumbnail
painter. It performs no provider request, post parsing, database load, or remote image decode in a
measured renderer journey. These are UI renderer measurements; they do not
measure networking, thumbnail decode, or prove that a production repository does not refetch.
Android/iOS host tests cover repository refresh and navigation behavior separately.

| Journey | Required evidence |
| --- | --- |
| Catalog list | Visible production rows; down and up gestures change the lazy grid position |
| Catalog grid | Visible production cards; down and up gestures change the lazy grid position |
| Thread | Visible production posts; down and up gestures change the lazy list position |
| Files | Visible production file tiles; down and up gestures change the first local thumbnail |
| Two panes | A scrolled catalog opens a populated thread; thread scrolling is independent; closing it preserves the exact catalog item and pixel offset |

The two-pane fixture also asserts one prepared model build and one catalog mount. It records the
catalog's composition counts before/after selection for diagnosis: selection may recompose a row
to show its selected state. A retained mount/model is not a claim that no recomposition occurred.
Missing rows or stationary gestures fail the journey; no empty-data path skips a measurement.

Fixture setup selects a 1080×1920 / 420dpi phone or 1280×800 / 160dpi wide window before launching
an explicit Activity intent. Tests verify window-manager readback and actual measured pixel/dp
dimensions, density, text scale, and pane widths. The wide catalog must be 420px, its divider 1px,
and the detail pane 859px. Each case restores display overrides afterward. The wide fixture checks
the same `showsTwoPanes` and `readerPaneWidths` rules as production. Use an English device
locale when comparing user-visible fixture captures; the journey selectors use resource tags.

Teardown resets size or density only when an override exists, then waits within a fixed bound for
the physical `UiDevice` width/height and target-context density to settle, waits for idle, and
asserts both overrides are cleared. This occurs outside collection and leaves startup, measured
regions, cases, iterations, metrics and journey assertions unchanged. The change follows ADB
transport loss after display reset in a profile attempt and a later performance run; the latter
completed six cases before thread startup returned status -1 without complete benchmark JSON.
The earlier f1 run's complete seven-case measurements remain valid historical evidence. Both
benchmark APK variants and strict formatting/static checks passed locally. A subsequent profile
run still lost transport after startup and the teardown guard passed without reset writes, so
display reset is not established as the primary cause. Both long workflows now pin emulator
build `16428233` (37.2.12), a controlled rollback from 37.2.13, whose process-exit GLES cleanup
regression is [reported upstream](https://github.com/zeroc-ice/ice/issues/6984). API 35, system
image, guards, cases, iterations and assertions remain unchanged. The first pinned runs verified
37.2.12, then QEMU exited before boot because `-show-kernel` and `-logcat` both claimed stdio;
no tests executed and no measurement/profile artifact was produced. Both workflows now keep only
`-show-kernel`. A permanent repository guard rejects conflicting console flags; its five regression
cases bring the passing script suite to 48 tests, with repository validation passed. The baseline
job budget rises from 60 to 90 minutes to allow all six convergence cases without reducing
iterations. The corrected configuration completed all seven 0bc metric cases; that successful run
exercises the pin without proving the cause of earlier failures. Complete profile generation is
recorded below; reviewed files were copied and unsigned-release packaging passed locally.

Run all frame/startup benchmarks on a connected device:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.orbin.benchmark.AppPerformanceBenchmark
```

For emulator runs, the workflow supplies the existing `EMULATOR,DEBUGGABLE,NOT-PROFILEABLE`
environment suppressions. The assertions and measured journeys are identical. Frame timings from
a hosted emulator are useful for comparing the same runner configuration, not absolute device
performance claims. Reports and traces are uploaded by `.github/workflows/performance.yml`.
The report validator requires successful XML cases for all seven CI journeys, ten startup samples,
and five frame iterations with nonempty finite CPU-duration and overrun samples. It fails on
missing reports, skipped/failed cases, zero CPU duration, nonfinite values, or an empty iteration.
It sets no absolute timing threshold. Run this check on downloaded results with:

```bash
python benchmark/validate_reports.py path/to/benchmark/build
python -m unittest discover -s benchmark/tests
```

The verified [0bc metrics run](https://github.com/Defuuls/Orbin/actions/runs/43135470936/job/130909649235)
booted emulator 37.2.12/build 16428233 and passed all seven cases. [Artifact 13907969224](https://github.com/Defuuls/Orbin/actions/runs/43135470936/artifacts/13907969224)
contains the original JSON/XML: cold/warm medians were 426.36/189.26ms; all five renderer cases
have five finite, nonempty frame iterations. Event source `0bc6b22a0f3d8b5e40634cab897f2a80e0f63ef8`
and synthetic checkout `7af339359e2555c40c2b247444c992aaaf08e6bc` share tree
`2020f0d9b82a870b284c4a57991f3a65466823dc`. The [implementation record](../docs/design-system/IMPLEMENTATION.md#measured-performance-checkpoints)
contains the audited CPU/overrun quantiles and historical f1 checkpoint. Positive overruns are
missed deadlines on a software emulator, not proof of hardware smoothness or a causal speedup.

Generate a shipping baseline profile on a rooted API 35 `google_apis` emulator or suitable device:

```bash
./gradlew :app:assembleDebug :app:generateReleaseBaselineProfile
```

`BaselineProfileGenerator.startup` records the real launcher and startup profile. Separate list,
grid, thread, Files, and two-pane profile cases reuse `ReaderJourneys` so every renderer is
collected before its process is stopped. Fixture package rules are excluded both by the
collector and the app's profile merge filter; only exercised shipping paths are retained.
Generated files belong in `app/src/release/generated/baselineProfiles/`.

Relevant pull requests run the rooted generation workflow and upload a `baseline-profile`
artifact from a read-only generation job. Monthly/manual runs use a separate write-scoped job for
the draft PR publisher on `automation/baseline-profile`. A successful artifact must be generated and reviewed before
claiming the committed profile is current; editing fixture sources does not regenerate it.

The [0bc profile generation](https://github.com/Defuuls/Orbin/actions/runs/43135470894/job/130909649140),
attempt 1, completed actual collection/merge/generation on emulator 37.2.12/build 16428233.
[Generated artifact 13910396463](https://github.com/Defuuls/Orbin/actions/runs/43135470894/artifacts/13910396463)
and [reports 13910331427](https://github.com/Defuuls/Orbin/actions/runs/43135470894/artifacts/13910331427)
come from the same event source, synthetic checkout and tree recorded for the metrics above.
All six required generator cases passed with no failures/errors/skips among them. Thread reached
the unchanged 15-iteration limit and logged an unstable final iteration; the standard collector/test
still passed and produced merged output. This does not establish convergence of all six cases.
The combined XML has 14 cases, with eight unrelated macrobenchmark cases intentionally skipped by enabledRules;
this is separate from the full seven-case metrics run, which had no skips.

| Generated file | Bytes | Lines | SHA-256 |
| --- | ---: | ---: | --- |
| baseline-prof.txt | 3,259,902 | 32,332 | `a4e3eacf6ea9bf855259e4ac56374e252ceeef3cb5d59c12c22d603a872b14fa` |
| startup-prof.txt | 2,581,159 | 26,580 | `14514b3bb1bd5e8cf691183caf6767e9d0247105a84c9a743a85ecd41d3380d9` |

The baseline contains production reader rules; both files exclude benchmark fixture rules.
[Descriptor counts and provenance](../docs/design-system/IMPLEMENTATION.md#generated-profile-artifact)
are recorded separately from integration: both reviewed files were copied byte-for-byte into the
repository and their hashes verified. Unsigned-release packaging passed in 2m 24s with
`:app:assembleRelease -Porbin.unsignedRelease=true -Porbin.warningsAsErrors=true`; actual merge,
minification, ART-profile compilation and packaging executed. The version 199/code 217 unsigned APK
is 16,313,250 bytes, SHA-256
`5e6127a6a0e981f8ba4efe3ca8907e4938ad38d4083756a622a9d741535056cd`. Its actual manifest has
one MainActivity MAIN/LAUNCHER and no fixture; uncompressed baseline.prof/baseline.profm assets
are 16,508/1,030 bytes with valid headers, and both DM sidecars are nonempty. Before merge,
require green checks on the profile/documentation commit at [PR 813 checks](https://github.com/Defuuls/Orbin/pull/813/checks).
Unsigned packaging does not establish signing, installation or physical-device results.

The old `subscribedFeedScrollFrames` case is replaced by `catalogListScrollFrames`,
`catalogGridScrollFrames`, `threadScrollFrames`, `filesScrollFrames`, and
`twoPaneScrollRetentionFrames`. The old `startupAndFeed` profile case is replaced by `startup`,
`catalogList`, `catalogGrid`, `thread`, `files`, and `twoPane`. Cold/warm/hot startup cases remain.
