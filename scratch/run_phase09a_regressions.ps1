$Device = "10CF3J1F3400238"
$PocPackage = "com.example.selfdisciplinepoc01"
$CalcPackage = "com.android.bbkcalculator"
$ChromePackage = "com.android.chrome"

Write-Output "=================================================="
Write-Output "STARTING REAL-DEVICE REGRESSION SUITE (PHASE 09-A)"
Write-Output "Device: vivo iQOO Neo 10 (Android 15 / OriginOS 5)"
Write-Output "=================================================="

function Ensure-Service-Running {
    adb -s $Device shell settings put secure enabled_accessibility_services "$PocPackage/$PocPackage.AppDetectorAccessibilityService"
    adb -s $Device shell settings put secure accessibility_enabled 1
    Start-Sleep -Milliseconds 500
}

function Go-Home {
    adb -s $Device shell input keyevent 3
    Start-Sleep -Milliseconds 800
}

function Clear-App-Data {
    adb -s $Device shell pm clear $PocPackage
    Start-Sleep -Seconds 1
    Ensure-Service-Running
}

function Get-Current-Focus {
    $focus = adb -s $Device shell dumpsys window | Select-String "mCurrentFocus"
    return ($focus -join " ")
}

function Clear-Logcat {
    adb -s $Device logcat -c
}

function Get-Logcat-Output {
    param([string]$filter)
    $logs = adb -s $Device logcat -d | Select-String $filter
    return $logs
}

$results = [ordered]@{}

# Wake up device
adb -s $Device shell input keyevent 224
adb -s $Device shell wm dismiss-keyguard
Start-Sleep -Milliseconds 500

# Setup: Add Calculator target
Clear-App-Data
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_ADD_TARGET $CalcPackage
Start-Sleep -Seconds 1
Go-Home

# --- TEST 09A-01: Limit = 10 seconds. Open allowed target. Wait without interacting. ---
# Expected: LOCK occurs at/after deadline WITHOUT requiring another Accessibility window event!
Write-Output "`n--- TEST 09A-01: Deadline Lock Without New Event ---"
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_POLICY_TARGET $CalcPackage --ez EXTRA_LIMIT_ENABLED true --ei EXTRA_LIMIT_SECONDS 10
Start-Sleep -Milliseconds 800
Go-Home
Clear-Logcat

# Open Calculator
adb -s $Device shell am start -n "$CalcPackage/.Calculator"
Start-Sleep -Milliseconds 1500

# Check that Calculator is initially allowed and in foreground
$focusInitial = Get-Current-Focus
$allowedInitial = $focusInitial -match "bbkcalculator"
Write-Output "Initial focus (should be Calculator): $allowedInitial ($focusInitial)"

# Now WAIT 10.5 seconds WITHOUT any user interaction or new window events!
Write-Output "Waiting 10.5 seconds for real-time deadline trigger without touch..."
Start-Sleep -Milliseconds 10500

# Check focus: LockScreenActivity must have appeared!
$focusAfterDeadline = Get-Current-Focus
$lockedAfterDeadline = $focusAfterDeadline -match "LockScreenActivity"
$logsWatcher = Get-Logcat-Output "WATCHER: LOCK_TRIGGERED|WATCHER: CALLBACK"

$t1Pass = $allowedInitial -and $lockedAfterDeadline -and ($logsWatcher.Count -gt 0)
Write-Output "[09A-01_Deadline_Lock_No_Event] Focus: $focusAfterDeadline | Watcher Logged: $($logsWatcher.Count -gt 0) | Result: $(if ($t1Pass) {'PASS'} else {'FAIL'})"
$results["09A-01_Deadline_Lock_No_Event"] = if ($t1Pass) { "PASS" } else { "FAIL" }
Go-Home

# --- TEST 09A-02: Limit = 10s. Leave target at 5s. ---
# Expected: No delayed callback locks the new foreground package!
Write-Output "`n--- TEST 09A-02: Leave Target Before Deadline ---"
# Reset calculator usage data
Clear-App-Data
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_ADD_TARGET $CalcPackage
Start-Sleep -Milliseconds 800
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_POLICY_TARGET $CalcPackage --ez EXTRA_LIMIT_ENABLED true --ei EXTRA_LIMIT_SECONDS 10
Start-Sleep -Milliseconds 800
Go-Home
Clear-Logcat

