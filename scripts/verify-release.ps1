param(
    [string]$GradleCommand,
    [string]$PythonCommand,
    [switch]$Offline,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        if (-not $PythonCommand) {
            $PythonCommand = (Get-Command python -ErrorAction SilentlyContinue).Source
        }
        if (-not $PythonCommand) { throw 'Pass -PythonCommand with a Python 3 interpreter.' }
        & $PythonCommand 'app/src/test/python/test_downloader_update.py'
        if ($LASTEXITCODE -ne 0) { throw 'Python regression tests failed.' }

        if (-not $GradleCommand) {
            $installedGradle = Get-Command gradle -ErrorAction SilentlyContinue
            $GradleCommand = if ($installedGradle) { $installedGradle.Source } else { Join-Path $projectRoot 'gradlew.bat' }
        }
        $buildArguments = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleRelease', '--no-daemon', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process')
        if ($Offline) { $buildArguments += '--offline' }
        & $GradleCommand @buildArguments
        if ($LASTEXITCODE -ne 0) { throw 'Android verification or release build failed.' }
    }

    $releaseDirectory = Join-Path $projectRoot 'app/build/outputs/apk/release'
    $metadata = Get-Content (Join-Path $releaseDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne 'com.k4music.app') { throw 'Unexpected release application ID.' }
    $entry = $metadata.elements | Where-Object { $_.filters.Count -eq 0 } | Select-Object -First 1
    if (-not $entry) { throw 'Universal release APK is missing.' }
    $apk = Join-Path $releaseDirectory $entry.outputFile
    $sdkLine = if (Test-Path 'local.properties') { Get-Content 'local.properties' | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1 }
    $sdkDirectory = if ($sdkLine) { ($sdkLine -replace '^sdk.dir=', '').Replace('\:', ':').Replace('\\', '\') } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdkDirectory) { $sdkDirectory = $env:ANDROID_HOME }
    if (-not $sdkDirectory) { throw 'Configure sdk.dir, ANDROID_SDK_ROOT or ANDROID_HOME.' }
    $buildTools = Get-ChildItem (Join-Path $sdkDirectory 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android build-tools are missing.' }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($abi in @('arm64-v8a', 'x86_64')) {
            foreach ($library in @('libqjs.so', 'libpython3.14.so')) {
                $packed = $archive.GetEntry("lib/$abi/$library")
                if (-not $packed -or $packed.Length -eq 0) { throw "Missing native runtime: $abi/$library" }
            }
        }
        if (-not $archive.GetEntry('assets/chaquopy/app.imy')) { throw 'Python application source archive is missing.' }
    } finally { $archive.Dispose() }

    $version = $entry.versionName -replace '-RELEASE$', ''
    $deliveryPath = Join-Path $projectRoot "K4Music-v$version-RELEASE.apk"
    Copy-Item -LiteralPath $apk -Destination $deliveryPath -Force
    $hash = (Get-FileHash -LiteralPath $deliveryPath -Algorithm SHA256).Hash
    "$hash  $([System.IO.Path]::GetFileName($deliveryPath))" | Set-Content "$deliveryPath.sha256"
    Write-Output "Verified release: $deliveryPath"
    Write-Output "Version code: $($entry.versionCode); SHA256: $hash"
} finally { Pop-Location }
