param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$QLoaderDirectory,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
$model = (& $AdbPath -s $Serial shell getprop ro.product.model 2>$null) -join ''
if ($LASTEXITCODE -ne 0 -or $model -notmatch 'Quest') { throw 'The selected device is not a connected Quest. No settings transferred.' }
$overrideLines = Get-Content -LiteralPath (Join-Path $QLoaderDirectory 'overrides.conf')
$configAddress = $null
foreach ($line in $overrideLines) { if ($line -match '^\s*ConfigUpdateUrl\s*=\s*(.+)\s*$') { $configAddress = $Matches[1].Trim().Trim('"'); break } }
if (!$configAddress) { throw 'ConfigUpdateUrl was not found in QLoader overrides.conf.' }
$uri = [Uri]$configAddress
if ($uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Fragment) { throw 'Expected an HTTPS configuration URL without userinfo or fragment.' }
$bootstrap = @{configUrl=$configAddress} | ConvertTo-Json -Compress
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = $AdbPath
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
foreach ($arg in @('-s',$Serial,'shell',"run-as ru.local.qcatalog sh -c 'umask 077; mkdir -p files && cat > files/bootstrap.json'")) { $info.ArgumentList.Add($arg) }
$process = [Diagnostics.Process]::new(); $process.StartInfo = $info
try {
    if (!$process.Start()) { throw 'Could not start the settings transfer.' }
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($bootstrap); $process.StandardInput.Close()
    if (!$process.WaitForExit(20000)) { $process.Kill(); throw 'Settings transfer timed out.' }
    if ($process.ExitCode -ne 0) { throw 'Settings transfer failed. Install the QCatalog preview APK first.' }
} finally { $process.Dispose(); $bootstrap=$null; $configAddress=$null; $overrideLines=$null }
& $AdbPath -s $Serial shell am force-stop ru.local.qcatalog | Out-Null
& $AdbPath -s $Serial shell am start -n ru.local.qcatalog/.MainActivity | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Open QCatalog from Unknown Sources to finish connecting.' }
'Settings transferred privately. QCatalog will encrypt the URL and remove the temporary import on startup.'
