# Upstream Compatibility Baselines

SCSSFX is an independent Java implementation whose compatibility work is
validated against fixed upstream snapshots.

| Component | Baseline | Purpose |
| --- | --- | --- |
| Dart Sass | `1.101.3` (`e8c12331ea5304a1d641d6a6bd4cb526cb3800b9`) | Sass language and diagnostic behavior |
| sass-spec | `24e61bf508f5b48968546fbf1a4c16af61048709` | Sass language conformance suite |
| JavaFX | `17.0.20` | JavaFX CSS and BSS version 6 oracle |
| JavaFX | `27-ea+25` | JavaFX CSS and BSS version 9 oracle |

Upstream source checkouts are development-only references. The build,
tests, and published artifacts must remain self-contained and must not depend
on a local upstream checkout.

OpenJFX source code is not copied into SCSSFX. JavaFX CSS and BSS support is an
independent implementation validated through public behavior and test-only
compatibility oracles.
