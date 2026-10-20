param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$SdkPath,
    [string]$PreviousApk,
    [string]$IncomingApk,
    [string]$ResumeRemote,
    [long]$ResumeSize,
    [string]$ResumeSha256,
    [string]$ResumePrefixSha256,
    [switch]$Ui,
    [switch]$LivePopularity,
    [switch]$LiveMedia
)
$ErrorActionPreference = 'Stop'
if ([bool]$PreviousApk -xor [bool]$IncomingApk) { throw 'Provide both PreviousApk and IncomingApk, or neither.' }
$qaDir = Join-Path $PSScriptRoot $(if($Ui){'build\ui-checks'}else{'build\storage-checks'})
$toolsDir = Join-Path $SdkPath 'build-tools\36.1.0'
$androidJar = Join-Path $SdkPath 'platforms\android-36.1\android.jar'
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
$env:JAVA_HOME = $JavaHome
& (Join-Path $PSScriptRoot 'fetch-dependencies.ps1')
$dependencyJars=@((Get-Content (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json) | ForEach-Object { Join-Path (Join-Path $PSScriptRoot 'lib') $_.File })
$compileClasspath=(@($androidJar)+$dependencyJars) -join ';'
$testPackage=if($Ui){'ru.local.qcatalog.uichecks'}else{'ru.local.qcatalog.storagechecks'}
$testType=if($Ui){'ui'}else{'storage'}
function Assert-Exit([string]$stage) { if ($LASTEXITCODE -ne 0) { throw "$stage failed (exit $LASTEXITCODE)" } }
foreach ($dir in @($qaDir,"$qaDir\classes","$qaDir\dex","$qaDir\tmp","$qaDir\generated")) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
& "$toolsDir\aapt2.exe" compile --dir "$PSScriptRoot\res" -o "$qaDir\resources.zip"
Assert-Exit 'Storage resources'
& "$toolsDir\aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot\tests\$testType\AndroidManifest.xml" --custom-package ru.local.qcatalog --java "$qaDir\generated" -o "$qaDir\unsigned.apk" "$qaDir\resources.zip"
Assert-Exit 'Storage QA manifest'
$sources=@(Get-ChildItem -LiteralPath "$PSScriptRoot\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+="$PSScriptRoot\tests\SpecialInstallTest.java"
$sources+="$PSScriptRoot\tests\MediaChecks.java"
$sources+=if($Ui){"$PSScriptRoot\tests\ui\UiChecks.java"}else{"$PSScriptRoot\tests\storage\StorageChecks.java"}
$sources+=@(Get-ChildItem -LiteralPath "$qaDir\generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" --release 8 -encoding UTF-8 -cp $compileClasspath -d "$qaDir\classes" @sources
Assert-Exit 'Storage QA compilation'
& "$JavaHome\bin\jar.exe" "-J-Djava.io.tmpdir=$qaDir\tmp" --create --file "$qaDir\classes.jar" -C "$qaDir\classes" .
Assert-Exit 'Storage QA archive'
& "$toolsDir\d8.bat" --lib $androidJar --min-api 29 --output "$qaDir\dex" "$qaDir\classes.jar" @dependencyJars
Assert-Exit 'Storage QA DEX'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$qaDir\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($license in Get-ChildItem (Join-Path $PSScriptRoot 'licenses') -File) { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$license.FullName,('assets/licenses/'+$license.Name),[IO.Compression.CompressionLevel]::Optimal) | Out-Null }
    foreach ($dependency in $dependencyJars) {
        $library=[IO.Compression.ZipFile]::OpenRead($dependency)
        try { foreach ($entry in $library.Entries | Where-Object { $_.FullName -match '^META-INF/(LICENSE|NOTICE|COPYING)' }) {
            $outEntry=$zip.CreateEntry('assets/licenses/'+[IO.Path]::GetFileNameWithoutExtension($dependency)+'/'+$entry.FullName)
            $inputStream=$entry.Open();$outputStream=$outEntry.Open();try { $inputStream.CopyTo($outputStream) }finally { $inputStream.Dispose();$outputStream.Dispose() }
        }}finally{$library.Dispose()}
    }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$qaDir\dex\classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    if ($ResumeRemote) { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$PSScriptRoot\native\linux-arm64\rclone",'lib/arm64-v8a/librclone.so',[IO.Compression.CompressionLevel]::Optimal) | Out-Null }
} finally { $zip.Dispose() }
& "$toolsDir\zipalign.exe" -f 4 "$qaDir\unsigned.apk" "$qaDir\aligned.apk"
Assert-Exit 'Storage QA alignment'
& "$toolsDir\apksigner.bat" sign --ks "$PSScriptRoot\build\debug.keystore" --ks-key-alias qcatalog --ks-pass pass:android --key-pass pass:android --out "$qaDir\checks.apk" "$qaDir\aligned.apk"
Assert-Exit 'Storage QA signing'
& $adb -s $Serial install -r "$qaDir\checks.apk"
Assert-Exit 'Storage QA install'
try {
    if ($PreviousApk) {
        foreach ($fixture in @(@{Path=$PreviousApk;Name='previous.apk'},@{Path=$IncomingApk;Name='incoming.apk'})) {
            $fixtureFile=Get-Item -LiteralPath $fixture.Path
            $remote='/data/local/tmp/qcatalog-storage-' + $fixture.Name
            & $adb -s $Serial push $fixtureFile.FullName $remote
            Assert-Exit 'APK fixture staging'
            try {
                & $adb -s $Serial shell "cat $remote | run-as ru.local.qcatalog.storagechecks sh -c 'mkdir -p files; cat > files/$($fixture.Name)'"
                Assert-Exit 'Private APK fixture transfer'
                $length=((& $adb -s $Serial shell run-as ru.local.qcatalog.storagechecks stat -c %s "files/$($fixture.Name)") -join '').Trim()
                if ($LASTEXITCODE -ne 0 -or [long]$length -ne $fixtureFile.Length) { throw 'APK fixture length differs after transfer.' }
            } finally { & $adb -s $Serial shell rm -f $remote | Out-Null }
        }
    }
    $instrumentArgs=@('-s',$Serial,'shell','am','instrument','-w')
    if($LiveMedia){if($Ui){throw 'UI fixtures intentionally have no network permission.'};$instrumentArgs+=@('-e','liveMedia','true')}
    if($LivePopularity){if($Ui){throw 'UI fixtures intentionally have no network permission.'};$instrumentArgs+=@('-e','livePopularity','true')}
    if ($ResumeRemote) {
        if ($ResumeSize -lt 65536 -or $ResumeSha256 -notmatch '^[a-f0-9]{64}$' -or $ResumePrefixSha256 -notmatch '^[a-f0-9]{64}$') { throw 'Supply verified size, full SHA-256 and first-65536-byte SHA-256 for the resume probe.' }
        & $adb -s $Serial shell "run-as ru.local.qcatalog cat files/FFA_config | run-as ru.local.qcatalog.storagechecks sh -c 'mkdir -p files; cat > files/FFA_config'"
        Assert-Exit 'Private resume probe configuration'
        $encoded=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($ResumeRemote))
        $instrumentArgs+=@('-e','remote64',$encoded,'-e','size',[string]$ResumeSize,'-e','sha',$ResumeSha256,'-e','prefixSha',$ResumePrefixSha256)
    }
    $instrumentArgs+=if($Ui){"$testPackage/ru.local.qcatalog.UiChecks"}else{"$testPackage/ru.local.qcatalog.StorageChecks"}
    $result = (& $adb @instrumentArgs) -join "`n"
    $result
    if($Ui){foreach($name in @('catalog','library','downloads','ready','installing','error','catalog-wide','theme-light','theme-dark','carousel')){& $adb -s $Serial shell "run-as $testPackage cat files/$name.png > /data/local/tmp/qcatalog-ui-$name.png"; & $adb -s $Serial pull "/data/local/tmp/qcatalog-ui-$name.png" "$qaDir\$name.png"; & $adb -s $Serial shell rm "/data/local/tmp/qcatalog-ui-$name.png"}}
    if ($result -notmatch 'result=PASS ') { throw 'Storage checks failed.' }
} finally { & $adb -s $Serial uninstall $testPackage | Out-Null }
