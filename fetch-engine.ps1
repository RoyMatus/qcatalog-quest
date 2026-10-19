param(
    [string]$ArchivePath
)
$ErrorActionPreference = 'Stop'
$nativeDir = Join-Path $PSScriptRoot 'native'
$engineFile = Join-Path $nativeDir 'linux-arm64\rclone'
$engineHash = 'E63F1CA56C27794A5AB7C0211E26BFFDACE8BCAA5A07EDC190D4FB38BA09816B'
$archiveHash = '7357E1DE1A8CE17159E50D850F79028901A4A19AC75AC7BBC7A15A2DBBE9F731'
if (Test-Path -LiteralPath $engineFile) {
    if ((Get-FileHash -LiteralPath $engineFile -Algorithm SHA256).Hash -eq $engineHash) {
        Get-Item -LiteralPath $engineFile | Select-Object FullName,Length
        return
    }
    throw 'The existing native engine has a different hash. Inspect it before replacing it.'
}
New-Item -ItemType Directory -Path $nativeDir -Force | Out-Null
if ([string]::IsNullOrWhiteSpace($ArchivePath)) {
    $ArchivePath = Join-Path $nativeDir 'linux-arm64.tar'
    if (!(Test-Path -LiteralPath $ArchivePath)) {
        Write-Output 'Downloading the provider archive (about 322 MiB). No installer is executed.'
        $partial = Join-Path $nativeDir 'linux-arm64.tar.part'
        Invoke-WebRequest -Uri 'https://cloud.dipvr.ru/onlineinstall/files/linux-arm64.tar' -OutFile $partial -MaximumRedirection 0
        if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $archiveHash) {
            throw 'Provider archive changed. The partial download was retained for inspection; no code was extracted or executed.'
        }
        Move-Item -LiteralPath $partial -Destination $ArchivePath
    }
}
$archiveFile = (Resolve-Path -LiteralPath $ArchivePath).Path
if ((Get-FileHash -LiteralPath $archiveFile -Algorithm SHA256).Hash -ne $archiveHash) {
    throw 'Archive hash does not match the verified provider build. No files were extracted.'
}
& tar.exe -xf $archiveFile -C $nativeDir 'linux-arm64/rclone'
if ($LASTEXITCODE -ne 0) { throw 'Could not extract the native engine.' }
if ((Get-FileHash -LiteralPath $engineFile -Algorithm SHA256).Hash -ne $engineHash) {
    throw 'Extracted engine hash does not match. Do not build with this file.'
}
Get-Item -LiteralPath $engineFile | Select-Object FullName,Length
Write-Output 'Verified provider engine. See THIRD_PARTY_NOTICES.txt for its provenance.'
