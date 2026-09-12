package com.example.selfdisciplinepoc01

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme
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
            CultivationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF060913) // Dark Cosmic Base
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
        // Lưu ý P0.3 / CORR-R04: Không gọi finish() ở đây để bảo toàn session hợp lệ,
        // tránh race condition hoặc tự đóng LockScreenActivity khi màn hình tắt/xoay/mở dialog.
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
    var isCompleting by remember { mutableStateOf(false) }

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
        Log.d(LockScreenActivity.TAG, "[EXIT] goToHomeScreen -> Gửi intent CATEGORY_HOME, gọi onGoToHome() và finish()")
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

    // Chặn phím Back cứng / cử chỉ và điều hướng về Android Home theo SSOT
    BackHandler {
        Log.d(LockScreenActivity.TAG, "[BACK_PRESSED] Người dùng bấm nút Back cứng/cử chỉ trên LockScreenActivity")
        goToHomeScreen()
    }

    // Confirmation Dialog phong cách Tiên hiệp
    if (taskToConfirm != null) {
        val task = taskToConfirm!!
        AlertDialog(
            onDismissRequest = {
                if (!isCompleting) taskToConfirm = null
            },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color(0xFF00E5FF),
            textContentColor = Color(0xFFE2E8F0),
            modifier = Modifier
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "✦", color = Color(0xFF00E5FF), fontSize = 16.sp)
                    Text(
                        text = "XÁC NHẬN CÔNG ĐỨC",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ký chủ xác nhận đã hoàn thành nhiệm vụ:",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x2200E5FF), RoundedCornerShape(6.dp))
                            .border(0.5.dp, Color(0x4400E5FF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "Hệ thống tin tưởng sự trung thực và tự giác của Ký chủ để kích hoạt giải trừ phong ấn linh lực.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCompleting) return@Button
                        isCompleting = true
                        coroutineScope.launch {
                            try {
                                val currentCycle = cycleRepo.getCurrentCycle()
                                completeTaskUseCase(task.id, currentCycle.cycleId)
                                taskToConfirm = null
                                refreshState()
                            } finally {
                                isCompleting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF090D16)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isCompleting) "ĐANG XÁC NHẬN..." else "XÁC NHẬN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isCompleting) taskToConfirm = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text("HỦY", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Background: Dark Cosmic + Cultivation Fog & Sealing Formation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF080D1A),
                        Color(0xFF04060E)
                    ),
                    center = Offset(500f, 900f),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Vẽ Đại Trận Phong Ấn (Ancient Sealing Formation) mờ ảo phía sau Panel
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.42f

            // Vòng tròn ngoài cùng
            drawCircle(
                color = Color(0x1500E5FF),
                radius = baseRadius,
                center = centerOffset,
                style = Stroke(width = 1.5f)
            )

            // Vòng tròn nét đứt thứ hai
            drawCircle(
                color = Color(0x187C4DFF),
                radius = baseRadius * 0.85f,
                center = centerOffset,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            )

            // Vòng tròn trung tâm
            drawCircle(
                color = Color(0x1200E5FF),
                radius = baseRadius * 0.65f,
                center = centerOffset,
                style = Stroke(width = 1f)
            )

            // Các tia định vị trận pháp (Cross runes)
            drawLine(
                color = Color(0x0C00E5FF),
                start = Offset(centerOffset.x, centerOffset.y - baseRadius * 1.1f),
                end = Offset(centerOffset.x, centerOffset.y + baseRadius * 1.1f),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0x0C00E5FF),
                start = Offset(centerOffset.x - baseRadius * 1.1f, centerOffset.y),
                end = Offset(centerOffset.x + baseRadius * 1.1f, centerOffset.y),
                strokeWidth = 1f
            )
        }

        // ============================================================
        // TRỌNG TÂM TUYỆT ĐỐI: BẢNG HỆ THỐNG NHIỆM VỤ HÌNH CHỮ NHẬT Ở GIỮA
        // ============================================================
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = if (uiState.isUnlocked) Color(0xFF10B981) else Color(0xFF00E5FF),
                    ambientColor = Color(0xFF7C4DFF)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF00A0F1D)) // Dark Translucent Obsidian
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = if (uiState.isUnlocked) {
                            listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF059669))
                        } else {
                            listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFF00B0FF))
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- 1. HEADER CỦA BẢNG ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✦",
                        color = if (uiState.isUnlocked) Color(0xFF34D399) else Color(0xFF00E5FF),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isUnlocked) "【 PHONG ẤN ĐÃ GIẢI TRỪ 】" else "【 HỆ THỐNG NHIỆM VỤ 】",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (uiState.isUnlocked) Color(0xFF34D399) else Color(0xFF00E5FF),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "✦",
                        color = if (uiState.isUnlocked) Color(0xFF34D399) else Color(0xFF00E5FF),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (uiState.isUnlocked) "CÔNG ĐỨC VIÊN MÃN — LINH LỰC THÔNG SUỐT" else "BẢO KHỐ PHONG ẤN TRẬN PHÁP",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Dải phân cách phát sáng
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    if (uiState.isUnlocked) Color(0x8834D399) else Color(0x8800E5FF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- 2. PHẦN PHONG ẤN / LOCK STATUS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1A0F172A), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color(0x2238BDF8), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Seal Badge Icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (uiState.isUnlocked) Color(0x2210B981) else Color(0x22F43F5E),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (uiState.isUnlocked) Color(0xFF10B981) else Color(0xFFF43F5E),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isUnlocked) "✓" else "🔒",
                            fontSize = 15.sp,
                            color = if (uiState.isUnlocked) Color(0xFF10B981) else Color(0xFFF43F5E)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isUnlocked) "ỨNG DỤNG ĐÃ ĐƯỢC GIẢI TRỪ" else "ỨNG DỤNG ĐANG BỊ PHONG ẤN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isUnlocked) Color(0xFF34D399) else Color(0xFFF43F5E),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.appDisplayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 3. NỘI DUNG NHIỆM VỤ / TRẠNG THÁI KHÓA ---
                if (uiState.isUnlocked) {
                    // Trạng thái đã mở khóa thành công (Canonical Evaluator xác nhận)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1A10B981), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x4410B981), RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎉 CÔNG ĐỨC VIÊN MÃN!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ký chủ đã hoàn thành đủ số nhiệm vụ tu luyện yêu cầu (${uiState.completedCount}/${uiState.requiredCount}). Phong ấn đã hoàn toàn được dỡ bỏ.",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary Action: Vào Ứng Dụng
                    Button(
                        onClick = { launchUnlockedApp() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color(0xFF04060E)
                        )
                    ) {
                        Text(
                            text = "【 VÀO ỨNG DỤNG 】",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    // Trạng thái đang bị khóa - Hiển thị nhiệm vụ cần hoàn thành theo SSOT
                    val nextTask = uiState.pendingTasks.firstOrNull()

                    if (nextTask != null) {
                        // Khung Nhiệm Vụ Tu Luyện Cần Làm
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x221E293B), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "NHIỆM VỤ YÊU CẦU",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8),
                                        letterSpacing = 0.5.sp
                                    )

                                    // Badge Trạng thái
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0x33F43F5E), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, Color(0x66F43F5E), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "CHƯA HOÀN THÀNH",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDA4AF)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = nextTask.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // --- 4. TIẾN ĐỘ K / N ---
                        if (uiState.totalRequiredTasks > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TIẾN ĐỘ NHIỆM VỤ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "${uiState.completedCount} / ${uiState.requiredCount}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Thanh Progress Bar mảnh
                            val progressRatio = if (uiState.requiredCount > 0) {
                                (uiState.completedCount.toFloat() / uiState.requiredCount).coerceIn(0f, 1f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF1E293B))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = progressRatio)
                                        .fillMaxHeight()
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF00E5FF), Color(0xFF3B82F6))
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // --- 5. PRIMARY ACTION: 【 TA ĐÃ HOÀN THÀNH 】 ---
                        Button(
                            onClick = { taskToConfirm = nextTask },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF060913)
                            )
                        ) {
                            Text(
                                text = "【 TA ĐÃ HOÀN THÀNH 】",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        // Trường hợp không có task liên kết cụ thể
                        Text(
                            text = stringResource(id = R.string.lock_screen_message),
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- 6. SECONDARY ACTION: 【 TRỞ VỀ HOME 】 ---
                OutlinedButton(
                    onClick = { goToHomeScreen() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(Color(0xFF334155), Color(0xFF1E293B))
                        )
                    )
                ) {
                    Text(
                        text = "TRỞ VỀ HOME",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
