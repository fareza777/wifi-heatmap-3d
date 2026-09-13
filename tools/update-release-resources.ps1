$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$texts = @{
  'values' = @{
    billing_pending='Payment pending. Ads will be removed when Google Play confirms payment.'
    billing_cancelled='Purchase cancelled. You have not been charged.'
    billing_purchase_failed='Purchase could not be started. Check Google Play and try again.'
    billing_restore='Restore purchase'
    billing_restoring='Checking purchases with Google Play…'
    billing_restored='Purchase restored. Ads are removed.'
    billing_nothing_to_restore='No completed remove-ads purchase was found for this Google Play account.'
    billing_acknowledgment_retry='Ads are removed. We are retrying purchase confirmation with Google Play.'
    billing_check_price='Check price and availability on Google Play'
    billing_removed='Ads removed'
    billing_restore_subtitle='Check your Google Play account for an earlier purchase'
    privacy_options='Ad privacy choices'
    privacy_options_subtitle='Review or change your advertising privacy choices'
    privacy_options_failed='Privacy choices could not be loaded. Please try again.'
    privacy_policy='Privacy policy'
    privacy_policy_subtitle='How scans, permissions, ads and purchases use data'
    about_privacy_body='Your surveys, room plans, Wi-Fi readings and landmark photos are saved in app storage. Location permission enables Wi-Fi scanning and can attach a location to your survey; the camera supports AR tracking and optional photos. Google AdMob processes advertising and diagnostic data according to your privacy choices. Google Play processes purchases. Speed tests contact Cloudflare, and network tests contact the selected hosts. Sharing a report or image sends its contents to the destination you choose. Read the privacy policy for details.'
  }
  'values-in' = @{
    billing_pending='Pembayaran tertunda. Iklan akan dihapus setelah Google Play mengonfirmasi pembayaran.'
    billing_cancelled='Pembelian dibatalkan. Anda tidak dikenai biaya.'
    billing_purchase_failed='Pembelian belum dapat dimulai. Periksa Google Play lalu coba lagi.'
    billing_restore='Pulihkan pembelian'
    billing_restoring='Memeriksa pembelian melalui Google Play…'
    billing_restored='Pembelian dipulihkan. Iklan telah dihapus.'
    billing_nothing_to_restore='Tidak ditemukan pembelian hapus iklan yang selesai pada akun Google Play ini.'
    billing_acknowledgment_retry='Iklan telah dihapus. Konfirmasi pembelian melalui Google Play sedang dicoba kembali.'
    billing_check_price='Periksa harga dan ketersediaan di Google Play'
    billing_removed='Iklan dihapus'
    billing_restore_subtitle='Periksa pembelian sebelumnya pada akun Google Play Anda'
    privacy_options='Pilihan privasi iklan'
    privacy_options_subtitle='Tinjau atau ubah pilihan privasi periklanan Anda'
    privacy_options_failed='Pilihan privasi belum dapat dimuat. Silakan coba lagi.'
    privacy_policy='Kebijakan privasi'
    privacy_policy_subtitle='Penggunaan data survei, izin, iklan, dan pembelian'
    about_privacy_body='Survei, denah, pengukuran Wi-Fi, dan foto penanda tersimpan di penyimpanan aplikasi. Izin lokasi memungkinkan pemindaian Wi-Fi dan dapat menyertakan lokasi survei; kamera mendukung pelacakan AR dan foto opsional. Google AdMob memproses data periklanan dan diagnostik sesuai pilihan privasi Anda. Google Play memproses pembelian. Tes kecepatan menghubungi Cloudflare, dan tes jaringan menghubungi host yang dipilih. Saat membagikan laporan atau gambar, isinya dikirim ke tujuan pilihan Anda. Baca kebijakan privasi untuk penjelasan lengkap.'
  }
}
foreach ($locale in $texts.Keys) {
  $path = Join-Path $root "app/src/main/res/$locale/strings.xml"
  $content = [IO.File]::ReadAllText($path)
  foreach ($name in $texts[$locale].Keys) {
    $value = [Security.SecurityElement]::Escape($texts[$locale][$name])
    $entry = "    <string name=`"$name`">$value</string>"
    $pattern = '(?s)    <string name="' + $name + '">.*?</string>'
    if ($content -match $pattern) { $content = [regex]::Replace($content,$pattern,[System.Text.RegularExpressions.MatchEvaluator]{param($m) $entry}) }
    else { $content = $content.Replace('</resources>', "$entry`n</resources>") }
  }
  $content = $content.Replace('WiFi Heatmap 3D: Signal Map','WiFi Heatmap 3D : Signal Map')
  [IO.File]::WriteAllText($path,$content,[Text.UTF8Encoding]::new($false))
}
