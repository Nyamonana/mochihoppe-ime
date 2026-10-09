# Private production signing (Windows)

Public target: https://github.com/Nyamonana/mochihoppe-ime
App name: もちエクIME. Package ID remains `com.mochihoppe.explorer.ime`.

`tools/mochi_signing.ps1 -Mode Init` creates an independent RSA-4096 JKS identity
once; it refuses to overwrite either an existing key or credential file. The
keystore and current-user Windows DPAPI-encrypted credentials are in ignored
`release-private/`, with directory access restricted to the current Windows user
and SYSTEM. Passwords are randomly generated and never printed or included in
command-line arguments. Do not commit, upload, or share this directory.

After `assembleLiteStandardReleaseUnsigned`, `-Mode Sign` audits the package and
signs a private, installable release candidate. `-Mode Verify` verifies it again.
These commands do NOT mark the redistribution review complete and do NOT publish
anything. An APK being signed is not license clearance. After the reviewed asset
manifest and notices are complete, `-Mode Release` audits the source, builds the
real release variant with the existing key, and audits the final APK. Credentials
are passed through process environment variables, then removed; configuration
cache is disabled. Publication is a separate explicit action.

## Owner-managed protected backup

- Back up the JKS safely and separately from the source code. Losing the key can
  prevent updates to an app already installed under this package ID.
- DPAPI credentials can normally be decrypted only by the same Windows user on
  this Windows installation. Copying the encrypted XML alone is NOT a portable
  password backup. The owner should promptly export/store the password
  in their own password manager or other protected portable backup. Do not put
  it into GitHub, chat, shell history, a public log, or a release attachment.
- To retrieve it locally, the owner may use PowerShell `Import-Clixml` on
  `release-private/signing.credentials.clixml` and inspect the resulting
  PSCredential in their own private environment. The signing script deliberately
  does not print it or copy it to the clipboard.
- No portable off-machine backup has been verified by the build process. The
  local key remains protected here; keep a separate owner-controlled backup.
- The first production APK uses a different signing certificate from the debug
  APK. Android will not install it as an update over the debug build. Back up
  needed dictionaries/settings before uninstalling the debug app yourself;
  recently used images/favorites are deliberately not cloud-backed-up. No script
  here uninstalls apps or changes a device's selected IME.

Never switch to a new production key merely to make a build succeed. Preserve the
key and its password across future releases.
