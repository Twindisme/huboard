# Privacy

huBoard is designed to keep keyboard data on the device. It has no account system, advertising,
analytics, or telemetry, and it does not send typed text, suggestions, clipboard contents,
contacts, dictionaries, or theme-script state to huBoard's maintainers.

## Update checks

Automatic update checks are enabled by default. After a successful check, huBoard waits at least 24
hours before requesting this small release manifest from GitHub again. A failed request may be
retried after at least one hour:

`https://github.com/Twindisme/huboard/releases/latest/download/update.json`

The request uses `huBoard/<version>` as its user agent. As with any web request, GitHub and the
network providers involved can observe normal connection metadata such as the IP address and time.
No keyboard content is added to the request.

Disable automatic checks under **Settings → About → Automatically check for updates**. The manual
**Check for updates** action still connects when you explicitly use it. huBoard only downloads an
APK after you tap **Download and install**; it verifies the downloaded file against the SHA-256
value in the release manifest before opening Android's installer.

## Data stored on the device

- Preferences, dictionaries, learned words, clipboard history, and imported themes stay in the
  app's local storage unless you explicitly export or share them.
- Clipboard history can be disabled and cleared in huBoard's settings.
- Contact and installed-app access are optional features controlled by Android permissions and
  huBoard settings.
- Crash logs remain local until you explicitly export them.
- Imported huBoard Motion scripts run in an isolated, resource-limited environment without
  network, filesystem, Android, clipboard, microphone, input-connection, or typed-text access.

Opening a GitHub, documentation, or community link leaves huBoard and is covered by the destination
service's privacy policy.
