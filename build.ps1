param(
    [Parameter(Mandatory=$true)][string]$EnginePath,
    [string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildToolsVersion = '36.1.0',
    [string]$Platform = 'android-36.1'
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Pass -JavaHome with a JDK 21 installation.' }
$engineFile = (Resolve-Path -LiteralPath $EnginePath).Path
$buildDir = Join-Path $PSScriptRoot 'build'
$toolsDir = Join-Path $SdkPath ('build-tools\' + $BuildToolsVersion)
$androidJar = Join-Path $SdkPath ('platforms\' + $Platform + '\android.jar')
$env:JAVA_HOME = $JavaHome
foreach ($folder in @($buildDir, "$buildDir\classes", "$buildDir\dex", "$buildDir\generated", "$buildDir\tests", "$buildDir\tmp")) { New-Item -ItemType Directory -Path $folder -Force | Out-Null }
# Clear only the known compilation output folders, so removed/renamed Java classes cannot leak into a new DEX.
foreach ($name in @('classes','generated','dex','tests')) {
    $target=[IO.Path]::GetFullPath((Join-Path $buildDir $name))
    $boundary=[IO.Path]::GetFullPath($buildDir)+[IO.Path]::DirectorySeparatorChar
    if (!$target.StartsWith($boundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid compilation output path.' }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
}
function Assert-Exit([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed (exit $LASTEXITCODE)" } }
& "$toolsDir\aapt2.exe" compile --dir "$PSScriptRoot\res" -o "$buildDir\resources.zip"
Assert-Exit 'Resource compilation'
& "$toolsDir\aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot\AndroidManifest.xml" --java "$buildDir\generated" -o "$buildDir\unsigned.apk" "$buildDir\resources.zip"
Assert-Exit 'Resource linking'
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath "$buildDir\generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" -encoding UTF-8 --release 8 -cp $androidJar -d "$buildDir\classes" @sources
Assert-Exit 'Java compilation'
& "$JavaHome\bin\jar.exe" "-J-Djava.io.tmpdir=$buildDir\tmp" --create --file "$buildDir\classes.jar" -C "$buildDir\classes" .
Assert-Exit 'Class archive'
& "$toolsDir\d8.bat" --lib $androidJar --min-api 29 --output "$buildDir\dex" "$buildDir\classes.jar"
Assert-Exit 'DEX compilation'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$buildDir\unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$buildDir\dex\classes.dex", 'classes.dex', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $engineFile, 'lib/arm64-v8a/librclone.so', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$PSScriptRoot\THIRD_PARTY_NOTICES.txt", 'assets/THIRD_PARTY_NOTICES.txt', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $zip.Dispose() }
& "$toolsDir\zipalign.exe" -P 16 -f 4 "$buildDir\unsigned.apk" "$buildDir\aligned.apk"
Assert-Exit 'APK alignment'
$key = "$buildDir\debug.keystore"
if (!(Test-Path -LiteralPath $key)) {
    & "$JavaHome\bin\keytool.exe" -genkeypair -keystore $key -storepass android -keypass android -alias qcatalog -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=QCatalog Local Development' 2>&1 | ForEach-Object { $_.ToString() }
    Assert-Exit 'Local development key'
}
$apk = "$buildDir\QCatalog-0.4.0-preview.apk"
& "$toolsDir\apksigner.bat" sign --ks $key --ks-key-alias qcatalog --ks-pass pass:android --key-pass pass:android --out $apk "$buildDir\aligned.apk"
Assert-Exit 'APK signing'
& "$toolsDir\apksigner.bat" verify --verbose $apk
Assert-Exit 'APK signature verification'
& "$JavaHome\bin\javac.exe" -encoding UTF-8 --release 8 -d "$buildDir\tests" "$PSScriptRoot\src\ru\local\qcatalog\Catalog.java" "$PSScriptRoot\src\ru\local\qcatalog\DownloadFiles.java" "$PSScriptRoot\src\ru\local\qcatalog\DescriptionText.java" "$PSScriptRoot\src\ru\local\qcatalog\InstallChecks.java" "$PSScriptRoot\src\ru\local\qcatalog\ResumeFile.java" "$PSScriptRoot\tests\CatalogTest.java" "$PSScriptRoot\tests\DownloadFilesTest.java" "$PSScriptRoot\tests\DescriptionTextTest.java" "$PSScriptRoot\tests\InstallChecksTest.java" "$PSScriptRoot\tests\ResumeFileTest.java" "$PSScriptRoot\src\ru\local\qcatalog\DataFiles.java" "$PSScriptRoot\tests\DataFilesTest.java"
Assert-Exit 'Parser test compilation'
& "$JavaHome\bin\java.exe" -cp "$buildDir\tests" ru.local.qcatalog.CatalogTest
Assert-Exit 'Parser tests'
& "$JavaHome\bin\java.exe" -cp "$buildDir\tests" ru.local.qcatalog.DownloadFilesTest "$buildDir\test-data"
Assert-Exit 'Download cleanup tests'
& "$JavaHome\bin\java.exe" -cp "$buildDir\tests" ru.local.qcatalog.DescriptionTextTest
Assert-Exit 'Description formatting tests'
& "$JavaHome\bin\java.exe" -cp "$buildDir\tests" ru.local.qcatalog.InstallChecksTest
Assert-Exit 'Installation conflict tests'
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$buildDir\tmp" -cp "$buildDir\tests" ru.local.qcatalog.ResumeFileTest
Assert-Exit 'Durable resume tests'
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$buildDir\tmp" -cp "$buildDir\tests" ru.local.qcatalog.DataFilesTest
Assert-Exit 'Data copy and verification tests'
[pscustomobject]@{Apk=$apk;Bytes=(Get-Item -LiteralPath $apk).Length;SHA256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash} | ConvertTo-Json -Compress