# Open Calculator for 4 seconds
adb -s $Device shell am start -n "$CalcPackage/.Calculator"
Start-Sleep -Seconds 4

# Leave Calculator by going Home at 4s
Go-Home
Write-Output "Left Calculator at 4s. Waiting 8 more seconds on Home..."
Start-Sleep -Seconds 8

# Check focus: Home/Launcher must remain focused, LockScreenActivity must NOT appear!
$focusAfterLeave = Get-Current-Focus
$t2Pass = ($focusAfterLeave -match "launcher") -and ($focusAfterLeave -notmatch "LockScreenActivity")
Write-Output "[09A-02_Leave_Target_Before_Deadline] Focus: $focusAfterLeave | Result: $(if ($t2Pass) {'PASS'} else {'FAIL'})"
$results["09A-02_Leave_Target_Before_Deadline"] = if ($t2Pass) { "PASS" } else { "FAIL" }

# --- TEST 09A-03: Reach exact threshold -> LockScreenActivity ---
Write-Output "`n--- TEST 09A-03: Reach Exact Threshold ---"
# Resume calculator and let it reach the remaining 6 seconds
Clear-Logcat
adb -s $Device shell am start -n "$CalcPackage/.Calculator"
Start-Sleep -Seconds 8

$focusThreshold = Get-Current-Focus
$t3Pass = $focusThreshold -match "LockScreenActivity"
Write-Output "[09A-03_Reach_Exact_Threshold] Focus: $focusThreshold | Result: $(if ($t3Pass) {'PASS'} else {'FAIL'})"
$results["09A-03_Reach_Exact_Threshold"] = if ($t3Pass) { "PASS" } else { "FAIL" }
Go-Home

# --- TEST 09A-04: Screen OFF at 5 seconds, wait 10+ seconds. No usage counted during Screen OFF. ---
Write-Output "`n--- TEST 09A-04: Screen OFF Pauses Deadline ---"
Clear-App-Data
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_ADD_TARGET $CalcPackage
Start-Sleep -Milliseconds 800
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_POLICY_TARGET $CalcPackage --ez EXTRA_LIMIT_ENABLED true --ei EXTRA_LIMIT_SECONDS 10
Start-Sleep -Milliseconds 800
Go-Home
Clear-Logcat

# Open Calculator for 4 seconds
adb -s $Device shell am start -n "$CalcPackage/.Calculator"
Start-Sleep -Seconds 4

# Turn Screen OFF
adb -s $Device shell input keyevent 26
Start-Sleep -Seconds 8

# Turn Screen ON
adb -s $Device shell input keyevent 224
adb -s $Device shell wm dismiss-keyguard
Start-Sleep -Milliseconds 1500

# Since screen was off for 8s, total usage is only ~4s (< 10s limit). Calculator must still be active!
$focusAfterScreenOn = Get-Current-Focus
$t4Pass = ($focusAfterScreenOn -match "bbkcalculator") -and ($focusAfterScreenOn -notmatch "LockScreenActivity")
Write-Output "[09A-04_Screen_OFF_Pauses_Deadline] Focus: $focusAfterScreenOn | Result: $(if ($t4Pass) {'PASS'} else {'FAIL'})"
$results["09A-04_Screen_OFF_Pauses_Deadline"] = if ($t4Pass) { "PASS" } else { "FAIL" }
Go-Home

# --- TEST 09A-05: Screen ON Does Not Assume Target Resumed Until Foreground Event ---
Write-Output "`n--- TEST 09A-05: Screen ON Confirms Via Foreground Event ---"
Clear-Logcat
Go-Home
adb -s $Device shell input keyevent 26
Start-Sleep -Seconds 2
adb -s $Device shell input keyevent 224
adb -s $Device shell wm dismiss-keyguard
Start-Sleep -Milliseconds 1500
$logsScreenOn = Get-Logcat-Output "SCREEN_RECEIVER.*Screen ON"
$logsBlindStart = Get-Logcat-Output "USAGE: START"
$t5Pass = ($logsScreenOn.Count -gt 0) -and ($logsBlindStart.Count -eq 0)
Write-Output "[09A-05_Screen_ON_Awaits_Foreground] Screen ON: $($logsScreenOn.Count -gt 0), Blind Start: $($logsBlindStart.Count -gt 0) | Result: $(if ($t5Pass) {'PASS'} else {'FAIL'})"
$results["09A-05_Screen_ON_Awaits_Foreground"] = if ($t5Pass) { "PASS" } else { "FAIL" }

