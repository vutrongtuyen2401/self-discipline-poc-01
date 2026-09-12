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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import kotlinx.coroutines.launch
import java.util.Locale

class LockScreenActivity : ComponentActivity() {

    private val logger: DiagnosticLogger by lazy {
        DiagnosticLoggerProvider.getLogger()
    }

    private var currentSessionId: Long = 0L
    private var targetPackageState by mutableStateOf("unknown")
    private var sessionState by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        val t4 = SystemClock.elapsedRealtimeNanos()
        super.onCreate(savedInstanceState)
        currentSessionId = savedInstanceState?.getLong(EXTRA_SESSION_ID) ?: intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        sessionState = currentSessionId
        val t1 = intent.getLongExtra(EXTRA_T1_NS, 0L)
        val t2 = intent.getLongExtra(EXTRA_T2_NS, 0L)
        val t3 = intent.getLongExtra(EXTRA_T3_NS, 0L)
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "unknown"
        targetPackageState = targetPkg

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
                        targetPackage = targetPackageState,
                        sessionId = sessionState,
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
        sessionState = currentSessionId
        val t1 = intent.getLongExtra(EXTRA_T1_NS, 0L)
        val t2 = intent.getLongExtra(EXTRA_T2_NS, 0L)
        val t3 = intent.getLongExtra(EXTRA_T3_NS, 0L)
        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: "unknown"
        targetPackageState = targetPkg

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
        if (!isChangingConfigurations) {
            finish()
        }
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

data class SystemPanelUiState(
    val appDisplayName: String = "",
    val packageName: String = "",
    val totalRequiredTasks: Int = 0,
    val completedCount: Int = 0,
    val requiredCount: Int = 0,
    val pendingTasks: List<CanonicalTask> = emptyList(),
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = true
)

@Composable
fun LockScreenContent(
    targetPackage: String = "unknown",
    sessionId: Long = 0L,
    onGoToHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val taskRepo = remember { CanonicalRepositoryProvider.getTaskRepository(context) }
    val vaultRepo = remember { CanonicalRepositoryProvider.getVaultRepository(context) }
    val cycleRepo = remember { CanonicalRepositoryProvider.getCycleRepository(context) }
    val lockEvaluator = remember { CanonicalRepositoryProvider.getLockEvaluator(context) }
    val completeTaskUseCase = remember { CompleteTaskUseCase(taskRepo, lockEvaluator) }

    var uiState by remember { mutableStateOf(SystemPanelUiState(packageName = targetPackage)) }
    var taskToConfirm by remember { mutableStateOf<CanonicalTask?>(null) }

    fun refreshState() {
        coroutineScope.launch {
            val app = vaultRepo.getVaultApp(targetPackage)
            val appName = app?.displayName ?: targetPackage
            val evaluation = lockEvaluator.evaluateApp(targetPackage)
            val linkedTasks = taskRepo.getTasksLinkedToApp(targetPackage).filter { !it.isArchived }
            val currentCycle = cycleRepo.getCurrentCycle()

            val cycleStates = linkedTasks.map { task ->
                task to (taskRepo.getCycleState(task.id, currentCycle.cycleId) ?: TaskCycleState(task.id, currentCycle.cycleId))
            }
            val completed = cycleStates.filter { it.second.isCompleted }.map { it.first }
            val pending = cycleStates.filter { !it.second.isCompleted }.map { it.first }
            val n = linkedTasks.size
            val required = CanonicalLockPolicy.calculateRequiredCompletions(n)

            uiState = SystemPanelUiState(
                appDisplayName = appName,
                packageName = targetPackage,
                totalRequiredTasks = n,
                completedCount = completed.size,
                requiredCount = required,
                pendingTasks = pending,
                isUnlocked = !evaluation.isLocked,
                isLoading = false
            )
        }
    }

    LaunchedEffect(targetPackage, sessionId) {
        refreshState()
    }

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

    fun launchUnlockedApp() {
        Log.d(LockScreenActivity.TAG, "[ENTER_UNLOCKED_APP] Mở app đích $targetPackage sau khi giải trừ phong ấn")
        onGoToHome()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
        activity?.finish()
    }

    // Intercept Back button to navigate to Home screen, breaking the infinite back loop
    BackHandler {
        Log.d(LockScreenActivity.TAG, "[BACK_PRESSED] Người dùng bấm nút Back cứng/cử chỉ trên LockScreenActivity")
        goToHomeScreen()
    }

    // Confirmation Dialog khi Ký chủ self-reports hoàn thành nhiệm vụ theo SSOT
    if (taskToConfirm != null) {
        val task = taskToConfirm!!
        AlertDialog(
            onDismissRequest = { taskToConfirm = null },
            title = {
                Text(
                    text = "Xác Nhận Hoàn Thành",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Ký chủ xác nhận đã hoàn thành nhiệm vụ [${task.title}]?\n\nHệ thống tin tưởng sự tự giác và trung thực của Ký chủ để phá bỏ phong ấn.",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val currentCycle = cycleRepo.getCurrentCycle()
                            completeTaskUseCase(task.id, currentCycle.cycleId)
                            taskToConfirm = null
                            refreshState()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Xác nhận", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToConfirm = null }) {
                    Text("Hủy")
                }
            }
        )
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
            // Lock Badge Indicator
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        if (uiState.isUnlocked) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (uiState.isUnlocked) Color(0xFF2E7D32) else Color(0xFFC62828),
                            shape = CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (uiState.isUnlocked) "PHONG ẤN ĐÃ GIẢI TRỪ" else "BẢNG HỆ THỐNG — PHONG ẤN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (uiState.isUnlocked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ứng dụng: ${uiState.appDisplayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isUnlocked) {
                // Trạng thái đã mở khóa
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎉 VIÊN MÃN CÔNG ĐỨC!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ký chủ đã hoàn thành đủ số nhiệm vụ tu luyện yêu cầu (${uiState.completedCount}/${uiState.requiredCount}). Phong ấn đã được dỡ bỏ.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { launchUnlockedApp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Text(
                        text = "Vào Ứng Dụng",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            } else {
                // Trạng thái đang bị khóa - Hiển thị nhiệm vụ cần hoàn thành theo SSOT
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.totalRequiredTasks > 0) {
                            Text(
                                text = "Tiến trình mở khóa: ${uiState.completedCount}/${uiState.requiredCount} nhiệm vụ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val nextTask = uiState.pendingTasks.firstOrNull()
                            if (nextTask != null) {
                                Text(
                                    text = "Nhiệm vụ phong ấn yêu cầu:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = nextTask.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { taskToConfirm = nextTask },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFC62828)
                                    )
                                ) {
                                    Text(
                                        text = "Ta đã hoàn thành",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(id = R.string.lock_screen_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { goToHomeScreen() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.lock_screen_button_home),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
