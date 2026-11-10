$ErrorActionPreference = 'Stop'
$fixture = Join-Path $env:RUNNER_TEMP ('signing bat ' + [guid]::NewGuid())
[void][IO.Directory]::CreateDirectory($fixture)
$mockDirectory = Join-Path $fixture 'mock'
[void][IO.Directory]::CreateDirectory($mockDirectory)
$env:SIGNING_MOCK_RECORD = Join-Path $fixture 'records'
[void][IO.Directory]::CreateDirectory($env:SIGNING_MOCK_RECORD)
$env:ASTRBOT_SIGNING_DIR = Join-Path $fixture 'private key'
$source = @'
using System;
using System.IO;
public static class FakeGh {
  public static int Main(string[] args) {
    if (args.Length == 2 && args[0] == "auth" && args[1] == "status") return 0;
    if (args.Length != 5 || args[0] != "secret" || args[1] != "set" || args[3] != "--repo" || args[4] != "example/testing") return 3;
    if (args[2] == Environment.GetEnvironmentVariable("SIGNING_MOCK_FAIL_SECRET")) return 2;
    using (var input = Console.OpenStandardInput())
    using (var buffer = new MemoryStream()) {
      input.CopyTo(buffer);
      byte[] bytes = buffer.ToArray();
      if (bytes.Length >= 3 && bytes[0] == 239 && bytes[1] == 187 && bytes[2] == 191) return 4;
      File.WriteAllBytes(Path.Combine(Environment.GetEnvironmentVariable("SIGNING_MOCK_RECORD"), args[2]), bytes);
    }
    return 0;
  }
}
'@
Add-Type -TypeDefinition $source -OutputType ConsoleApplication -OutputAssembly (Join-Path $mockDirectory 'gh.exe')
$env:PATH = $mockDirectory + ';' + $env:PATH
$bat = Join-Path $fixture 'setup-signing.bat'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\setup-signing.bat') -Destination $bat
function Run-Bat([string] $mode, [int] $expected) {
    & $bat example/testing $mode
    if ($LASTEXITCODE -ne $expected) { throw "Unexpected BAT exit code: $LASTEXITCODE, wanted $expected" }
}
Run-Bat '' 0
$store = Join-Path $env:ASTRBOT_SIGNING_DIR 'release.jks'
$firstHash = (Get-FileHash -LiteralPath $store -Algorithm SHA256).Hash
$names = @('ANDROID_KEYSTORE_BASE64', 'ANDROID_STORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
foreach ($name in $names) {
    if (-not (Test-Path -LiteralPath (Join-Path $env:SIGNING_MOCK_RECORD $name))) { throw "Missing secret: $name" }
}
$encoded = [IO.File]::ReadAllText((Join-Path $env:SIGNING_MOCK_RECORD 'ANDROID_KEYSTORE_BASE64'))
if ([Convert]::ToBase64String([Convert]::FromBase64String($encoded)) -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))) { throw 'Keystore upload bytes changed.' }
if ([IO.File]::ReadAllText((Join-Path $env:SIGNING_MOCK_RECORD 'ANDROID_KEY_ALIAS')) -ne 'astrbot-mobile') { throw 'Alias upload changed.' }
Run-Bat '' 1
if ((Get-FileHash -LiteralPath $store -Algorithm SHA256).Hash -ne $firstHash) { throw 'Existing key was overwritten.' }
$env:SIGNING_MOCK_FAIL_SECRET = 'ANDROID_STORE_PASSWORD'
Run-Bat '--reuse' 1
$env:SIGNING_MOCK_FAIL_SECRET = ''
Run-Bat '--reuse' 0
if ((Get-FileHash -LiteralPath $store -Algorithm SHA256).Hash -ne $firstHash) { throw 'Retry changed the signing key.' }
& $bat invalid-repo
if ($LASTEXITCODE -ne 1) { throw 'Invalid repository was accepted.' }
Write-Host 'Windows signing checks passed: generation, stdin uploads, spaces in paths, no-overwrite, partial failure and retry.'

exit 0
