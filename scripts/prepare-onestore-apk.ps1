[CmdletBinding()]
param(
    [Parameter()]
    [string]$ArtifactPath,

    [Parameter()]
    [string]$ConfigPath,

    [Parameter()]
    [string]$ReleaseRoot,

    [Parameter()]
    [switch]$ValidateOnly,

    [Parameter()]
    [long]$MinimumVersionCodeExclusive,

    [Parameter()]
    [switch]$ReplacePreparedArtifact
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail {
    param([Parameter(Mandatory = $true)][string]$Message)
    throw "[ONE store APK] $Message"
}

function Get-NormalizedSha256 {
    param([Parameter(Mandatory = $true)][string]$Value)
    $trimmed = $Value.Trim()
    if ($trimmed -notmatch '^(?:[0-9A-Fa-f]{64}|(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})$') {
        Fail "Invalid SHA-256 value: expected 64 hexadecimal digits, with optional byte separators."
    }
    return ($trimmed -replace ':', '').ToLowerInvariant()
}

function Invoke-CheckedTool {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $output = @(& $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    if ($LASTEXITCODE -ne 0) {
        Fail "$Label failed with exit code $LASTEXITCODE."
    }
    return $output
}

function Get-AndroidBuildTools {
    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($sdkRoot in $sdkCandidates) {
        $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
            continue
        }

        $versions = Get-ChildItem -LiteralPath $buildToolsRoot -Directory | ForEach-Object {
            $parsed = $null
            if ([version]::TryParse($_.Name, [ref]$parsed)) {
                [pscustomobject]@{ Directory = $_; Version = $parsed }
            }
        } | Sort-Object Version -Descending

        foreach ($candidate in $versions) {
            $aapt = Join-Path $candidate.Directory.FullName 'aapt.exe'
            $apkSigner = Join-Path $candidate.Directory.FullName 'apksigner.bat'
            if ((Test-Path -LiteralPath $aapt -PathType Leaf) -and
                (Test-Path -LiteralPath $apkSigner -PathType Leaf)) {
                return [pscustomobject]@{
                    SdkRoot = $sdkRoot
                    Version = $candidate.Directory.Name
                    Aapt = $aapt
                    ApkSigner = $apkSigner
                }
            }
        }
    }

    Fail 'Android SDK build-tools with aapt.exe and apksigner.bat were not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.'
}

function Get-ApkIdentity {
    param(
        [Parameter(Mandatory = $true)][string[]]$Badging,
        [Parameter(Mandatory = $true)][bool]$RequireNonDebug
    )

    $packageLine = $Badging | Where-Object { $_ -match '^package:\s' } | Select-Object -First 1
    $minSdkLine = $Badging | Where-Object { $_ -match '^sdkVersion:' } | Select-Object -First 1
    $targetSdkLine = $Badging | Where-Object { $_ -match '^targetSdkVersion:' } | Select-Object -First 1

    if (-not $packageLine -or
        $packageLine -notmatch "name='(?<package>[^']+)'\s+versionCode='(?<code>\d+)'\s+versionName='(?<name>[^']*)'") {
        Fail 'aapt output did not contain a parseable package, versionCode, and versionName.'
    }
    $packageName = $Matches['package']
    $versionCode = [long]$Matches['code']
    $versionName = $Matches['name']

    if (-not $minSdkLine -or $minSdkLine -notmatch "^sdkVersion:'(?<sdk>\d+)'$") {
        Fail 'aapt output did not contain a numeric minSdkVersion.'
    }
    $minSdk = [int]$Matches['sdk']

    if (-not $targetSdkLine -or $targetSdkLine -notmatch "^targetSdkVersion:'(?<sdk>\d+)'$") {
        Fail 'aapt output did not contain a numeric targetSdkVersion.'
    }
    $targetSdk = [int]$Matches['sdk']

    if ($RequireNonDebug -and
        ($Badging | Where-Object { $_ -match '^application-debuggable$|testOnly=.true.' })) {
        Fail 'APK is debuggable or testOnly; a release APK is required.'
    }

    return [pscustomobject]@{
        PackageName = $packageName
        VersionCode = $versionCode
        VersionName = $versionName
        MinSdk = $minSdk
        TargetSdk = $targetSdk
    }
}

function Get-ApkSignature {
    param([Parameter(Mandatory = $true)][string[]]$VerificationOutput)

    $verified = @($VerificationOutput | Where-Object { $_ -eq 'Verifies' }).Count -gt 0
    $signerLine = $VerificationOutput | Where-Object { $_ -match '^Number of signers:' } | Select-Object -First 1
    $certificateLine = $VerificationOutput |
        Where-Object { $_ -match 'certificate SHA-256 digest:' } |
        Select-Object -First 1

    if (-not $verified) {
        Fail 'apksigner did not report a verified APK.'
    }
    if (-not $signerLine -or $signerLine -notmatch '^Number of signers:\s*(?<count>\d+)$') {
        Fail 'apksigner output did not contain the signer count.'
    }
    $signerCount = [int]$Matches['count']

    if (-not $certificateLine -or $certificateLine -notmatch 'certificate SHA-256 digest:\s*(?<digest>[0-9A-Fa-f:]+)$') {
        Fail 'apksigner output did not contain a certificate SHA-256 digest.'
    }
    $certificateSha256 = Get-NormalizedSha256 $Matches['digest']

    $v2OrNewer = @(@('v2', 'v3', 'v3.1', 'v3.2', 'v4') | Where-Object {
        $scheme = $_
        @($VerificationOutput | Where-Object {
            $_ -match "^Verified using $([regex]::Escape($scheme)) scheme .*:\s*true$"
        }).Count -gt 0
    })

    return [pscustomobject]@{
        SignerCount = $signerCount
        CertificateSha256 = $certificateSha256
        HasV2OrNewer = $v2OrNewer.Count -gt 0
    }
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$defaultConfigPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'onestore-apk-release.config.json'))
$defaultReleaseRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'onestore-release'))

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = $defaultConfigPath
} else {
    $ConfigPath = [System.IO.Path]::GetFullPath($ConfigPath)
}
if ([string]::IsNullOrWhiteSpace($ReleaseRoot)) {
    $ReleaseRoot = $defaultReleaseRoot
}
$releaseRoot = [System.IO.Path]::GetFullPath($ReleaseRoot)

