param([string]$Serial = 'emulator-5554', [switch]$OriginalsOnly, [switch]$FramedOnly, [int[]]$Pages = (0..7))
$ErrorActionPreference = 'Stop'
$adb = 'C:/Android/Sdk/platform-tools/adb.exe'
$package = 'com.f7developer.wifiheatmap3d'
$captureRoot = $PSScriptRoot
$names = @('01-3d-coverage','02-map-export','03-signal-trends','04-channel-analysis','05-security-check','06-ping-test','07-survey-history','08-appearance')
& $adb -s $Serial shell wm size 1080x1920
& $adb -s $Serial shell wm density 480
foreach ($framed in @($false, $true)) {
    if ($OriginalsOnly -and $framed) { continue }
    if ($FramedOnly -and !$framed) { continue }
    $folder = if ($framed) { 'play-store' } else { 'originals' }
    foreach ($page in $Pages) {
        # Preserve the user-supplied original phone hero unchanged.
        if (!$framed -and $page -eq 0) { continue }
        & $adb -s $Serial shell am force-stop $package
        & $adb -s $Serial shell am start -W -n "$package/com.sinyal.app.marketing.StoreScreenshotActivity" --ei page $page --ez framed $framed.ToString().ToLower()
        Start-Sleep -Seconds $(if ($page -eq 0) { 15 } else { 5 })
        & $adb -s $Serial shell screencap -p /sdcard/store-capture.png
        & $adb -s $Serial pull /sdcard/store-capture.png (Join-Path $captureRoot "$folder/$($names[$page]).png")
        Write-Output "Captured $folder/$($names[$page]).png"
    }
}
