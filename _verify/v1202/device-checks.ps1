$tabletAdb = 'C:/Users/M/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$tabletQaDir = 'D:/Desktop/FreeChat/FreeChatAndroid/_verify/v1202'

function Read-TabletUi {
    $tabletDumpOk = $false
    for ($tabletAttempt = 0; $tabletAttempt -lt 3; $tabletAttempt++) {
        $tabletDumpOutput = & $tabletAdb -s emulator-5560 shell uiautomator dump /sdcard/v1202-ui.xml 2>&1
        if (($tabletDumpOutput -join "`n") -match 'dumped to') { $tabletDumpOk = $true; break }
    }
    if (-not $tabletDumpOk) { throw 'UI hierarchy not ready; refusing stale XML.' }
    & $tabletAdb -s emulator-5560 pull /sdcard/v1202-ui.xml "$tabletQaDir/ui.xml" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not pull the current UI hierarchy.' }
    [xml]$tabletTree = Get-Content "$tabletQaDir/ui.xml"
    return $tabletTree
}

function Tap-TabletNode([string]$label, [bool]$description = $false, [int]$index = 0) {
    $tabletTree = Read-TabletUi
    $tabletNodes = @($tabletTree.SelectNodes('//node') | Where-Object {
        if ($description) { $_.'content-desc' -eq $label } else { $_.text -eq $label }
    })
    if ($tabletNodes.Count -le $index) { throw "Missing UI node: $label index=$index" }
    $tabletBounds = [regex]::Matches($tabletNodes[$index].bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $tabletTapX = [int](($tabletBounds[0] + $tabletBounds[2]) / 2)
    $tabletTapY = [int](($tabletBounds[1] + $tabletBounds[3]) / 2)
    & $tabletAdb -s emulator-5560 shell input tap $tabletTapX $tabletTapY
    "Tapped $label at $tabletTapX,$tabletTapY"
}

function Save-TabletShot([string]$name) {
    Read-TabletUi | Out-Null
    & $tabletAdb -s emulator-5560 shell screencap -p /sdcard/v1202-shot.png
    & $tabletAdb -s emulator-5560 pull /sdcard/v1202-shot.png "$tabletQaDir/$name.png" 2>$null
}

function Show-TabletLabels {
    $tabletTree = Read-TabletUi
    $tabletTree.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
        '{0} | {1} | {2} | enabled={3}' -f $_.text,$_.'content-desc',$_.bounds,$_.enabled
    }
}