if (-not $ValidateOnly) {
    if ($ConfigPath -ne $defaultConfigPath) {
        Fail 'A custom ConfigPath is allowed only with -ValidateOnly.'
    }
    if ($releaseRoot -ne $defaultReleaseRoot) {
        Fail 'A custom ReleaseRoot is allowed only with -ValidateOnly.'
    }
    if ($PSBoundParameters.ContainsKey('MinimumVersionCodeExclusive')) {
        Fail 'MinimumVersionCodeExclusive can be overridden only with -ValidateOnly.'
    }
}

$incomingRoot = Join-Path $releaseRoot 'incoming'
$binaryRoot = Join-Path $releaseRoot 'binary'
$evidencePath = Join-Path $releaseRoot 'evidence\artifact_validation.md'
$metadataPath = Join-Path $binaryRoot 'current-apk.json'

if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    Fail "Config file not found: $ConfigPath"
}
$config = Get-Content -Raw -Encoding UTF8 -LiteralPath $ConfigPath | ConvertFrom-Json
if ($config.schemaVersion -ne 1 -or $config.binaryType -ne 'APK') {
    Fail 'Config schemaVersion must be 1 and binaryType must be APK.'
}
if ($config.artifactBaseName -notmatch '^[A-Za-z0-9._-]+$') {
    Fail 'Config artifactBaseName must contain only letters, digits, dot, underscore, or hyphen.'
}

try {
    $configMinimumVersionCodeExclusive = [long]$config.minimumVersionCodeExclusive
    $configMinimumMinSdk = [int]$config.minimumMinSdk
    $configMinimumTargetSdk = [int]$config.minimumTargetSdk
    $configMaximumBytesExclusive = [long]$config.maximumBytesExclusive
    $configRequiredSignerCount = [int]$config.requiredSignerCount
    $allowedCertificates = @($config.allowedCertificateSha256 | ForEach-Object { Get-NormalizedSha256 $_ })
} catch {
    Fail "Config policy values are invalid: $($_.Exception.Message)"
}

if ($allowedCertificates.Count -eq 0) {
    Fail 'Config must contain at least one allowed signing certificate.'
}

if (-not $ValidateOnly) {
    $requiredProductionCertificate = 'dd259fbd1bdba0bef81a24cf0a5d176e1ee12f00238441cedf0aa1a0a70adf77'
    if ($config.applicationId -ne 'com.ssafy.ssabangpalbang' -or
        $configMinimumVersionCodeExclusive -ne 18 -or
        $configMinimumMinSdk -ne 24 -or
        $configMinimumTargetSdk -ne 36 -or
        $configMaximumBytesExclusive -ne 2147483648 -or
        $configRequiredSignerCount -ne 1 -or
        -not [bool]$config.requireV2OrNewerSignature -or
        -not [bool]$config.requireNonDebug -or
        $allowedCertificates.Count -ne 1 -or
        $allowedCertificates[0] -ne $requiredProductionCertificate) {
        Fail 'The production policy config differs from the approved ONE store release policy.'
    }
}

