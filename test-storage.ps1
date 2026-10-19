param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$SdkPath
)
$ErrorActionPreference = 'Stop'
$qaDir = Join-Path $PSScriptRoot 'build\storage-checks'
$toolsDir = Join-Path $SdkPath 'build-tools\36.1.0'
$androidJar = Join-Path $SdkPath 'platforms\android-36.1\android.jar'
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
$env:JAVA_HOME = $JavaHome
function Assert-Exit([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed (exit $LASTEXITCODE)" } }
foreach ($dir in @($qaDir,"$qaDir\classes","$qaDir\dex","$qaDir\tmp")) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
& "$toolsDir\aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot\tests\storage\AndroidManifest.xml" -o "$qaDir\unsigned.apk"
Assert-Exit 'Storage QA manifest'
& "$JavaHome\bin\javac.exe" --release 8 -encoding UTF-8 -cp $androidJar -d "$qaDir\classes" "$PSScriptRoot\src\ru\local\qcatalog\PrivateStore.java" "$PSScriptRoot\tests\storage\StorageChecks.java"
Assert-Exit 'Storage QA compilation'
& "$JavaHome\bin\jar.exe" "-J-Djava.io.tmpdir=$qaDir\tmp" --create --file "$qaDir\classes.jar" -C "$qaDir\classes" .
Assert-Exit 'Storage QA archive'
& "$toolsDir\d8.bat" --lib $androidJar --min-api 29 --output "$qaDir\dex" "$qaDir\classes.jar"
Assert-Exit 'Storage QA DEX'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$qaDir\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$qaDir\dex\classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::Optimal) | Out-Null } finally { $zip.Dispose() }
& "$toolsDir\zipalign.exe" -f 4 "$qaDir\unsigned.apk" "$qaDir\aligned.apk"
Assert-Exit 'Storage QA alignment'
& "$toolsDir\apksigner.bat" sign --ks "$PSScriptRoot\build\debug.keystore" --ks-key-alias qcatalog --ks-pass pass:android --key-pass pass:android --out "$qaDir\checks.apk" "$qaDir\aligned.apk"
Assert-Exit 'Storage QA signing'
& $adb -s $Serial install -r "$qaDir\checks.apk"
Assert-Exit 'Storage QA install'
try {
    $result = (& $adb -s $Serial shell am instrument -w ru.local.qcatalog.storagechecks/ru.local.qcatalog.StorageChecks) -join "`n"
    $result
    if ($result -notmatch 'result=PASS ') { throw 'Storage checks failed.' }
} finally { & $adb -s $Serial uninstall ru.local.qcatalog.storagechecks | Out-Null }
