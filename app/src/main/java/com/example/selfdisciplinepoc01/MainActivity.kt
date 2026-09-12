package com.example.selfdisciplinepoc01

import android.os.Bundle
import androidx.room.withTransaction
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryProvider
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderHolder
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryServiceImpl
import com.example.selfdisciplinepoc01.ui.missionhall.MissionHallScreen
import com.example.selfdisciplinepoc01.ui.missionhall.MissionHallViewModel
import com.example.selfdisciplinepoc01.ui.vault.VaultScreen
import com.example.selfdisciplinepoc01.ui.vault.VaultViewModel
import com.example.selfdisciplinepoc01.usage.UsageTracker
import com.example.selfdisciplinepoc01.usage.UsageTrackerProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme.colors.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }


    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        handleCanonicalTestSeams(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val addPkg = intent?.getStringExtra("EXTRA_ADD_TARGET")
        val removePkg = intent?.getStringExtra("EXTRA_REMOVE_TARGET")
        val togglePkg = intent?.getStringExtra("EXTRA_TOGGLE_TARGET")
        val setEnabled = intent?.getBooleanExtra("EXTRA_SET_ENABLED", true) ?: true

        val policyPkg = intent?.getStringExtra("EXTRA_POLICY_TARGET")

        // Canonical Test Seams for P0 Real Device Validation
        val testAlarmSec = intent?.getIntExtra("EXTRA_TEST_ALARM_DELAY_SEC", -1) ?: -1
        if (testAlarmSec > 0) {
            com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager.scheduleTestAlarm(applicationContext, testAlarmSec.toLong())
        }

        handleCanonicalTestSeams(intent)

        if (addPkg != null || removePkg != null || togglePkg != null || policyPkg != null) {
            val repo = TargetRepositoryProvider.getRepository(applicationContext)
            lifecycleScope.launch {
                addPkg?.let { repo.add(it) }
                removePkg?.let { repo.remove(it) }
                togglePkg?.let { repo.setEnabled(it, setEnabled) }

                if (policyPkg != null) {
                    val hasSched = intent.hasExtra("EXTRA_SCHEDULE_ENABLED")
                    val schedEnabled = intent.getBooleanExtra("EXTRA_SCHEDULE_ENABLED", false)
                    val sH = intent.getIntExtra("EXTRA_START_HOUR", 9)
                    val sM = intent.getIntExtra("EXTRA_START_MINUTE", 0)
                    val eH = intent.getIntExtra("EXTRA_END_HOUR", 17)
                    val eM = intent.getIntExtra("EXTRA_END_MINUTE", 0)
                    val schedule = if (hasSched) TimeSchedule(schedEnabled, sH, sM, eH, eM) else null

                    val hasLimit = intent.hasExtra("EXTRA_LIMIT_ENABLED")
                    val limitEnabled = intent.getBooleanExtra("EXTRA_LIMIT_ENABLED", false)
                    val limitMins = intent.getIntExtra("EXTRA_LIMIT_MINUTES", 0)
                    val limitSecs = if (intent.hasExtra("EXTRA_LIMIT_SECONDS")) intent.getIntExtra("EXTRA_LIMIT_SECONDS", 0) else null
                    val timeLimit = if (hasLimit) TimeLimit(limitEnabled, limitMins, limitSecs) else null

                    repo.updatePolicy(policyPkg, schedule, timeLimit)
                    AppDetectorAccessibilityService.onPolicyUpdated(policyPkg)
                }
                finish()
            }
        }
    }

    private fun handleCanonicalTestSeams(intent: android.content.Intent?) {
        val launchLockScreenPkg = intent?.getStringExtra("EXTRA_CANONICAL_LAUNCH_LOCK_SCREEN")
        val triggerBoundary = intent?.getBooleanExtra("EXTRA_CANONICAL_TRIGGER_BOUNDARY", false) ?: false
        val triggerReconcile = intent?.getBooleanExtra("EXTRA_CANONICAL_RECONCILE", false) ?: false

        val clearCanonicalData = intent?.getBooleanExtra("EXTRA_CANONICAL_CLEAR_DATA", false) ?: false
        val vaultAddPkg = intent?.getStringExtra("EXTRA_CANONICAL_VAULT_ADD_PKG")
        val vaultRemovePkg = intent?.getStringExtra("EXTRA_CANONICAL_VAULT_REMOVE_PKG")
        val createTaskReward = intent?.getStringExtra("EXTRA_CANONICAL_CREATE_TASK_WITH_REWARD") // format: "taskId|title|pkg|timing"
        val updateReward = intent?.getStringExtra("EXTRA_CANONICAL_UPDATE_REWARD_TIMING") // format: "taskId|pkg|timing"
        val linkTaskApp = intent?.getStringExtra("EXTRA_CANONICAL_LINK_TASK_APP") // format: "taskId|packageName"
        val unlinkTaskApp = intent?.getStringExtra("EXTRA_CANONICAL_UNLINK_TASK_APP") // format: "taskId|packageName"
        val deleteTaskPkg = intent?.getStringExtra("EXTRA_CANONICAL_DELETE_TASK")
        val queryVaultApp = intent?.getStringExtra("EXTRA_CANONICAL_QUERY_VAULT_APP")
        val addVoucherPkg = intent?.getStringExtra("EXTRA_CANONICAL_ADD_VOUCHER_PKG")
        val seedCycleCompletion = intent?.getStringExtra("EXTRA_CANONICAL_SEED_CYCLE_COMPLETION") // format: "taskId|cycleId"
        val seedLockPkg = intent?.getStringExtra("EXTRA_CANONICAL_SEED_LOCK_APP")
        val seedUnlockPkg = intent?.getStringExtra("EXTRA_CANONICAL_SEED_UNLOCK_APP")
        val completeTaskId = intent?.getStringExtra("EXTRA_CANONICAL_COMPLETE_TASK")
        val queryDeleteEligibilityPkg = intent?.getStringExtra("EXTRA_CANONICAL_QUERY_DELETE_ELIGIBILITY")
        val evalPkg = intent?.getStringExtra("EXTRA_EVALUATE_CANONICAL")

        if (launchLockScreenPkg != null) {
            val lockIntent = android.content.Intent(this, LockScreenActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockScreenActivity.EXTRA_TARGET_PACKAGE, launchLockScreenPkg)
                putExtra(LockScreenActivity.EXTRA_SESSION_ID, System.currentTimeMillis())
            }
            startActivity(lockIntent)
            android.util.Log.i("CanonicalTestSeam", "[LAUNCH_LOCK_SCREEN] Launched LockScreenActivity for $launchLockScreenPkg")
        }

        if (triggerBoundary) {
            com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager.onCycleBoundaryReached(applicationContext)
            android.util.Log.i("CanonicalTestSeam", "[CYCLE_BOUNDARY_TRIGGERED] onCycleBoundaryReached executed manually from intent")
        }

        if (triggerReconcile) {
            com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager.reconcileCycleOnStartup(applicationContext)
            android.util.Log.i("CanonicalTestSeam", "[CYCLE_RECONCILE_TRIGGERED] reconcileCycleOnStartup executed manually from intent")
        }

        lifecycleScope.launch {
            val vaultRepo = com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider.getVaultRepository(applicationContext)
            val taskRepo = com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider.getTaskRepository(applicationContext)
            val lockEvaluator = com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider.getLockEvaluator(applicationContext)
            val adapter = com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider.getAdapter(applicationContext)

            val addVaultUseCase = com.example.selfdisciplinepoc01.domain.canonical.usecase.AddVaultAppUseCase(vaultRepo, lockEvaluator)
            val removeVaultUseCase = com.example.selfdisciplinepoc01.domain.canonical.usecase.RemoveVaultAppUseCase(vaultRepo, taskRepo)
            val createTaskUseCase = com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase(taskRepo, lockEvaluator)
            val updateRewardUseCase = com.example.selfdisciplinepoc01.domain.canonical.usecase.UpdateTaskRewardLinkageUseCase(taskRepo, lockEvaluator)
            val deleteTaskUseCase = com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase(taskRepo, lockEvaluator)

            if (clearCanonicalData) {
                val db = com.example.selfdisciplinepoc01.data.database.AppDatabase.getInstance(applicationContext)
                db.withTransaction {
                    val allApps = db.canonicalVaultDao().getAllApps()
                    for (app in allApps) {
                        db.canonicalVaultDao().deleteApp(app.packageName)
                        db.taskRewardLinkDao().deleteByApp(app.packageName)
                    }
                    val allTasks = db.canonicalTaskDao().getAllActiveTasks()
                    for (task in allTasks) {
                        db.canonicalTaskDao().archiveTask(task.id)
                        db.taskRewardLinkDao().deleteByTask(task.id)
                    }
                }
                adapter.recomputeSnapshot()
                android.util.Log.i("CanonicalTestSeam", "[CLEAR] Đã refresh snapshot và dọn dẹp dữ liệu canonical test.")
            }

            if (vaultAddPkg != null) {
                val now = java.time.Instant.now()
                val eval = addVaultUseCase(vaultAddPkg, vaultAddPkg, now)
                adapter.recomputeSnapshot()
                android.util.Log.i("CanonicalTestSeam", "[VAULT_ADD] Đã thêm '$vaultAddPkg' vào Vault qua UseCase. Decision=${eval.decision}, totalLinks=${eval.totalLinkedRewardTasks}, required=${eval.requiredCompletions}")
            }

            if (vaultRemovePkg != null) {
                removeVaultUseCase(vaultRemovePkg)
                adapter.recomputeSnapshot()
                val eval = lockEvaluator.evaluateApp(vaultRemovePkg)
                android.util.Log.i("CanonicalTestSeam", "[VAULT_REMOVE] Đã gỡ '$vaultRemovePkg' khỏi Vault qua UseCase. Decision=${eval.decision}")
            }

            if (createTaskReward != null) {
                val parts = createTaskReward.split(Regex("[|,;]")).map { it.trim().trim('\'', '"', '\\') }
                if (parts.size >= 4) {
                    val taskId = parts[0]
                    val title = parts[1]
                    val pkg = parts[2]
                    val timingStr = parts[3].lowercase()
                    val timing = if (timingStr == "next_cycle") {
                        com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.PENDING_NEXT_CYCLE
                    } else {
                        com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                    }
                    val res = createTaskUseCase.execute(
                        id = taskId,
                        title = title,
                        linkedAppPackageNames = listOf(pkg),
                        timing = timing
                    )
                    adapter.recomputeSnapshot()
                    val eval = lockEvaluator.evaluateApp(pkg)
                    android.util.Log.i("CanonicalTestSeam", "[CREATE_TASK_REWARD] Task '$taskId' link '$pkg' timing=$timing. App eval=${eval.decision}, req=${eval.requiredCompletions}, comp=${eval.completedLinkedRewardTasks}")
                }
            }

            if (updateReward != null) {
                val parts = updateReward.split(Regex("[|,;]")).map { it.trim().trim('\'', '"', '\\') }
                if (parts.size >= 3) {
                    val taskId = parts[0]
                    val pkg = parts[1]
                    val timingStr = parts[2].lowercase()
                    val timing = if (timingStr == "next_cycle") {
                        com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.PENDING_NEXT_CYCLE
                    } else {
                        com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                    }
                    val res = updateRewardUseCase.execute(
                        taskId = taskId,
                        selectedPackageNames = if (pkg.isEmpty()) emptyList() else listOf(pkg),
                        timing = timing
                    )
                    adapter.recomputeSnapshot()
                    val eval = if (pkg.isNotEmpty()) lockEvaluator.evaluateApp(pkg) else null
                    android.util.Log.i("CanonicalTestSeam", "[UPDATE_REWARD_TIMING] Task '$taskId' -> '$pkg' timing=$timing. App eval=${eval?.decision}")
                }
            }

            if (linkTaskApp != null) {
                val parts = linkTaskApp.split(Regex("[|,;]")).map { it.trim().trim('\'', '"', '\\') }
                if (parts.size == 2) {
                    val taskId = parts[0]
                    val pkg = parts[1]
                    // Ensure task exists
                    if (taskRepo.getTask(taskId) == null) {
                        createTaskUseCase.execute(
                            id = taskId,
                            title = "Task $taskId",
                            linkedAppPackageNames = listOf(pkg),
                            timing = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                        )
                    } else {
                        val currentLinks = taskRepo.getAppsLinkedToTask(taskId)
                        val newLinks = (currentLinks + pkg).distinct()
                        updateRewardUseCase.execute(
                            taskId = taskId,
                            selectedPackageNames = newLinks,
                            timing = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                        )
                    }
                    adapter.recomputeSnapshot()
                    val links = taskRepo.getTasksLinkedToApp(pkg)
                    val eval = lockEvaluator.evaluateApp(pkg)
                    val syncEval = adapter.evaluateSync(pkg)
                    android.util.Log.i("CanonicalTestSeam", "[LINK_TASK_APP] Đã liên kết Task '$taskId' với '$pkg' (IMMEDIATE). Links=${links.size}, eval=${eval.decision}, syncAction=${syncEval.finalAction}")
                }
            }

            if (unlinkTaskApp != null) {
                val parts = unlinkTaskApp.split(Regex("[|,;]")).map { it.trim().trim('\'', '"', '\\') }
                if (parts.size == 2) {
                    val taskId = parts[0]
                    val pkg = parts[1]
                    val currentLinks = taskRepo.getAppsLinkedToTask(taskId)
                    val newLinks = currentLinks.filter { it != pkg }
                    updateRewardUseCase.execute(
                        taskId = taskId,
                        selectedPackageNames = newLinks,
                        timing = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                    )
                    adapter.recomputeSnapshot()
                    val links = taskRepo.getTasksLinkedToApp(pkg)
                    val eval = lockEvaluator.evaluateApp(pkg)
                    val syncEval = adapter.evaluateSync(pkg)
                    android.util.Log.i("CanonicalTestSeam", "[UNLINK_TASK_APP] Đã hủy liên kết Task '$taskId' khỏi '$pkg' (IMMEDIATE). Còn lại links: ${links.size}, eval=${eval.decision}, syncAction=${syncEval.finalAction}")
                }
            }

            if (deleteTaskPkg != null) {
                deleteTaskUseCase(deleteTaskPkg)
                adapter.recomputeSnapshot()
                android.util.Log.i("CanonicalTestSeam", "[DELETE_TASK] Đã xóa task '$deleteTaskPkg' qua DeleteTaskUseCase.")
            }

            if (seedCycleCompletion != null) {
                val parts = seedCycleCompletion.split("|")
                if (parts.size == 2) {
                    val taskId = parts[0].trim()
                    val cycleIdStr = parts[1].trim()
                    val now = java.time.Instant.now()
                    taskRepo.completeTask(taskId, com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId(cycleIdStr), now)
                    adapter.recomputeSnapshot()
                    android.util.Log.i("CanonicalTestSeam", "[SEED_CYCLE_COMPLETION] Đã đánh dấu hoàn thành Task '$taskId' trong chu kỳ '$cycleIdStr'")
                }
            }

            if (addVoucherPkg != null) {
                val now = java.time.Instant.now()
                val voucher = com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect(
                    voucherId = "test_voucher_${System.currentTimeMillis()}",
                    voucherName = "Lệnh Bài Miễn Phong Ấn",
                    targetPackageName = if (addVoucherPkg == "*") null else addVoucherPkg,
                    effectiveFrom = now.minusSeconds(60),
                    effectiveUntil = now.plusSeconds(3600)
                )
                (vaultRepo as? com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalVaultRepositoryImpl)?.saveVoucher(voucher)
                adapter.recomputeSnapshot()
                android.util.Log.i("CanonicalTestSeam", "[VOUCHER_ADD] Đã thêm Voucher hiệu lực cho '$addVoucherPkg'")
            }

            if (seedLockPkg != null) {
                val now = java.time.Instant.now()
                addVaultUseCase(seedLockPkg, "Test Vault App", now)
                val taskId = "test_task_${seedLockPkg.replace('.', '_')}"
                val cycleId = com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine.getCurrentCycleId(now, java.time.ZoneId.systemDefault())
                createTaskUseCase.execute(
                    id = taskId,
                    title = "Nhiệm vụ phong ấn test cho $seedLockPkg",
                    linkedAppPackageNames = listOf(seedLockPkg),
                    timing = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                )
                taskRepo.undoTaskCompletion(taskId, cycleId)
                adapter.recomputeSnapshot()
                val eval = adapter.evaluateSync(seedLockPkg)
                android.util.Log.i("CanonicalTestSeam", "[SEED_LOCKED] Đã seed Vault App '$seedLockPkg' có 1 task chưa hoàn thành (N=1, K=0). Kết quả đánh giá: finalAction=${eval.finalAction}, reason=${eval.reason}")
            }

            if (seedUnlockPkg != null) {
                val now = java.time.Instant.now()
                val cycleId = com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine.getCurrentCycleId(now, java.time.ZoneId.systemDefault())
                val taskId = "test_task_${seedUnlockPkg.replace('.', '_')}"
                taskRepo.completeTask(taskId, cycleId, now)
                adapter.recomputeSnapshot()
                val eval = adapter.evaluateSync(seedUnlockPkg)
                android.util.Log.i("CanonicalTestSeam", "[SEED_UNLOCKED] Đã hoàn thành task cho '$seedUnlockPkg' trong chu kỳ $cycleId (N=1, K=1). Kết quả đánh giá: finalAction=${eval.finalAction}, reason=${eval.reason}")
            }

            if (completeTaskId != null) {
                val now = java.time.Instant.now()
                val cycleId = com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine.getCurrentCycleId(now, java.time.ZoneId.systemDefault())
                taskRepo.completeTask(completeTaskId, cycleId, now)
                adapter.recomputeSnapshot()
                val linkedApps = taskRepo.getAppsLinkedToTask(completeTaskId)
                com.example.selfdisciplinepoc01.domain.canonical.usecase.CanonicalMutationSyncManager.notifyMutationCommitted(linkedApps)
                android.util.Log.i("CanonicalTestSeam", "[COMPLETE_TASK] Đã complete task '$completeTaskId' trong cycle '$cycleId'. Affected apps=$linkedApps")
            }

            if (queryDeleteEligibilityPkg != null) {
                adapter.recomputeSnapshot()
                val lockEval = lockEvaluator.evaluateApp(queryDeleteEligibilityPkg)
                val n = lockEval.totalLinkedRewardTasks
                val k = lockEval.completedLinkedRewardTasks
                val canDelete = com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(n, k)
                val required = com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.calculateRequiredForDeletion(n)
                val syncEval = adapter.evaluateSync(queryDeleteEligibilityPkg)
                android.util.Log.i("CanonicalTestSeam", "[QUERY_DELETE_ELIGIBILITY] App '$queryDeleteEligibilityPkg': N=$n, K=$k, requiredForDeletion=$required, canDelete=$canDelete, lockDecision=${lockEval.decision}, syncAction=${syncEval.finalAction}")
            }

            if (evalPkg != null) {
                adapter.recomputeSnapshot()
                val eval = adapter.evaluateSync(evalPkg)
                android.util.Log.i("CanonicalTestSeam", "[EVALUATE] Package '$evalPkg': finalAction=${eval.finalAction}, isVaultApp=${eval.isVaultApp}, reason=${eval.reason}")
            }

            if (queryVaultApp != null) {
                adapter.recomputeSnapshot()
                val app = vaultRepo.getVaultApp(queryVaultApp)
                val links = taskRepo.getTasksLinkedToApp(queryVaultApp)
                val eval = lockEvaluator.evaluateApp(queryVaultApp)
                val syncEval = adapter.evaluateSync(queryVaultApp)
                android.util.Log.i("CanonicalTestSeam", "[QUERY_VAULT_APP] App '$queryVaultApp': inVault=${app != null}, linksCount=${links.size}, evaluatorDecision=${eval.decision}, syncFinalAction=${syncEval.finalAction}, reason=${syncEval.reason}")
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val repository = remember { CoreDataRepositoryProvider.getRepository(context) }
    val businessDayProvider = remember { BusinessDayProviderHolder.instance }
    val discoveryService = remember { InstalledAppDiscoveryServiceImpl(context) }
    val enforcementAdapter = remember {
        com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider.getAdapter(context)
    }
    val missionHallViewModel = remember {
        MissionHallViewModel.provideFactory(context, enforcementAdapter)
            .create(MissionHallViewModel::class.java)
    }
    val vaultViewModel = remember {
        VaultViewModel.provideFactory(context, enforcementAdapter)
            .create(VaultViewModel::class.java)
    }

    val cultivationColors = com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme.colors

    Scaffold(
        containerColor = cultivationColors.background,
        bottomBar = {
            NavigationBar(
                containerColor = cultivationColors.surface,
                contentColor = cultivationColors.textPrimary
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        missionHallViewModel.refresh()
                    },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = cultivationColors.celestialGold,
                        selectedTextColor = cultivationColors.celestialGold,
                        unselectedIconColor = cultivationColors.textMuted,
                        unselectedTextColor = cultivationColors.textMuted,
                        indicatorColor = cultivationColors.celestialGoldMuted
                    ),
                    label = { Text("Nhiệm Vụ Đường", fontWeight = FontWeight.SemiBold) },
                    icon = { Text("📜", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        vaultViewModel.refreshEnforcement()
                    },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = cultivationColors.spiritTeal,
                        selectedTextColor = cultivationColors.spiritTeal,
                        unselectedIconColor = cultivationColors.textMuted,
                        unselectedTextColor = cultivationColors.textMuted,
                        indicatorColor = cultivationColors.spiritTealMuted
                    ),
                    label = { Text("Bảo Khố", fontWeight = FontWeight.SemiBold) },
                    icon = { Text("🏛️", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = cultivationColors.celestialGold,
                        selectedTextColor = cultivationColors.celestialGold,
                        unselectedIconColor = cultivationColors.textMuted,
                        unselectedTextColor = cultivationColors.textMuted,
                        indicatorColor = cultivationColors.celestialGoldMuted
                    ),
                    label = { Text("Quản Trị Thực Thi", fontWeight = FontWeight.SemiBold) },
                    icon = { Text("🛡️", fontSize = 18.sp) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> MissionHallScreen(viewModel = missionHallViewModel)
                1 -> VaultScreen(viewModel = vaultViewModel)
                2 -> ForegroundDetectorScreen()
            }
        }
    }
}

@Composable
fun ForegroundDetectorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val targetRepository = remember { TargetRepositoryProvider.getRepository(context) }
    val usageTracker = remember { UsageTrackerProvider.getTracker(context) }
    val lockedApps by targetRepository.getLockedPackages().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var isServiceEnabled by remember {
        mutableStateOf(
            AccessibilityUtil.isAccessibilityServiceEnabled(
                context,
                AppDetectorAccessibilityService::class.java
            )
        )
    }

    // Refresh state whenever activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = AccessibilityUtil.isAccessibilityServiceEnabled(
                    context,
                    AppDetectorAccessibilityService::class.java
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "SelfDisciplinePoc01",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Phase 09-A — Real-Time Daily Limit Enforcement",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isServiceEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceEnabled) "Accessibility Service: ENABLED" else "Accessibility Service: DISABLED",
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceEnabled) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isServiceEnabled)
                            "Accessibility detector is running, tracking usage, and enforcing policy locks."
                        else
                            "To enable locking on target apps, activate 'SelfDisciplinePoc01 Foreground App Detector' in Accessibility Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF37474F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Settings Action Button
            Button(
                onClick = { AccessibilityUtil.openAccessibilitySettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Open Accessibility Settings",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Target Management Section
            TargetManagementCard(
                lockedApps = lockedApps,
                usageTracker = usageTracker,
                onAdd = { pkg ->
                    coroutineScope.launch {
                        targetRepository.add(pkg)
                    }
                },
                onRemove = { pkg ->
                    coroutineScope.launch {
                        targetRepository.remove(pkg)
                    }
                },
                onSetEnabled = { pkg, enabled ->
                    coroutineScope.launch {
                        targetRepository.setEnabled(pkg, enabled)
                    }
                },
                onUpdatePolicy = { pkg, schedule, timeLimit ->
                    coroutineScope.launch {
                        targetRepository.updatePolicy(pkg, schedule, timeLimit)
                        AppDetectorAccessibilityService.onPolicyUpdated(pkg)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Verification & Instructions Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Phase 08 Policy Rules",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Default targets with no schedule/limit remain locked 24/7.\n" +
                                "2. Schedule Lock enforces locks during configured intervals (e.g. 09:00-17:00 or 22:00-07:00).\n" +
                                "3. Daily Usage Limits track usage via monotonic elapsed time and lock when reached.\n" +
                                "4. Locking and allowed transitions preserve Frozen Core invariants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TargetManagementCard(
    lockedApps: List<LockedApp>,
    usageTracker: UsageTracker,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onUpdatePolicy: (String, TimeSchedule?, TimeLimit?) -> Unit
) {
    var inputPackage by remember { mutableStateOf("") }
    var selectedAppForPolicy by remember { mutableStateOf<LockedApp?>(null) }

    // Dialog for editing Schedule and Time Limit
    if (selectedAppForPolicy != null) {
        PolicyConfigDialog(
            app = selectedAppForPolicy!!,
            onDismiss = { selectedAppForPolicy = null },
            onSave = { sched, limit ->
                onUpdatePolicy(selectedAppForPolicy!!.packageName, sched, limit)
                selectedAppForPolicy = null
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Policy Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${lockedApps.count { it.enabled }} active / ${lockedApps.size} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add Target Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPackage,
                    onValueChange = { inputPackage = it },
                    placeholder = { Text("e.g. com.android.bbkcalculator", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val trimmed = inputPackage.trim()
                        if (trimmed.isNotEmpty()) {
                            onAdd(trimmed)
                            inputPackage = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Preset Suggestions
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickChip(label = "Chrome", pkg = "com.android.chrome") { inputPackage = it }
                QuickChip(label = "Calculator", pkg = "com.android.bbkcalculator") { inputPackage = it }
                QuickChip(label = "Settings", pkg = "com.android.settings") { inputPackage = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of Targets
            if (lockedApps.isEmpty()) {
                Text(
                    text = "No targets configured. Add a package name above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lockedApps.forEach { app ->
                        TargetAppRow(
                            app = app,
                            usageTracker = usageTracker,
                            onToggle = { onSetEnabled(app.packageName, it) },
                            onDelete = { onRemove(app.packageName) },
                            onConfigurePolicy = { selectedAppForPolicy = app }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickChip(label: String, pkg: String, onSelect: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.clickable { onSelect(pkg) }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TargetAppRow(
    app: LockedApp,
    usageTracker: UsageTracker,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onConfigurePolicy: () -> Unit
) {
    // Poll usage every 1 second while UI is composed
    var todayUsageMillis by remember { mutableStateOf(usageTracker.getTodayUsage(app.packageName)) }

    LaunchedEffect(app.packageName) {
        while (isActive) {
            todayUsageMillis = usageTracker.getTodayUsage(app.packageName)
            delay(1000L)
        }
    }

    val usedMinutes = todayUsageMillis / 60_000L
    val usedSeconds = (todayUsageMillis % 60_000L) / 1000L

    val scheduleSummary = if (app.schedule != null && app.schedule.enabled) {
        String.format("Schedule: %02d:%02d–%02d:%02d", app.schedule.startHour, app.schedule.startMinute, app.schedule.endHour, app.schedule.endMinute)
    } else {
        "Schedule: Off"
    }

    val limitSummary = if (app.timeLimit != null && app.timeLimit.enabled) {
        "Limit: ${app.timeLimit.dailyLimitMinutes} min/day"
    } else {
        "Limit: Off"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.enabled) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.packageName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (app.enabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (app.enabled) "Status: ACTIVE" else "Status: DISABLED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (app.enabled) Color(0xFF2E7D32) else Color(0xFF757575)
                    )
                }

                Switch(
                    checked = app.enabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Policy Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$scheduleSummary | $limitSummary",
                        fontSize = 11.sp,
                        color = Color(0xFF37474F)
                    )
                    Text(
                        text = "Used today: ${usedMinutes}m ${usedSeconds}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (app.timeLimit?.enabled == true && todayUsageMillis >= app.timeLimit.dailyLimitMinutes * 60_000L)
                            Color(0xFFC62828)
                        else
                            Color(0xFF1565C0)
                    )
                }

                Row {
                    OutlinedButton(
                        onClick = onConfigurePolicy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Policy", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyConfigDialog(
    app: LockedApp,
    onDismiss: () -> Unit,
    onSave: (TimeSchedule?, TimeLimit?) -> Unit
) {
    var scheduleEnabled by remember { mutableStateOf(app.schedule?.enabled ?: false) }
    var startHour by remember { mutableStateOf(app.schedule?.startHour?.toString() ?: "9") }
    var startMinute by remember { mutableStateOf(app.schedule?.startMinute?.toString() ?: "0") }
    var endHour by remember { mutableStateOf(app.schedule?.endHour?.toString() ?: "17") }
    var endMinute by remember { mutableStateOf(app.schedule?.endMinute?.toString() ?: "0") }

    var limitEnabled by remember { mutableStateOf(app.timeLimit?.enabled ?: false) }
    var dailyLimitMinutes by remember { mutableStateOf(app.timeLimit?.dailyLimitMinutes?.toString() ?: "30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Policy", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Spacer(modifier = Modifier.height(12.dp))

                // Schedule Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                    Text("Enable Schedule Lock", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                if (scheduleEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startHour,
                            onValueChange = { startHour = it },
                            label = { Text("Start H", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startMinute,
                            onValueChange = { startMinute = it },
                            label = { Text("Start M", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = endHour,
                            onValueChange = { endHour = it },
                            label = { Text("End H", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endMinute,
                            onValueChange = { endMinute = it },
                            label = { Text("End M", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Limit Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = limitEnabled, onCheckedChange = { limitEnabled = it })
                    Text("Enable Daily Limit", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                if (limitEnabled) {
                    OutlinedTextField(
                        value = dailyLimitMinutes,
                        onValueChange = { dailyLimitMinutes = it },
                        label = { Text("Daily Limit (Minutes)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val schedule = if (scheduleEnabled) {
                        TimeSchedule(
                            enabled = true,
                            startHour = startHour.toIntOrNull()?.coerceIn(0, 23) ?: 9,
                            startMinute = startMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                            endHour = endHour.toIntOrNull()?.coerceIn(0, 23) ?: 17,
                            endMinute = endMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        )
                    } else null

                    val limit = if (limitEnabled) {
                        TimeLimit(
                            enabled = true,
                            dailyLimitMinutes = dailyLimitMinutes.toIntOrNull()?.coerceAtLeast(0) ?: 30
                        )
                    } else null

                    onSave(schedule, limit)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
