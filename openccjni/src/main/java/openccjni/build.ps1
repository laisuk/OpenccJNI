param(
  [string]$JavaHome = $env:JAVA_HOME,
  [string]$Out = "OpenccWrapper.dll",
  [switch]$StaticRuntime
)

if (-not $JavaHome) { throw "JAVA_HOME not set" }
$inc    = Join-Path $JavaHome "include"
$incWin = Join-Path $inc "win32"

$cxx = $env:CXX; if (-not $cxx) { $cxx = "g++" }
$flags = @("-shared","-O2","-std=c++17","-I.","-I$inc","-I$incWin","-L.","-lopencc_fmmseg_capi")
if ($StaticRuntime) { $flags += @("-static-libstdc++","-static-libgcc") }
$flags += @("-o", $Out)

& $cxx @flags "OpenccWrapper.cpp" | Write-Output
Write-Host "Built: $Out"
