package com.example.selfdisciplinepoc01.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CanonicalMutationSyncManager
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UpdateTaskRewardLinkageUseCase
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider
import com.example.selfdisciplinepoc01.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Authoritative Canonical Test Seam Receiver.
 * Receives test commands via 'am broadcast -a com.example.selfdisciplinepoc01.ACTION_CANONICAL_TEST'
 * completely independent of UI activity stack / taskAffinity constraints.
 */
class CanonicalTestSeamReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ACTION_CANONICAL_TEST) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        val launchLockScreenPkg = intent.getStringExtra("EXTRA_CANONICAL_LAUNCH_LOCK_SCREEN")
        val triggerBoundary = intent.getBooleanExtra("EXTRA_CANONICAL_TRIGGER_BOUNDARY", false)
        val clearCanonicalData = intent.getBooleanExtra("EXTRA_CANONICAL_CLEAR_DATA", false)
        val vaultAddPkg = intent.getStringExtra("EXTRA_CANONICAL_VAULT_ADD_PKG")
        val vaultRemovePkg = intent.getStringExtra("EXTRA_CANONICAL_VAULT_REMOVE_PKG")
        val createTaskReward = intent.getStringExtra("EXTRA_CANONICAL_CREATE_TASK_WITH_REWARD")
        val updateReward = intent.getStringExtra("EXTRA_CANONICAL_UPDATE_REWARD_TIMING")
        val linkTaskApp = intent.getStringExtra("EXTRA_CANONICAL_LINK_TASK_APP")
        val unlinkTaskApp = intent.getStringExtra("EXTRA_CANONICAL_UNLINK_TASK_APP")
        val deleteTaskPkg = intent.getStringExtra("EXTRA_CANONICAL_DELETE_TASK")
        val completeTaskId = intent.getStringExtra("EXTRA_CANONICAL_COMPLETE_TASK")
        val queryVaultApp = intent.getStringExtra("EXTRA_CANONICAL_QUERY_VAULT_APP")
        val queryDeleteEligibilityPkg = intent.getStringExtra("EXTRA_CANONICAL_QUERY_DELETE_ELIGIBILITY")

        if (launchLockScreenPkg != null) {
            val lockIntent = Intent(appContext, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(LockScreenActivity.EXTRA_TARGET_PACKAGE, launchLockScreenPkg)
                putExtra(LockScreenActivity.EXTRA_SESSION_ID, System.currentTimeMillis())
            }
            appContext.startActivity(lockIntent)
            Log.i(TAG, "[LAUNCH_LOCK_SCREEN] Launched LockScreenActivity for $launchLockScreenPkg")
        }

        if (triggerBoundary) {
            com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager.onCycleBoundaryReached(appContext)
            Log.i(TAG, "[CYCLE_BOUNDARY_TRIGGERED] onCycleBoundaryReached executed manually from broadcast")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val vaultRepo = CanonicalRepositoryProvider.getVaultRepository(appContext)
                val taskRepo = CanonicalRepositoryProvider.getTaskRepository(appContext)
                val lockEvaluator = CanonicalRepositoryProvider.getLockEvaluator(appContext)
                val adapter = TaskAppEnforcementAdapterProvider.getAdapter(appContext)

                val addVaultUseCase = AddVaultAppUseCase(vaultRepo, lockEvaluator)
                val removeVaultUseCase = RemoveVaultAppUseCase(vaultRepo, taskRepo)
                val createTaskUseCase = CreateTaskUseCase(taskRepo, lockEvaluator)
                val updateRewardUseCase = UpdateTaskRewardLinkageUseCase(taskRepo, lockEvaluator)
                val deleteTaskUseCase = DeleteTaskUseCase(taskRepo, lockEvaluator)

                if (clearCanonicalData) {
                    val db = AppDatabase.getInstance(appContext)
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
                    Log.i(TAG, "[CLEAR] Đã refresh snapshot và dọn dẹp dữ liệu canonical test.")
                }

                if (vaultAddPkg != null) {
                    val now = Instant.now()
                    val eval = addVaultUseCase(vaultAddPkg, vaultAddPkg, now)
                    adapter.recomputeSnapshot()
                    Log.i(TAG, "[VAULT_ADD] Đã thêm '$vaultAddPkg' vào Vault qua UseCase. Decision=${eval.decision}, totalLinks=${eval.totalLinkedRewardTasks}, required=${eval.requiredCompletions}")
                }

                if (vaultRemovePkg != null) {
                    removeVaultUseCase(vaultRemovePkg)
                    adapter.recomputeSnapshot()
                    val eval = lockEvaluator.evaluateApp(vaultRemovePkg)
                    Log.i(TAG, "[VAULT_REMOVE] Đã gỡ '$vaultRemovePkg' khỏi Vault qua UseCase. Decision=${eval.decision}")
                }

                if (createTaskReward != null) {
                    val parts = createTaskReward.split(Regex("[|,;:@]")).map { it.trim().trim('\'', '"', '\\') }
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
                        createTaskUseCase.execute(
                            id = taskId,
                            title = title,
                            linkedAppPackageNames = listOf(pkg),
                            timing = timing
                        )
                        adapter.recomputeSnapshot()
                        val eval = lockEvaluator.evaluateApp(pkg)
                        Log.i(TAG, "[CREATE_TASK_REWARD] Task '$taskId' link '$pkg' timing=$timing. App eval=${eval.decision}, req=${eval.requiredCompletions}, comp=${eval.completedLinkedRewardTasks}")
                    }
                }

                if (updateReward != null) {
                    val parts = updateReward.split(Regex("[|,;:@]")).map { it.trim().trim('\'', '"', '\\') }
                    if (parts.size >= 3) {
                        val taskId = parts[0]
                        val pkg = parts[1]
                        val timingStr = parts[2].lowercase()
                        val timing = if (timingStr == "next_cycle") {
                            com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.PENDING_NEXT_CYCLE
                        } else {
                            com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                        }
                        updateRewardUseCase.execute(
                            taskId = taskId,
                            selectedPackageNames = if (pkg.isEmpty()) emptyList() else listOf(pkg),
                            timing = timing
                        )
                        adapter.recomputeSnapshot()
                        val eval = if (pkg.isNotEmpty()) lockEvaluator.evaluateApp(pkg) else null
                        Log.i(TAG, "[UPDATE_REWARD_TIMING] Task '$taskId' -> '$pkg' timing=$timing. App eval=${eval?.decision}")
                    }
                }

                if (linkTaskApp != null) {
                    val parts = linkTaskApp.split(Regex("[|,;:@]")).map { it.trim().trim('\'', '"', '\\') }
                    if (parts.size == 2) {
                        val taskId = parts[0]
                        val pkg = parts[1]
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
                        Log.i(TAG, "[LINK_TASK_APP] Đã liên kết Task '$taskId' với '$pkg' (IMMEDIATE). Links=${links.size}, eval=${eval.decision}, syncAction=${syncEval.finalAction}")
                    }
                }

                if (unlinkTaskApp != null) {
                    val parts = unlinkTaskApp.split(Regex("[|,;:@]")).map { it.trim().trim('\'', '"', '\\') }
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
                        Log.i(TAG, "[UNLINK_TASK_APP] Đã hủy liên kết Task '$taskId' khỏi '$pkg' (IMMEDIATE). Còn lại links: ${links.size}, eval=${eval.decision}, syncAction=${syncEval.finalAction}")
                    }
                }

                if (deleteTaskPkg != null) {
                    deleteTaskUseCase(deleteTaskPkg)
                    adapter.recomputeSnapshot()
                    Log.i(TAG, "[DELETE_TASK] Đã xóa task '$deleteTaskPkg' qua DeleteTaskUseCase.")
                }

                if (completeTaskId != null) {
                    val now = Instant.now()
                    val cycleId = com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine.getCurrentCycleId(now, java.time.ZoneId.systemDefault())
                    taskRepo.completeTask(completeTaskId, cycleId, now)
                    adapter.recomputeSnapshot()
                    val linkedApps = taskRepo.getAppsLinkedToTask(completeTaskId)
                    CanonicalMutationSyncManager.notifyMutationCommitted(linkedApps)
                    Log.i(TAG, "[COMPLETE_TASK] Đã complete task '$completeTaskId' trong cycle '$cycleId'. Affected apps=$linkedApps")
                }

                if (queryVaultApp != null) {
                    adapter.recomputeSnapshot()
                    val app = vaultRepo.getVaultApp(queryVaultApp)
                    val links = taskRepo.getTasksLinkedToApp(queryVaultApp)
                    val eval = lockEvaluator.evaluateApp(queryVaultApp)
                    val syncEval = adapter.evaluateSync(queryVaultApp)
                    Log.i(TAG, "[QUERY_VAULT_APP] App '$queryVaultApp': inVault=${app != null}, linksCount=${links.size}, evaluatorDecision=${eval.decision}, syncFinalAction=${syncEval.finalAction}, reason=${syncEval.reason}")
                }

                if (queryDeleteEligibilityPkg != null) {
                    adapter.recomputeSnapshot()
                    val lockEval = lockEvaluator.evaluateApp(queryDeleteEligibilityPkg)
                    val n = lockEval.totalLinkedRewardTasks
                    val k = lockEval.completedLinkedRewardTasks
                    val canDelete = com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(n, k)
                    val required = com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.calculateRequiredForDeletion(n)
                    val syncEval = adapter.evaluateSync(queryDeleteEligibilityPkg)
                    Log.i(TAG, "[QUERY_DELETE_ELIGIBILITY] App '$queryDeleteEligibilityPkg': N=$n, K=$k, requiredForDeletion=$required, canDelete=$canDelete, lockDecision=${lockEval.decision}, syncAction=${syncEval.finalAction}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANONICAL_TEST = "com.example.selfdisciplinepoc01.ACTION_CANONICAL_TEST"
        private const val TAG = "CanonicalTestSeam"
    }
}
