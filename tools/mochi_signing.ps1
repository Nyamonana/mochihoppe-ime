# Windows-only private signing-artifact generator. Never prints passwords.
# Candidate signing does NOT satisfy or bypass the public redistribution gate.
[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('Init', 'Sign', 'Release', 'Verify')][string]$Mode)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$private = Join-Path $root 'release-private'
$store = Join-Path $private 'mochihoppe-ime-release.jks'
$credentials = Join-Path $private 'signing.credentials.clixml'
$candidate = Join-Path $private 'mochihoppe-ime-release-candidate.apk'
$java = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:/Program Files/Android/Android Studio/jbr' }
$keytool = Join-Path $java 'bin/keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to a JDK containing keytool.' }
try {
    if ($Mode -eq 'Init') {
        if ((Test-Path -LiteralPath $store) -or (Test-Path -LiteralPath $credentials)) {
            throw 'Signing state already exists. Refusing to overwrite or rotate the release identity.'
        }
        New-Item -ItemType Directory -Path $private -Force | Out-Null
        $sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        & icacls.exe $private /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing directory access.' }
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
        [Array]::Clear($bytes, 0, $bytes.Length)
        # Export-Clixml encrypts the SecureString with current-user Windows DPAPI.
        # Neither key/password nor this local credential file is sent to GitHub.
        $credential = [System.Management.Automation.PSCredential]::new('mochihoppe-ime', $secure)
        $credential | Export-Clixml -LiteralPath $credentials
        $env:MOCHI_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
        & $keytool -genkeypair -keystore $store -storetype JKS -alias $credential.UserName `
            -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 `
            -storepass:env MOCHI_SIGNING_PASSWORD -keypass:env MOCHI_SIGNING_PASSWORD `
            -dname 'CN=Mochi Hoppe IME, OU=Mobile, O=Mochi Hoppe, C=JP'
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Preserve existing files and investigate; do not automatically regenerate.' }
        Write-Output 'Created an independent production signing identity under ignored release-private/. Back up before public release.'
    } else {
        if (!(Test-Path -LiteralPath $store) -or !(Test-Path -LiteralPath $credentials)) { throw 'Signing state missing. Run Init once.' }
        $credential = Import-Clixml -LiteralPath $credentials
        $env:MOCHI_SIGNING_PASSWORD = $credential.GetNetworkCredential().Password
        if ($Mode -eq 'Release') {
            if (!(Test-Path -LiteralPath (Join-Path $root 'release/runtime-sha256.json'))) {
                throw 'Reviewed asset manifest missing. Do not bypass the redistribution gate.'
            }
            & python (Join-Path $PSScriptRoot 'mochi_release_audit.py') --check-push
            if ($LASTEXITCODE -ne 0) { throw 'Source/asset audit failed.' }
            $env:MOCHI_KEYSTORE_FILE = $store
            $env:MOCHI_KEY_ALIAS = $credential.UserName
            # Avoid persisting signing credentials in Gradle's configuration cache.
            $env:JAVA_HOME = $java
            Push-Location $root
            try {
                & .\gradlew.bat :app:assembleLiteStandardRelease -PmochiRedistributionReviewed=true --no-configuration-cache
                if ($LASTEXITCODE -ne 0) { throw 'Production build failed.' }
            } finally { Pop-Location }
            $release = Join-Path $root 'app/build/outputs/apk/liteStandard/release/app-lite-standard-release.apk'
            $manifest = Join-Path $root 'app/build/intermediates/merged_manifests/liteStandardRelease/processLiteStandardReleaseManifest/AndroidManifest.xml'
            & python (Join-Path $PSScriptRoot 'mochi_release_audit.py') --check-push --apk $release --manifest $manifest
            if ($LASTEXITCODE -ne 0) { throw 'Production APK audit failed.' }
            Copy-Item -LiteralPath $release -Destination $candidate
        }
        $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
        $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
        if (!$buildTools) { throw 'Install Android SDK build-tools first.' }
        $signer = Join-Path $buildTools.FullName 'apksigner.bat'
        if ($Mode -eq 'Sign') {
            $unsigned = Join-Path $root 'app/build/outputs/apk/liteStandard/releaseUnsigned/app-liteStandard-release-unsigned.apk'
            $manifest = Join-Path $root 'app/build/intermediates/merged_manifests/liteStandardReleaseUnsigned/processLiteStandardReleaseUnsignedManifest/AndroidManifest.xml'
            & python (Join-Path $PSScriptRoot 'mochi_release_audit.py') --check-push --apk $unsigned --manifest $manifest
            if ($LASTEXITCODE -ne 0) { throw 'APK/source audit failed. Refusing to sign.' }
            $badging = & (Join-Path $buildTools.FullName 'aapt2.exe') dump badging $unsigned
            if ($LASTEXITCODE -ne 0 -or ($badging -match 'application-debuggable') -or !($badging -match "package: name='com.mochihoppe.explorer.ime'")) {
                throw 'Refusing to sign a debuggable or unexpected package.'
            }
            & $signer sign --v4-signing-enabled false --ks $store --ks-key-alias $credential.UserName --ks-pass env:MOCHI_SIGNING_PASSWORD `
                --key-pass env:MOCHI_SIGNING_PASSWORD --out $candidate $unsigned
            if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
        }
        & $signer verify --verbose --print-certs $candidate
        if ($LASTEXITCODE -ne 0) { throw 'Candidate signature verification failed.' }
        Get-FileHash -LiteralPath $candidate -Algorithm SHA256 | Format-List Algorithm, Hash
        if ($Mode -eq 'Release') { Write-Output 'Audited production APK built using the existing independent signing identity.' }
        else { Write-Output 'Private candidate only. Do not publish before the asset redistribution review is complete.' }
    }
} finally {
    Remove-Item Env:MOCHI_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MOCHI_KEYSTORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:MOCHI_KEY_ALIAS -ErrorAction SilentlyContinue
    $credential = $null
    $secure = $null
}
