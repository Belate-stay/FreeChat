$tabletAdb = 'C:/Users/M/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$tabletQaDir = 'D:/Desktop/FreeChat/FreeChatAndroid/_verify/v1205'

function Read-TabletUi {
    $tabletDumpOk = $false
    for ($tabletAttempt = 0; $tabletAttempt -lt 3; $tabletAttempt++) {
        $tabletDumpOutput = & $tabletAdb -s emulator-5560 shell uiautomator dump /sdcard/v1205-ui.xml 2>&1
        if (($tabletDumpOutput -join "`n") -match 'dumped to') { $tabletDumpOk = $true; break }
    }
    if (-not $tabletDumpOk) { throw 'UI hierarchy not ready; refusing stale XML.' }
    & $tabletAdb -s emulator-5560 pull /sdcard/v1205-ui.xml "$tabletQaDir/ui.xml" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not pull current UI hierarchy.' }
    return [xml](Get-Content -LiteralPath "$tabletQaDir/ui.xml")
}

function Tap-TabletNode([string]$label, [bool]$description = $false, [int]$index = 0) {
    $tabletTree = Read-TabletUi
    $tabletNodes = @($tabletTree.SelectNodes('//node') | Where-Object {
        if ($description) { $_.'content-desc' -eq $label } else { $_.text -eq $label }
    })
    if ($tabletNodes.Count -le $index) { throw "Missing UI node: $label index=$index" }
    $tabletBounds = [regex]::Matches($tabletNodes[$index].bounds, '\d+') | ForEach-Object { [int]$_.Value }
    & $tabletAdb -s emulator-5560 shell input tap ([int](($tabletBounds[0] + $tabletBounds[2]) / 2)) ([int](($tabletBounds[1] + $tabletBounds[3]) / 2))
    "Tapped $label at $($tabletNodes[$index].bounds)"
}

function Save-TabletShot([string]$name) {
    Read-TabletUi | Out-Null
    Copy-Item -LiteralPath "$tabletQaDir/ui.xml" -Destination "$tabletQaDir/$name.xml"
    & $tabletAdb -s emulator-5560 shell screencap -p /sdcard/v1205-shot.png
    & $tabletAdb -s emulator-5560 pull /sdcard/v1205-shot.png "$tabletQaDir/$name.png" 2>$null
}

function Show-TabletLabels {
    $tabletTree = Read-TabletUi
    $tabletTree.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
        '{0} | {1} | {2} | enabled={3}' -f $_.text,$_.'content-desc',$_.bounds,$_.enabled
    }
}

function Set-TabletFontPreset([int]$ordinal, [string]$expectedPercent) {
    Tap-TabletNode '字体大小' | Out-Null
    $fontTree = Read-TabletUi
    $fontSlider = $fontTree.SelectSingleNode('//node[@class="android.widget.SeekBar"]')
    if (-not $fontSlider) { throw 'Missing live font slider.' }
    $fontBounds = [regex]::Matches($fontSlider.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $fontX = [int]($fontBounds[0] + ($fontBounds[2] - $fontBounds[0]) * $ordinal / 4)
    $fontInset = [int](($fontBounds[2] - $fontBounds[0]) * .06)
    $fontX = [Math]::Clamp($fontX, $fontBounds[0] + $fontInset, $fontBounds[2] - $fontInset)
    $fontY = [int](($fontBounds[1] + $fontBounds[3]) / 2)
    & $tabletAdb -s emulator-5560 shell input swipe ([int](($fontBounds[0] + $fontBounds[2]) / 2)) $fontY $fontX $fontY 240
    $fontPreview = Read-TabletUi
    if (-not ($fontPreview.SelectNodes('//node') | Where-Object { $_.text -eq $expectedPercent })) {
        throw "Font preview did not select $expectedPercent."
    }
    Tap-TabletNode '确定' | Out-Null
    $fontResult = Read-TabletUi
    $fontResult.SelectNodes('//node') | Where-Object { $_.text -in @('字体', '字体大小', '标准', '特大', '小') } |
        ForEach-Object { '{0} | {1}' -f $_.text, $_.bounds }
}
