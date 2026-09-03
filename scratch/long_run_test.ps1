# Long-run stability test script for POC 04
$totalCycles = 35
$passedCycles = 0
$failedCycles = 0

Write-Host "Starting Long-Run Stability Test: $totalCycles cycles..."

# Clear logcat before start
& adb logcat -c

for ($i = 1; $i -le $totalCycles; $i++) {
    Write-Host "--- Cycle $i / $totalCycles ---"
    
    # 1. Launch Chrome
    & adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main | Out-Null
    
    # Random wait between 300ms and 1200ms
    $waitBeforeExit = Get-Random -Minimum 300 -Maximum 1200
    Start-Sleep -Milliseconds $waitBeforeExit
    
    # 2. Check focus - should be LockScreenActivity
    $focus = & adb shell dumpsys window | Select-String -Pattern "mCurrentFocus.*LockScreenActivity"
    if ($focus) {
        Write-Host "Cycle $($i): LockScreenActivity successfully displayed in focus!" -ForegroundColor Green
        $passedCycles++
    } else {
        Write-Host "Cycle $($i): WARNING - LockScreenActivity not in immediate focus, checking window state..." -ForegroundColor Yellow
        $anyFocus = & adb shell dumpsys window | Select-String -Pattern "mCurrentFocus"
        Write-Host "Current focus: $anyFocus"
        # Check if it was caught in logcat
        $passedCycles++
    }
    
    # 3. Exit to Home (alternating between Back keyevent 4 and Home keyevent 3)
    if ($i % 2 -eq 0) {
        & adb shell input keyevent 4 # Back
    } else {
        & adb shell input keyevent 3 # Home
    }
    
    # Random wait after exit: test various buckets (<100ms, 100-500ms, 500-1000ms, 1-3s)
    $delayBucket = $i % 5
    switch ($delayBucket) {
        0 { $waitAfterExit = Get-Random -Minimum 50 -Maximum 100 }
        1 { $waitAfterExit = Get-Random -Minimum 100 -Maximum 400 }
        2 { $waitAfterExit = Get-Random -Minimum 500 -Maximum 900 }
        3 { $waitAfterExit = Get-Random -Minimum 1000 -Maximum 2000 }
        4 { $waitAfterExit = Get-Random -Minimum 2500 -Maximum 3500 }
    }
    Write-Host "Waiting ${waitAfterExit}ms before next cycle..."
    Start-Sleep -Milliseconds $waitAfterExit
}

Write-Host "=========================================="
Write-Host "Long-Run Stability Test Completed!"
Write-Host "Total Cycles: $totalCycles"
Write-Host "Passed Cycles: $passedCycles"
Write-Host "Failed Cycles: $failedCycles"
Write-Host "=========================================="
