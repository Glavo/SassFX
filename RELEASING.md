# Releasing SassFX

SassFX releases are published from Git tags by GitHub Actions. Do not run the
publication tasks from an ordinary development build.

## Prerequisites

The `release` GitHub environment must define these secrets:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` for the Central
  Publisher API;
- `SIGNING_KEY` and `SIGNING_PASSWORD` for the armored OpenPGP signing key;
- `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` for the Gradle Plugin
  Portal.

The public key corresponding to `SIGNING_KEY` must be available from a public
OpenPGP key server before publication.

## Prepare a release

Set `sassfxVersion` in `gradle/version.properties` to the stable semantic
version being prepared, and replace `Unreleased` in that version's
`CHANGELOG.md` heading with its ISO 8601 release date. Ordinary builds append
`-SNAPSHOT` to the base version. Then run the same verification used by the
release workflow:

```shell
./gradlew --no-daemon \
  -PsassfxVersion=0.1.0 \
  clean check verifyReleaseVersion verifyPublishedConsumer \
  --warning-mode all
```

Review `CHANGELOG.md`, confirm that the working tree contains the intended
release state, and create a tag whose name is the version prefixed with `v`:

```shell
git tag v0.1.0
git push origin v0.1.0
```

## Automated publication

Pushing a `v*.*.*` tag starts `.github/workflows/release.yml`. The workflow:

1. derives the project version from the tag and repeats the release checks;
2. stages every Maven publication in an isolated repository;
3. uses JReleaser to validate, sign, and deploy the staged repository through
   the Central Publisher API;
4. publishes `org.glavo.sassfx` to the Gradle Plugin Portal;
5. creates a GitHub Release with generated notes after both artifact
   repositories accept the release.

JReleaser reuses the pushed tag and does not create or replace it. A failed
workflow must be diagnosed before retrying; do not move a published release
tag to different source content. Maven Central and the Gradle Plugin Portal are
published by independent jobs. If one succeeds and the other fails, rerun only
the failed jobs so that an accepted version is not published again.

Publication configuration does not imply that a particular version has
already been released. Check Maven Central, the Gradle Plugin Portal, and the
GitHub Releases page for the externally visible state.
