<#
.SYNOPSIS
  Driver for building, installing, launching, and driving the FitTracker Android app
  on a real, physically connected Android device over adb (no emulator, no Linux
  container - this is a native Windows + PowerShell + physical-USB-device setup).

.DESCRIPTION
  Wraps the exact sequence of commands used interactively (and verified) across a long
  session of building this app: JAVA_HOME must point at a JDK (Android Studio's bundled
  JBR works) before any ./gradlew invocation, or gradle fails with
  "JAVA_HOME is not set and no 'java' command could be found in your PATH."

  Also wraps uiautomator-based tapping, which avoids the single most common failure mode
  hit repeatedly by hand: screenshots returned by the Read tool are DOWNSCALED (this
  device: real 1080x2400 -> displayed 900x2000, a 1.2x factor), and manually eyeballing
  coordinates from the displayed image and using them directly as real tap coordinates
  is consistently wrong. -Action tapText sidesteps this entirely by reading REAL
  device pixel bounds out of uiautomator dump and tapping the exact center - no manual
  scaling, no guessing.

  NOTE ON ENCODING: keep this file plain ASCII. Windows PowerShell 5.1 (not pwsh 7) reads
  a BOM-less script using the system codepage, not UTF-8 - a stray em-dash or accented
  character saved as UTF-8-no-BOM was enough to corrupt parsing further down the file
  and throw confusing "Unexpected token '{'" errors nowhere near the real cause. Do not
  reintroduce curly quotes / em-dashes / accents into this file.

.PARAMETER Action
  build | install | buildInstall | wake | launch | screenshot | dump | tapText | tapXY |
  type | back | home | crashcheck

.EXAMPLE
  .\driver.ps1 -Action buildInstall
  .\driver.ps1 -Action wake
  .\driver.ps1 -Action launch
  .\driver.ps1 -Action screenshot -Name main
  .\driver.ps1 -Action tapText -Text "Katalog"
  .\driver.ps1 -Action type -Text "Benchpress"
  .\driver.ps1 -Action crashcheck
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("build", "install", "buildInstall", "wake", "launch", "screenshot", "dump", "tapText", "tapXY", "type", "back", "home", "crashcheck")]
    [string]$Action,

    [string]$Text,
    [int]$X,
    [int]$Y,
    [string]$Name = "shot",
    [string]$Activity = ".MainActivity"
)

# --- Fixed environment for this machine/device - adjust if you're on a different box ---
$ProjectDir = "C:\applikacie kubo\FitTrackerFix\FitTracker"
$JavaHome   = "C:\Program Files\Android\Android Studio\jbr"
$Adb        = "C:\Users\jakub\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$Package    = "sk.fit.tracker"
$Apk        = "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk"
$OutDir     = "$PSScriptRoot\out"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Invoke-Gradle {
    param([string]$Task)
    Push-Location $ProjectDir
    try {
        $env:JAVA_HOME = $JavaHome
        & .\gradlew.bat $Task --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradlew $Task failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Get-UiDump {
    $remote = "/sdcard/_driver_dump.xml"
    $local = "$OutDir\ui_dump.xml"
    & $Adb shell uiautomator dump $remote | Out-Null
    & $Adb pull $remote $local | Out-Null
    return $local
}

switch ($Action) {

    "build" {
        Invoke-Gradle -Task "assembleDebug"
        Write-Output "Built: $Apk"
    }

    "install" {
        & $Adb install -r $Apk
    }

    "buildInstall" {
        Invoke-Gradle -Task "assembleDebug"
        & $Adb install -r $Apk
    }

    "wake" {
        & $Adb shell input keyevent KEYCODE_WAKEUP
        Start-Sleep -Milliseconds 400
        # Swipe up once in case a (non-secure) lock/swipe screen is showing.
        & $Adb shell input swipe 540 1800 540 600 300
    }

    "launch" {
        # ONLY MainActivity is exported (android:exported="true" in the manifest) - every
        # other activity in this app throws a SecurityException ("Permission Denial ...
        # not exported") if you try "am start -n" on it directly. Reach every other
        # screen by tapping through the UI from MainActivity (see tapText below).
        & $Adb shell am start -n "$Package/$Activity"
    }

    "screenshot" {
        $remote = "/sdcard/_driver_$Name.png"
        $local = "$OutDir\$Name.png"
        & $Adb shell screencap -p $remote | Out-Null
        & $Adb pull $remote $local | Out-Null
        # Report REAL device pixel size so the caller never has to guess a display/scale
        # factor - Get-Item on a PNG won't give pixel dims in plain PowerShell, so ask
        # the device directly (authoritative, no downscaling like the Read tool applies).
        $size = & $Adb shell wm size
        Write-Output "Saved: $local"
        Write-Output "Device screen size (real px, use these for any manual tap math): $size"
    }

    "dump" {
        $path = Get-UiDump
        Write-Output "UI dump saved: $path"
    }

    "tapText" {
        if (-not $Text) { throw "Provide -Text" }
        $dumpPath = Get-UiDump
        [xml]$xml = Get-Content $dumpPath
        $node = $xml.SelectNodes("//node[@text]") | Where-Object { $_.text -like "*$Text*" } | Select-Object -First 1
        if (-not $node) { throw "No node with text matching '$Text' found in current UI dump ($dumpPath)." }
        if ($node.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
            $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            & $Adb shell input tap $cx $cy
            Write-Output "Tapped '$($node.text)' at real device coords ($cx, $cy) - bounds $($node.bounds)"
        } else {
            throw "Could not parse bounds '$($node.bounds)' for node with text '$($node.text)'"
        }
    }

    "tapXY" {
        & $Adb shell input tap $X $Y
    }

    "type" {
        if (-not $Text) { throw "Provide -Text" }
        # adb shell input text treats a raw space as a token separator and truncates -
        # always substitute percent-s for spaces before sending.
        $escaped = $Text -replace ' ', '%s'
        & $Adb shell input text $escaped
    }

    "back" {
        & $Adb shell input keyevent KEYCODE_BACK
    }

    "home" {
        & $Adb shell input keyevent KEYCODE_HOME
    }

    "crashcheck" {
        & $Adb logcat -d AndroidRuntime:E "*:S"
    }
}