if (-not $PSBoundParameters.ContainsKey('MinimumVersionCodeExclusive')) {
    $MinimumVersionCodeExclusive = $configMinimumVersionCodeExclusive
}

$lockStream = $null
$lockPath = $null
$snapshotPath = $null

try {
    if (-not $ValidateOnly) {
        New-Item -ItemType Directory -Force -Path $binaryRoot | Out-Null
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $evidencePath) | Out-Null
        $lockPath = Join-Path $binaryRoot '.prepare.lock'
        try {
            $lockStream = [System.IO.File]::Open(
                $lockPath,
                [System.IO.FileMode]::OpenOrCreate,
                [System.IO.FileAccess]::ReadWrite,
                [System.IO.FileShare]::None
            )
        } catch {
            Fail 'Another APK preparation process is already running.'
        }
    }

    if ([string]::IsNullOrWhiteSpace($ArtifactPath)) {
        if (-not (Test-Path -LiteralPath $incomingRoot -PathType Container)) {
            Fail "Incoming directory not found: $incomingRoot"
        }
        $incomingApks = @(Get-ChildItem -LiteralPath $incomingRoot -File -Filter '*.apk' -Force)
        if ($incomingApks.Count -ne 1) {
            Fail "Expected exactly one APK in onestore-release\incoming, found $($incomingApks.Count)."
        }
        $ArtifactPath = $incomingApks[0].FullName
    }

    $sourcePath = (Resolve-Path -LiteralPath $ArtifactPath).Path
    $sourceArtifact = Get-Item -LiteralPath $sourcePath
    if ($sourceArtifact.PSIsContainer -or $sourceArtifact.Extension -ine '.apk') {
        Fail 'Artifact must be a regular .apk file.'
    }
    if (($sourceArtifact.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Fail 'Symbolic links and reparse points are not accepted as release artifacts.'
    }
    if ($sourceArtifact.Length -le 0 -or $sourceArtifact.Length -ge $configMaximumBytesExclusive) {
        Fail "APK size must be greater than 0 and less than $configMaximumBytesExclusive bytes; actual=$($sourceArtifact.Length)."
    }

    $sourceFileName = $sourceArtifact.Name
    $sourceSize = $sourceArtifact.Length
    $sourceHashBeforeCopy = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourcePath).Hash.ToUpperInvariant()
    $snapshotRoot = if ($ValidateOnly) { [System.IO.Path]::GetTempPath() } else { $binaryRoot }
    $snapshotPath = Join-Path $snapshotRoot ("onestore-candidate-{0}.apk" -f [guid]::NewGuid().ToString('N'))
    Copy-Item -LiteralPath $sourcePath -Destination $snapshotPath
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $snapshotPath).Hash.ToUpperInvariant()
    if ($sourceHash -ne $sourceHashBeforeCopy) {
        Fail 'Source APK changed while the immutable validation snapshot was being created.'
    }

    $snapshotArtifact = Get-Item -LiteralPath $snapshotPath
    if ($snapshotArtifact.Length -ne $sourceSize) {
        Fail 'Validation snapshot size does not match the source APK.'
    }

    $tools = Get-AndroidBuildTools
    $badging = Invoke-CheckedTool -FilePath $tools.Aapt -Arguments @('dump', 'badging', $snapshotPath) -Label 'aapt dump badging'
    $identity = Get-ApkIdentity -Badging $badging -RequireNonDebug ([bool]$config.requireNonDebug)
    $signatureOutput = Invoke-CheckedTool -FilePath $tools.ApkSigner -Arguments @('verify', '--verbose', '--print-certs', $snapshotPath) -Label 'apksigner verify'
    $signature = Get-ApkSignature -VerificationOutput $signatureOutput

    if ($identity.PackageName -ne $config.applicationId) {
        Fail "Package mismatch: expected=$($config.applicationId), actual=$($identity.PackageName)."
    }
    if ($identity.VersionCode -le $MinimumVersionCodeExclusive) {
        Fail "versionCode must be greater than $MinimumVersionCodeExclusive; actual=$($identity.VersionCode)."
    }
    if ($identity.MinSdk -lt $configMinimumMinSdk) {
        Fail "minSdk must be at least $configMinimumMinSdk; actual=$($identity.MinSdk)."
    }
    if ($identity.TargetSdk -lt $configMinimumTargetSdk) {
        Fail "targetSdk must be at least $configMinimumTargetSdk; actual=$($identity.TargetSdk)."
    }
    if ($signature.SignerCount -ne $configRequiredSignerCount) {
        Fail "Signer count mismatch: expected=$configRequiredSignerCount, actual=$($signature.SignerCount)."
    }
    if ([bool]$config.requireV2OrNewerSignature -and -not $signature.HasV2OrNewer) {
        Fail 'APK must be signed with APK Signature Scheme v2 or newer.'
    }
    if ($signature.CertificateSha256 -notin $allowedCertificates) {
        Fail "Signing certificate mismatch: actual=$($signature.CertificateSha256)."
    }

    $safeVersionName = ($identity.VersionName -replace '[^0-9A-Za-z._-]', '_').Trim('_')
    if ([string]::IsNullOrWhiteSpace($safeVersionName) -or $safeVersionName.Length -gt 80) {
        Fail 'versionName cannot be converted to a safe artifact filename of at most 80 characters.'
    }
    $canonicalName = "$($config.artifactBaseName)-v$safeVersionName-$($identity.VersionCode).apk"
    $destinationPath = Join-Path $binaryRoot $canonicalName

    $result = [ordered]@{
        status = if ($ValidateOnly) { 'VALIDATED_ONLY' } else { 'READY_FOR_STORE_UPLOAD' }
        generatedAt = (Get-Date).ToString('o')
        sourceFileName = $sourceFileName
        preparedFileName = $canonicalName
        packageName = $identity.PackageName
        versionName = $identity.VersionName
        versionCode = $identity.VersionCode
        minSdk = $identity.MinSdk
        targetSdk = $identity.TargetSdk
        sizeBytes = $sourceSize
        sha256 = $sourceHash
        certificateSha256 = $signature.CertificateSha256.ToUpperInvariant()
        signerCount = $signature.SignerCount
        v2OrNewerSignature = $signature.HasV2OrNewer
        buildToolsVersion = $tools.Version
        storeExistingVersionCodeChecked = $false
        storeAppSigningPolicyChecked = $false
    }

    if ($ValidateOnly) {
        $result | ConvertTo-Json -Depth 3
        Write-Host '[ONE store APK] Validation passed; no release files were changed.'
        return
    }

    if (Test-Path -LiteralPath $destinationPath -PathType Container) {
        Fail "Prepared artifact destination is a directory: $destinationPath"
    }

    $destinationMatches = $false
    if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
        $destinationHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destinationPath).Hash.ToUpperInvariant()
        if ($destinationHash -ne $sourceHash -and -not $ReplacePreparedArtifact) {
            Fail "Prepared artifact collision for $canonicalName. Re-run with -ReplacePreparedArtifact only after explicit approval."
        }
        $destinationMatches = $destinationHash -eq $sourceHash
    }

    if ($destinationMatches -and
        (Test-Path -LiteralPath $metadataPath -PathType Leaf) -and
        (Test-Path -LiteralPath $evidencePath -PathType Leaf)) {
        try {
            $existingMetadata = Get-Content -Raw -Encoding UTF8 -LiteralPath $metadataPath | ConvertFrom-Json
            $existingEvidence = Get-Content -Raw -Encoding UTF8 -LiteralPath $evidencePath
            if ($existingMetadata.status -eq 'READY_FOR_STORE_UPLOAD' -and
                $existingMetadata.sha256 -eq $sourceHash -and
                $existingMetadata.preparedFileName -eq $canonicalName -and
                $existingEvidence.Contains('READY_FOR_STORE_UPLOAD') -and
                $existingEvidence.Contains($canonicalName) -and
                $existingEvidence.Contains($sourceHash)) {
                $existingMetadata | ConvertTo-Json -Depth 3
                Write-Host "[ONE store APK] Already prepared with the same SHA-256: $destinationPath"
                return
            }
        } catch {
            Write-Warning 'Existing metadata or evidence is unreadable; it will be regenerated.'
        }
    }

    $metadata = $result | ConvertTo-Json -Depth 3
    $evidence = @"
