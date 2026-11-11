param(
    [Parameter(Mandatory=$true)][string]$Manifest,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $speaker.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'en-*' } | Select-Object -First 1
    if (!$voice) { throw 'An enabled English Windows speech voice is required.' }
    $speaker.SelectVoice($voice.VoiceInfo.Name)
    $speaker.Rate = -1
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(8000, [System.Speech.AudioFormat.AudioBitsPerSample]::Eight, [System.Speech.AudioFormat.AudioChannel]::Mono)
    foreach ($item in (Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json)) {
        $destination = Join-Path $OutputDirectory ($item.key + '.wav')
        if (!(Test-Path -LiteralPath $destination)) {
            $speaker.SetOutputToWaveFile($destination, $format)
            $speaker.Speak($item.text)
            $speaker.SetOutputToNull()
        }
    }
} finally { $speaker.Dispose() }
# Original office illustration for Part 1. No external image/download dependency.
$bitmap = New-Object System.Drawing.Bitmap(800, 480)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$font = New-Object System.Drawing.Font('Arial', 14)
try {
    $graphics.Clear([System.Drawing.Color]::WhiteSmoke)
    $graphics.FillRectangle([System.Drawing.Brushes]::LightBlue, 35, 45, 180, 130)
    $graphics.DrawRectangle([System.Drawing.Pens]::SteelBlue, 35, 45, 180, 130)
    $graphics.DrawLine([System.Drawing.Pens]::SteelBlue, 125, 45, 125, 175)
    $graphics.FillRectangle([System.Drawing.Brushes]::Tan, 180, 300, 390, 25)
    $graphics.FillRectangle([System.Drawing.Brushes]::SaddleBrown, 195, 325, 15, 95)
    $graphics.FillRectangle([System.Drawing.Brushes]::SaddleBrown, 540, 325, 15, 95)
    $graphics.FillRectangle([System.Drawing.Brushes]::SlateGray, 355, 225, 110, 70)
    $graphics.FillRectangle([System.Drawing.Brushes]::LightSteelBlue, 364, 234, 92, 51)
    $graphics.FillRectangle([System.Drawing.Brushes]::White, 220, 284, 75, 10)
    $graphics.FillEllipse([System.Drawing.Brushes]::Bisque, 285, 160, 50, 50)
    $graphics.FillRectangle([System.Drawing.Brushes]::RoyalBlue, 278, 210, 65, 80)
    $graphics.DrawLine([System.Drawing.Pens]::RoyalBlue, 320, 240, 365, 280)
    $graphics.FillRectangle([System.Drawing.Brushes]::Gray, 650, 185, 100, 210)
    foreach ($y in @(210,255,300,345)) { $graphics.FillRectangle([System.Drawing.Brushes]::WhiteSmoke, 660, $y, 80, 30) }
    $graphics.DrawString('OFFICE - Original practice illustration', $font, [System.Drawing.Brushes]::DarkSlateGray, 35, 15)
    $graphics.DrawString('FILES', $font, [System.Drawing.Brushes]::DarkSlateGray, 666, 155)
    $bitmap.Save((Join-Path $OutputDirectory 'office.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
