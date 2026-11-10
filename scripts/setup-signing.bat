@echo off
setlocal DisableDelayedExpansion
set "ASTRBOT_SIGNING_SELF=%~f0"
set "ASTRBOT_SIGNING_REPO=%~1"
set "ASTRBOT_SIGNING_REUSE=%~2"
if not defined ASTRBOT_SIGNING_REPO set "ASTRBOT_SIGNING_REPO=z2bguoguo/AstrBotMobileUI"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$parts=[regex]::Split([IO.File]::ReadAllText($env:ASTRBOT_SIGNING_SELF),'(?m)^# POWERSHELL-BEGIN\r?$'); & ([scriptblock]::Create($parts[1]))"
set "SIGNING_EXIT_CODE=%ERRORLEVEL%"
if "%~1"=="" pause
endlocal & exit /b %SIGNING_EXIT_CODE%
# POWERSHELL-BEGIN
$ErrorActionPreference = 'Stop'

function Quote-NativeArgument([string] $Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-QuietNative([string] $Executable, [string[]] $ArgumentValues, [string] $InputText = $null) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = (($ArgumentValues | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        # Windows PowerShell 5.1 uses Console.InputEncoding for redirected stdin.
        $previousEncoding = [Console]::InputEncoding
        try {
            [Console]::InputEncoding = New-Object Text.UTF8Encoding($false)
            $started = $process.Start()
        } finally { [Console]::InputEncoding = $previousEncoding }
        if (-not $started) { throw 'Cannot start the required command.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if ($null -ne $InputText) { $process.StandardInput.Write($InputText) }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(60000)) {
            $process.Kill()
            throw 'Command timed out. Existing signing files were kept.'
        }
        # Never echo native command output: it may contain sensitive material.
        $null = $stdout.Result
        $null = $stderr.Result
        return $process.ExitCode
    } finally { $process.Dispose() }
}

try {
    $repo = $env:ASTRBOT_SIGNING_REPO
    if ($repo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
        throw 'Use: setup-signing.bat owner/repo [--reuse]'
    }
    $reuse = $env:ASTRBOT_SIGNING_REUSE -eq '--reuse'
    if ($env:ASTRBOT_SIGNING_REUSE -and -not $reuse) {
        throw 'The only optional argument is --reuse (upload the existing local key).'
    }
    $gh = Get-Command gh.exe -ErrorAction SilentlyContinue
    if (-not $gh) { throw 'GitHub CLI is missing. Install it using: winget install --id GitHub.cli -e' }
    $keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($keytool) { $keytoolPath = $keytool.Source } else {
        $candidates = @(
            $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' }),
            (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe'),
            (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\keytool.exe')
        )
        $keytoolPath = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
    }
    if (-not $keytoolPath) { throw 'JDK keytool is missing. Install JDK 17+ or set JAVA_HOME to Android Studio jbr.' }
    if ((Invoke-QuietNative $gh.Source @('auth', 'status')) -ne 0) {
        throw 'GitHub is not signed in. Run: gh auth login ; then run this BAT again.'
    }
    $directory = $env:ASTRBOT_SIGNING_DIR
    if (-not $directory) { $directory = Join-Path $env:USERPROFILE '.astrbot-mobile-signing' }
    $directory = [IO.Path]::GetFullPath($directory)
    $store = Join-Path $directory 'release.jks'
    $storePassword = Join-Path $directory 'store-password'
    $keyPassword = Join-Path $directory 'key-password'
    $aliasPath = Join-Path $directory 'key-alias'
    if (Test-Path -LiteralPath $store) {
        if (-not $reuse) { throw 'A release key already exists. It was NOT replaced. To upload that same key again, append --reuse.' }
        foreach ($path in @($storePassword, $keyPassword, $aliasPath)) {
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Existing key backup is incomplete. Restore its original password and alias files; do not regenerate the key.' }
        }
    } elseif ($reuse) { throw 'No existing release.jks was found. Run without --reuse for first-time setup.' }
    [void][IO.Directory]::CreateDirectory($directory)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($identity)
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    [void]$acl.AddAccessRule($rule)
    [IO.Directory]::SetAccessControl($directory, $acl)
    if (-not $reuse) {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        $password = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        $utf8 = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllText($storePassword, $password, $utf8)
        [IO.File]::WriteAllText($keyPassword, $password, $utf8)
        [IO.File]::WriteAllText($aliasPath, 'astrbot-mobile', $utf8)
        $arguments = @('-genkeypair', '-noprompt', '-keystore', $store, '-storetype', 'JKS', '-alias', 'astrbot-mobile', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-storepass:file', $storePassword, '-keypass:file', $keyPassword, '-dname', 'CN=AstrBot Mobile UI')
        if ((Invoke-QuietNative $keytoolPath $arguments) -ne 0) { throw 'Key generation failed. Existing files were kept. Check your JDK before retrying.' }
    }
    $alias = [IO.File]::ReadAllText($aliasPath).Trim()
    if (-not $alias) { throw 'The existing key alias is empty.' }
    $verify = @('-list', '-keystore', $store, '-storetype', 'JKS', '-alias', $alias, '-storepass:file', $storePassword)
    if ((Invoke-QuietNative $keytoolPath $verify) -ne 0) { throw 'The keystore, password or alias does not match. Nothing was uploaded.' }
    $secrets = [ordered]@{
        ANDROID_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))
        ANDROID_STORE_PASSWORD = [IO.File]::ReadAllText($storePassword).Trim()
        ANDROID_KEY_ALIAS = $alias
        ANDROID_KEY_PASSWORD = [IO.File]::ReadAllText($keyPassword).Trim()
    }
    foreach ($name in $secrets.Keys) {
        if ((Invoke-QuietNative $gh.Source @('secret', 'set', $name, '--repo', $repo) $secrets[$name]) -ne 0) {
            throw "Upload failed for $name. Your local key is safe. Check GitHub permissions and rerun with --reuse."
        }
        Write-Host "Configured $name."
    }
    Write-Host "Signing configured for $repo."
    Write-Host "Back up this private directory offline: $directory"
    Write-Host 'Keep the same key for all future releases. Rerun the Quality workflow to publish.'
    exit 0
} catch {
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