# Final APK artifact validation

- Status: **READY_FOR_STORE_UPLOAD**
- Generated at: $($result.generatedAt)
- Prepared file: $($result.preparedFileName) (gitignored)
- Package: $($result.packageName)
- Version: $($result.versionName) / versionCode $($result.versionCode)
- SDK: minSdk $($result.minSdk), targetSdk $($result.targetSdk)
- Size: $($result.sizeBytes) bytes
- SHA-256: $($result.sha256)
- Signing certificate SHA-256: $($result.certificateSha256)
- Signers: $($result.signerCount)
- APK Signature Scheme v2 or newer: $($result.v2OrNewerSignature)
- Android build-tools: $($result.buildToolsVersion)

## Human gates before upload

- [ ] Confirm that the ONE store product binary type is APK.
- [ ] Confirm that the current maximum store versionCode is lower than $($result.versionCode).
- [ ] Match the ONE store App Signing policy to the certificate above.
- [ ] Record the EAS build ID and confirm its Git commit matches the tested release commit.
- [ ] Approve listing content, age rating, data safety, images, and public contact details.
- [ ] Recheck the primary review account login immediately before submission.
- [ ] Confirm the package, version, signature, and supported-device checks after upload.
- [ ] The account owner must complete MFA, enter review passwords, and submit for review.
"@

    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    $commitItems = @()
    if (-not $destinationMatches) {
        $commitItems += [pscustomobject]@{
            Name = 'APK'
            Target = $destinationPath
            Stage = "$destinationPath.partial"
            Backup = "$destinationPath.backup"
            Content = $null
            IsBinary = $true
            HadOriginal = $false
            Installed = $false
        }
    }
    $commitItems += [pscustomobject]@{
        Name = 'metadata'
        Target = $metadataPath
        Stage = "$metadataPath.partial"
        Backup = "$metadataPath.backup"
        Content = $metadata + [Environment]::NewLine
        IsBinary = $false
        HadOriginal = $false
        Installed = $false
    }
    $commitItems += [pscustomobject]@{
        Name = 'evidence'
        Target = $evidencePath
        Stage = "$evidencePath.partial"
        Backup = "$evidencePath.backup"
        Content = $evidence.TrimStart() + [Environment]::NewLine
        IsBinary = $false
        HadOriginal = $false
        Installed = $false
    }

    $commitSucceeded = $false
    try {
        foreach ($item in $commitItems) {
            if (Test-Path -LiteralPath $item.Target -PathType Container) {
                Fail "$($item.Name) target is a directory: $($item.Target)"
            }
            if (Test-Path -LiteralPath $item.Backup -PathType Leaf) {
                if (-not (Test-Path -LiteralPath $item.Target)) {
                    Move-Item -LiteralPath $item.Backup -Destination $item.Target
                    Fail "Recovered $($item.Name) from an interrupted prior run; rerun preparation."
                }
                Fail "Stale backup exists for $($item.Name): $($item.Backup)"
            }
            if (Test-Path -LiteralPath $item.Stage) {
                Remove-Item -LiteralPath $item.Stage -Force
            }
            if ($item.IsBinary) {
                Copy-Item -LiteralPath $snapshotPath -Destination $item.Stage
                $stagedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $item.Stage).Hash.ToUpperInvariant()
                if ($stagedHash -ne $sourceHash) {
                    Fail 'Staged APK SHA-256 does not match the validated snapshot.'
                }
            } else {
                [System.IO.File]::WriteAllText($item.Stage, $item.Content, $utf8WithoutBom)
            }
        }

        foreach ($item in $commitItems) {
            if (Test-Path -LiteralPath $item.Target -PathType Leaf) {
                Move-Item -LiteralPath $item.Target -Destination $item.Backup
                $item.HadOriginal = $true
            }
            Move-Item -LiteralPath $item.Stage -Destination $item.Target
            $item.Installed = $true
        }
        $commitSucceeded = $true
    } catch {
        for ($index = $commitItems.Count - 1; $index -ge 0; $index--) {
            $item = $commitItems[$index]
            if ($item.Installed -and (Test-Path -LiteralPath $item.Target -PathType Leaf)) {
                Remove-Item -LiteralPath $item.Target -Force
            }
            if ($item.HadOriginal -and (Test-Path -LiteralPath $item.Backup -PathType Leaf)) {
                Move-Item -LiteralPath $item.Backup -Destination $item.Target
            }
        }
        throw
    } finally {
        foreach ($item in $commitItems) {
            if (Test-Path -LiteralPath $item.Stage -PathType Leaf) {
                Remove-Item -LiteralPath $item.Stage -Force
            }
            if ($commitSucceeded -and (Test-Path -LiteralPath $item.Backup -PathType Leaf)) {
                Remove-Item -LiteralPath $item.Backup -Force
            }
        }
    }

    $result | ConvertTo-Json -Depth 3
    Write-Host "[ONE store APK] READY_FOR_STORE_UPLOAD: $destinationPath"
} finally {
    if ($snapshotPath -and (Test-Path -LiteralPath $snapshotPath -PathType Leaf)) {
        Remove-Item -LiteralPath $snapshotPath -Force
    }
    if ($lockStream) {
        $lockStream.Dispose()
    }
    if ($lockPath -and (Test-Path -LiteralPath $lockPath -PathType Leaf)) {
        Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
    }
}
