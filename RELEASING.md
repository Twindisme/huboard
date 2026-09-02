# Releasing huBoard

huBoard releases are built and signed by `.github/workflows/release-huboard.yml`. Preserve the same
Android signing key for every release; losing it prevents existing installations from accepting an
update.

## One-time repository setup

Create and securely back up a release keystore, then configure these GitHub Actions secrets:

- `HUBOARD_KEYSTORE_BASE64`: base64-encoded keystore contents;
- `HUBOARD_KEYSTORE_PASSWORD`: keystore password;
- `HUBOARD_KEY_ALIAS`: signing-key alias;
- `HUBOARD_KEY_PASSWORD`: signing-key password.

Never commit the keystore, its encoded contents, or its passwords.

## Release checklist

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`. Version codes must increase.
2. Use a version name shaped like `4.0-huboard.2`.
3. Run `./gradlew :app:testRunTestsUnitTest :app:lintRunTests` with JDK 21.
4. Merge the release commit into `main`.
5. Create and push exactly one matching tag, for example `v4.0-huboard.2`.

The release workflow only accepts `v*-huboard.*` tags and verifies that the tag equals `v` plus the
APK's `versionName`. Do not push the repository's inherited historical tags.

The workflow verifies the APK signature, publishes the versioned APK and checksum, maintains the
stable `huBoard-latest.apk` asset, creates `update.json` for the in-app updater, and attaches the Hu
Tao, Ayaka, and Xiangling theme packs.
