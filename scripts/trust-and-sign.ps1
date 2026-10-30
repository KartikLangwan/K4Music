<#
.SYNOPSIS
    Signs local development builds with a personal test certificate.

.DESCRIPTION
    This changes CurrentUser certificate stores when explicitly run. -InstallRoot also
    adds the certificate to the user's Root store. Do not run this on end-user machines
    as a substitute for production publisher signing.

    A self-signed development certificate does not establish public SmartScreen reputation
    and does not guarantee that SmartScreen, Smart App Control or Defender will allow a file.
    Production distribution needs a managed publisher-signing process and independent checks.
#>

param(
    [string]$TargetFile = "",
    [switch]$InstallRoot = $false
)

$ErrorActionPreference = "Stop"

$CertSubject = "CN=K4 Audio Development CA"
Write-Host "==> Checking for K4 Audio Code Signing Certificate..." -ForegroundColor Cyan

$cert = Get-ChildItem -Path Cert:\CurrentUser\My | Where-Object { $_.Subject -eq $CertSubject } | Select-Object -First 1

if (-not $cert) {
    Write-Host "==> Generating new local Code Signing Certificate ($CertSubject)..." -ForegroundColor Yellow
    $cert = New-SelfSignedCertificate -Subject $CertSubject `
        -Type CodeSigning `
        -CertStoreLocation Cert:\CurrentUser\My `
        -NotAfter (Get-Date).AddYears(5) `
        -KeyExportPolicy Exportable `
        -KeyLength 2048 `
        -HashAlgorithm SHA256

    Write-Host "==> Created new certificate: $($cert.Thumbprint)" -ForegroundColor Green
} else {
    Write-Host "==> Found existing certificate: $($cert.Thumbprint)" -ForegroundColor Green
}

try {
    if ($InstallRoot) {
        $rootStore = New-Object System.Security.Cryptography.X509Certificates.X509Store "Root", "CurrentUser"
        $rootStore.Open("ReadWrite")
        if (-not ($rootStore.Certificates | Where-Object { $_.Thumbprint -eq $cert.Thumbprint })) {
            $rootStore.Add($cert)
        }
        $rootStore.Close()
        Write-Host "==> Certificate verified in CurrentUser Root store." -ForegroundColor Green
    }

    $pubStore = New-Object System.Security.Cryptography.X509Certificates.X509Store "TrustedPublisher", "CurrentUser"
    $pubStore.Open("ReadWrite")
    if (-not ($pubStore.Certificates | Where-Object { $_.Thumbprint -eq $cert.Thumbprint })) {
        $pubStore.Add($cert)
    }
    $pubStore.Close()

    $peopleStore = New-Object System.Security.Cryptography.X509Certificates.X509Store "TrustedPeople", "CurrentUser"
    $peopleStore.Open("ReadWrite")
    if (-not ($peopleStore.Certificates | Where-Object { $_.Thumbprint -eq $cert.Thumbprint })) {
        $peopleStore.Add($cert)
    }
    $peopleStore.Close()

    Write-Host "==> Certificate verified in TrustedPublisher and TrustedPeople stores." -ForegroundColor Green
} catch {
    Write-Warning "Could not register to certificate stores: $_"
}

# Collect files to sign
$filesToSign = @()

if ($TargetFile -and (Test-Path $TargetFile)) {
    $filesToSign += (Resolve-Path $TargetFile).Path
} else {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $repoRoot = Split-Path -Parent $scriptDir

    $appExe = Join-Path $repoRoot "windows\build\compose\binaries\main\app\K4 Audio\K4 Audio.exe"
    if (Test-Path $appExe) { $filesToSign += $appExe }

    $installerDir = Join-Path $repoRoot "windows\build\installer"
    if (Test-Path $installerDir) {
        $installers = Get-ChildItem -Path $installerDir -Filter "K4Audio-Setup-*.exe"
        foreach ($inst in $installers) { $filesToSign += $inst.FullName }
    }
}

if ($filesToSign.Count -eq 0) {
    Write-Host "==> No built binaries found to sign yet. Build them with:" -ForegroundColor Gray
    Write-Host "    .\gradlew.bat -p windows createDistributable" -ForegroundColor Gray
    Write-Host "    .\gradlew.bat -p windows packageInstaller" -ForegroundColor Gray
    Write-Host "==> Trusted certificate is ready in your store for future builds!" -ForegroundColor Green
    exit 0
}

foreach ($file in $filesToSign) {
    Write-Host "==> Signing $file..." -ForegroundColor Cyan
    # Remove ReadOnly and Mark of the Web if present
    Set-ItemProperty -Path $file -Name IsReadOnly -Value $false -ErrorAction SilentlyContinue
    Unblock-File -Path $file -ErrorAction SilentlyContinue

    $status = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -TimestampServer "http://timestamp.digicert.com" -HashAlgorithm SHA256
    if ($status.Status -ne "Valid") {
        # Retry without timestamp server if network timestamping fails
        $status = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -HashAlgorithm SHA256
    }
    if ($status.Status -eq "Valid") {
        Write-Host "    [OK] Signed successfully!" -ForegroundColor Green
    } else {
        Write-Warning "    [WARN] Signing returned status: $($status.Status) - $($status.StatusMessage)"
    }
}

Write-Host "==> Signing process completed. Binaries are signed with certificate '$CertSubject'." -ForegroundColor Green
