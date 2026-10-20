param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [string]$SpecialReleaseId,
    [switch]$LiveMedia
)
$ErrorActionPreference = 'Stop'
$buildDir = Join-Path $PSScriptRoot 'build'
$qaDir = Join-Path $buildDir 'quest-checks'
$toolsDir = Join-Path $SdkPath 'build-tools\36.1.0'
$androidJar = Join-Path $SdkPath 'platforms\android-36.1\android.jar'
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
$env:JAVA_HOME = $JavaHome
function Assert-Exit([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed (exit $LASTEXITCODE)" } }
foreach ($dir in @($qaDir,"$qaDir\classes","$qaDir\dex")) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
& "$toolsDir\aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot\tests\android\AndroidManifest.xml" -o "$qaDir\unsigned.apk"
Assert-Exit 'QA manifest'
& "$JavaHome\bin\javac.exe" --release 8 -encoding UTF-8 -cp "$androidJar;$buildDir\classes.jar" -d "$qaDir\classes" "$PSScriptRoot\tests\android\QuestChecks.java" "$PSScriptRoot\tests\MediaChecks.java"
Assert-Exit 'QA compilation'
& "$JavaHome\bin\jar.exe" "-J-Djava.io.tmpdir=$buildDir\tmp" --create --file "$qaDir\classes.jar" -C "$qaDir\classes" .
Assert-Exit 'QA archive'
& "$toolsDir\d8.bat" --lib $androidJar --min-api 29 --output "$qaDir\dex" "$qaDir\classes.jar"
Assert-Exit 'QA DEX'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$qaDir\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$qaDir\dex\classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::Optimal) | Out-Null } finally { $zip.Dispose() }
& "$toolsDir\zipalign.exe" -f 4 "$qaDir\unsigned.apk" "$qaDir\aligned.apk"
Assert-Exit 'QA alignment'
& "$toolsDir\apksigner.bat" sign --ks "$buildDir\debug.keystore" --ks-key-alias qcatalog --ks-pass pass:android --key-pass pass:android --out "$qaDir\checks.apk" "$qaDir\aligned.apk"
Assert-Exit 'QA signing'
& $adb -s $Serial install -r "$qaDir\checks.apk"
Assert-Exit 'QA install'
try {
    $extra=@();if($SpecialReleaseId){if($SpecialReleaseId -notmatch '^[a-f0-9]{64}$'){throw 'Invalid release identifier'};$extra=@('-e','specialReleaseId',$SpecialReleaseId)}
    if($LiveMedia){$extra+=@('-e','liveMedia','true')}
    $result = (& $adb -s $Serial shell am instrument -w @extra ru.local.qcatalog.tests/ru.local.qcatalog.QuestChecks) -join "`n"
    $result
    if ($result -notmatch 'result=PASS ') { throw 'On-device checks failed.' }
} finally { & $adb -s $Serial uninstall ru.local.qcatalog.tests | Out-Null }