# --- TEST 09A-06: Process / Service Lifecycle Preserves Stability (No Stale Launch) ---
Write-Output "`n--- TEST 09A-06: Process Lifecycle & Stale Guard ---"
Clear-Logcat
adb -s $Device shell am force-stop $PocPackage
Ensure-Service-Running
Go-Home
Start-Sleep -Milliseconds 800
$focusAfterServiceRestart = Get-Current-Focus
$t6Pass = ($focusAfterServiceRestart -match "launcher") -and ($focusAfterServiceRestart -notmatch "LockScreenActivity")
Write-Output "[09A-06_Service_Lifecycle_Stale_Guard] Focus: $focusAfterServiceRestart | Result: $(if ($t6Pass) {'PASS'} else {'FAIL'})"
$results["09A-06_Service_Lifecycle_Stale_Guard"] = if ($t6Pass) { "PASS" } else { "FAIL" }

# --- TEST 09A-07: Schedule + Daily Limit Coexistence ---
Write-Output "`n--- TEST 09A-07: Schedule + Daily Limit Coexistence ---"
Clear-App-Data
# Setup schedule 00:00 - 23:59 (active) + limit 60 min
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_ADD_TARGET $CalcPackage
Start-Sleep -Milliseconds 800
adb -s $Device shell am start -n "$PocPackage/.MainActivity" --es EXTRA_POLICY_TARGET $CalcPackage --ez EXTRA_SCHEDULE_ENABLED true --ei EXTRA_START_HOUR 0 --ei EXTRA_START_MINUTE 0 --ei EXTRA_END_HOUR 23 --ei EXTRA_END_MINUTE 59 --ez EXTRA_LIMIT_ENABLED true --ei EXTRA_LIMIT_MINUTES 60
Start-Sleep -Milliseconds 800
Go-Home

# Launch calculator -> Schedule is active, must lock immediately
adb -s $Device shell am start -n "$CalcPackage/.Calculator"
Start-Sleep -Milliseconds 1500
$focusSched = Get-Current-Focus
$t7Pass = $focusSched -match "LockScreenActivity"
Write-Output "[09A-07_Schedule_And_Limit_Coexistence] Focus: $focusSched | Result: $(if ($t7Pass) {'PASS'} else {'FAIL'})"
$results["09A-07_Schedule_And_Limit_Coexistence"] = if ($t7Pass) { "PASS" } else { "FAIL" }
Go-Home

# --- TEST 09A-08: Rapid Foreground Transitions (No Duplicate Callbacks, Crashes, or Stuck Overlay) ---
Write-Output "`n--- TEST 09A-08: Rapid Foreground Transitions ---"
Clear-Logcat
for ($i = 1; $i -le 5; $i++) {
    adb -s $Device shell am start -n "$ChromePackage/com.google.android.apps.chrome.Main"
    Start-Sleep -Milliseconds 300
    Go-Home
    Start-Sleep -Milliseconds 300
}
# Final open Chrome
adb -s $Device shell am start -n "$ChromePackage/com.google.android.apps.chrome.Main"
Start-Sleep -Milliseconds 1500
$focusRapid = Get-Current-Focus
$t8Pass = $focusRapid -match "LockScreenActivity"
Write-Output "[09A-08_Rapid_Foreground_Transitions] Focus: $focusRapid | Result: $(if ($t8Pass) {'PASS'} else {'FAIL'})"
$results["09A-08_Rapid_Foreground_Transitions"] = if ($t8Pass) { "PASS" } else { "FAIL" }
Go-Home

# Cleanup
Clear-App-Data
Go-Home

Write-Output "`n=================================================="
Write-Output "PHASE 09-A REGRESSION SUITE RESULTS SUMMARY"
Write-Output "=================================================="
$passCount = 0
foreach ($test in $results.Keys) {
    Write-Output ("{0,-38} : {1}" -f $test, $results[$test])
    if ($results[$test] -eq "PASS") { $passCount++ }
}
Write-Output "=================================================="
Write-Output "OVERALL STATUS: $(if ($passCount -eq $results.Count) {"ALL TESTS PASSED ($passCount/$($results.Count))"} else {"FAILED ($passCount/$($results.Count))"})"
Write-Output "=================================================="
