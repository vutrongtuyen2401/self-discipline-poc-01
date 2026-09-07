package com.example.selfdisciplinepoc01

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.os.SystemClock
import android.view.ViewTreeObserver
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import java.util.Locale

class LockScreenActivity : ComponentActivity() {

    private val logger: DiagnosticLogger by lazy {
        DiagnosticLoggerProvider.getLogger()
    }

    private var currentSessionId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val t4 = SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)
        currentSessionId = savedInstanceState?.getLong(EXTRA_SESSION_ID) ?: intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        val t1 = intent.getLongExtra(EXTRA_T1_NS, 0L)
        val t2 = intent.getLongExtra(EXTRA_T2_NS, 0L)
        val t3 = intent.getLongExtra(EXTRA_T3_NS, 0L)
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "unknown"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        Log.d(TAG, "onCreate (sessionId=$currentSessionId)")
        logger.info(
            DiagnosticEvent(
                type = DiagnosticEventType.LOCKSCREEN_CREATED,
                message = "LockScreenActivity created for $targetPkg (sessionId=$currentSessionId)",
                packageName = targetPkg,
                sessionId = currentSessionId
            )
        )
        if (t1 != 0L) {
            setupFirstFrameLatencyMeasurement(targetPkg, t1, t2, t3, t4)
        }

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LockScreenContent(
                        onGoToHome = {
                            AppDetectorAccessibilityService.onLockScreenExited(currentSessionId)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart (sessionId=$currentSessionId)")
        AppDetectorAccessibilityService.onLockScreenResumed(currentSessionId)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume (sessionId=$currentSessionId)")
        logger.debug(
            DiagnosticEvent(
                type = DiagnosticEventType.LOCKSCREEN_RESUMED,
                message = "LockScreenActivity resumed (sessionId=$currentSessionId)",
                sessionId = currentSessionId
            )
        )
        AppDetectorAccessibilityService.onLockScreenResumed(currentSessionId)
    }

    override fun onNewIntent(intent: Intent) {
        val t4 = SystemClock.elapsedRealtimeNanos()
        super.onNewIntent(intent)
        setIntent(intent)
        currentSessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        val t1 = intent.getLongExtra(EXTRA_T1_NS, 0L)
        val t2 = intent.getLongExtra(EXTRA_T2_NS, 0L)
        val t3 = intent.getLongExtra(EXTRA_T3_NS, 0L)
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "unknown"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        Log.d(TAG, "onNewIntent (sessionId=$currentSessionId)")
        logger.info(
            DiagnosticEvent(
                type = DiagnosticEventType.LOCKSCREEN_NEW_INTENT,
                message = "LockScreenActivity received newIntent for $targetPkg (sessionId=$currentSessionId)",
                packageName = targetPkg,
                sessionId = currentSessionId
            )
        )
        logger.info(
            DiagnosticEvent(
                type = DiagnosticEventType.LOCK_SESSION_REUSED,
                message = "Existing LockScreenActivity instance reused for $targetPkg (sessionId=$currentSessionId)",
                packageName = targetPkg,
                sessionId = currentSessionId
            )
        )
        AppDetectorAccessibilityService.onLockScreenResumed(currentSessionId)
        if (t1 != 0L) {
            setupFirstFrameLatencyMeasurement(targetPkg, t1, t2, t3, t4)
        }
    }

    private fun setupFirstFrameLatencyMeasurement(
        targetPkg: String,
        t1: Long,
        t2: Long,
        t3: Long,
        t4: Long
    ) {
        val decorView = window.decorView
        decorView.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var hasDrawn = false
            override fun onDraw() {
                if (!hasDrawn) {
                    hasDrawn = true
                    val t5 = SystemClock.elapsedRealtimeNanos()
                    decorView.post {
                        try {
                            decorView.viewTreeObserver.removeOnDrawListener(this)
                        } catch (e: Exception) {
                            Log.w(TAG, "Không thể remove OnDrawListener", e)
                        }
                    }
                    emitLatencyLog(targetPkg, t1, t2, t3, t4, t5)
                }
            }
        })
    }

    private fun emitLatencyLog(
        targetPkg: String,
        t1: Long,
        t2: Long,
        t3: Long,
        t4: Long,
        t5: Long
    ) {
        val eventToLaunchMs = (t2 - t1) / 1_000_000.0
        val launchToOnCreateMs = (t4 - t3) / 1_000_000.0
        val onCreateToFirstFrameMs = (t5 - t4) / 1_000_000.0
        val eventToFirstFrameMs = (t5 - t1) / 1_000_000.0

        val formattedLog = String.format(
            Locale.US,
            """
            [LOCK_LATENCY]
            target_package=%s
            event_received_ns=%d
            launch_decision_ns=%d
            start_activity_ns=%d
            activity_on_create_ns=%d
            first_frame_ns=%d
            event_to_launch_ms=%.2f
            launch_to_onCreate_ms=%.2f
            onCreate_to_firstFrame_ms=%.2f
            event_to_firstFrame_ms=%.2f
            """.trimIndent(),
            targetPkg,
            t1,
            t2,
            t3,
            t4,
            t5,
            eventToLaunchMs,
            launchToOnCreateMs,
            onCreateToFirstFrameMs,
            eventToFirstFrameMs
        )
        Log.i(TAG, formattedLog)
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "[LOCK_LATENCY_CSV] target=%s,event_to_launch_ms=%.2f,launch_to_onCreate_ms=%.2f,onCreate_to_firstFrame_ms=%.2f,event_to_firstFrame_ms=%.2f",
                targetPkg,
                eventToLaunchMs,
                launchToOnCreateMs,
                onCreateToFirstFrameMs,
                eventToFirstFrameMs
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_SESSION_ID, currentSessionId)
        Log.d(TAG, "onSaveInstanceState (sessionId=$currentSessionId)")
    }

    override fun onPause() {
        Log.d(TAG, "onPause (sessionId=$currentSessionId)")
        super.onPause()
    }

    override fun onStop() {
        Log.d(TAG, "onStop (sessionId=$currentSessionId)")
        super.onStop()
        AppDetectorAccessibilityService.onLockScreenStopped(currentSessionId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy (sessionId=$currentSessionId)")
        logger.info(
            DiagnosticEvent(
                type = DiagnosticEventType.LOCKSCREEN_DESTROYED,
                message = "LockScreenActivity destroyed (sessionId=$currentSessionId)",
                sessionId = currentSessionId
            )
        )
        super.onDestroy()
        AppDetectorAccessibilityService.onLockScreenDestroyed(currentSessionId)
    }

    companion object {
        const val TAG = "LockScreenActivity"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_T1_NS = "extra_t1_ns"
        const val EXTRA_T2_NS = "extra_t2_ns"
        const val EXTRA_T3_NS = "extra_t3_ns"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}

@Composable
fun LockScreenContent(onGoToHome: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity

    fun goToHomeScreen() {
        Log.d(LockScreenActivity.TAG, "[EXIT] goToHomeScreen được gọi (BackHandler hoặc Button) -> Gửi intent CATEGORY_HOME, gọi onGoToHome() và finish()")
        onGoToHome()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
        activity?.finish()
    }

    // Intercept Back button to navigate to Home screen, breaking the infinite back loop
    BackHandler {
        Log.d(LockScreenActivity.TAG, "[BACK_PRESSED] Người dùng bấm nút Back cứng/cử chỉ trên LockScreenActivity")
        goToHomeScreen()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lock Badge / Icon Indicator
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFFEBEE), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFC62828), shape = CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.lock_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = stringResource(id = R.string.lock_screen_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(20.dp),
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { goToHomeScreen() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(id = R.string.lock_screen_button_home),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
