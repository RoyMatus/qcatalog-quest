$ErrorActionPreference='Stop'
$libraryDir=Join-Path $PSScriptRoot 'lib'
New-Item -ItemType Directory -Force $libraryDir | Out-Null
foreach($dependency in (Get-Content (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json)) {
    $file=Join-Path $libraryDir $dependency.File
    if (!(Test-Path -LiteralPath $file)) { Invoke-WebRequest $dependency.Url -OutFile $file }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $dependency.Sha256) { throw "Dependency SHA-256 mismatch: $($dependency.File)" }
}
