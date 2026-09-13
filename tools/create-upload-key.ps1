$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$keyPath = Join-Path $root 'wifiheatmap-upload.jks'
$propertiesPath = Join-Path $root 'release-signing.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) { throw 'Signing files already exist; refusing to replace them.' }
$secretBytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$keyPassword = [Convert]::ToBase64String($secretBytes)
$env:WIFI_UPLOAD_KEY_PASSWORD = $keyPassword
try {
    & keytool -genkeypair -v -keystore $keyPath -storetype JKS -alias wifiheatmap-upload -keyalg RSA -keysize 3072 -validity 10000 -storepass:env WIFI_UPLOAD_KEY_PASSWORD -keypass:env WIFI_UPLOAD_KEY_PASSWORD -dname 'CN=F7 Developer, OU=Android, O=F7 Developer, C=ID'
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed.' }
    $content = "storeFile=wifiheatmap-upload.jks`nstorePassword=$keyPassword`nkeyAlias=wifiheatmap-upload`nkeyPassword=$keyPassword`n"
    [IO.File]::WriteAllText($propertiesPath,$content,[Text.UTF8Encoding]::new($false))
    Write-Output 'Created upload key and local signing configuration. Secrets are excluded from Git.'
} finally { Remove-Item Env:WIFI_UPLOAD_KEY_PASSWORD -ErrorAction SilentlyContinue }
