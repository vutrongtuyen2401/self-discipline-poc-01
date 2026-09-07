package com.example.selfdisciplinepoc01.ui.missionhall

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.usecase.ArchiveTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetMissionHallTasksUseCase
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MissionHallViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CoreDataRepository
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)

    private lateinit var getTasksUseCase: GetMissionHallTasksUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var completeTaskUseCase: CompleteTaskUseCase
    private lateinit var archiveTaskUseCase: ArchiveTaskUseCase
    private lateinit var getTaskLinkedAppsUseCase: com.example.selfdisciplinepoc01.domain.usecase.GetTaskLinkedAppsUseCase
    private lateinit var updateTaskLinkedAppsUseCase: com.example.selfdisciplinepoc01.domain.usecase.UpdateTaskLinkedAppsUseCase
    private lateinit var getVaultAppsUseCase: com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase

    private fun createViewModel(): MissionHallViewModel {
        return MissionHallViewModel(
            getTasksUseCase,
            createTaskUseCase,
            completeTaskUseCase,
            archiveTaskUseCase,
            getTaskLinkedAppsUseCase,
            updateTaskLinkedAppsUseCase,
            getVaultAppsUseCase
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CoreDataRepositoryImpl(database)

        getTasksUseCase = GetMissionHallTasksUseCase(repository, businessDayProvider)
        createTaskUseCase = CreateTaskUseCase(repository)
        completeTaskUseCase = CompleteTaskUseCase(repository, businessDayProvider)
        archiveTaskUseCase = ArchiveTaskUseCase(repository)
        getTaskLinkedAppsUseCase = com.example.selfdisciplinepoc01.domain.usecase.GetTaskLinkedAppsUseCase(repository)
        updateTaskLinkedAppsUseCase = com.example.selfdisciplinepoc01.domain.usecase.UpdateTaskLinkedAppsUseCase(repository)
        getVaultAppsUseCase = com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase(repository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_initialState_isEmptyAndNotLoading() = runBlocking {
        val viewModel = createViewModel()
        val state = withTimeout(3000) {
            viewModel.uiState.first { !it.isLoading }
        }
        assertFalse(state.isLoading)
        assertTrue(state.chain.isEmpty)
        assertFalse(state.isAddingTask)
        assertNull(state.inputError)
    }

    @Test
    fun testViewModel_openAndDismissDialog() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        assertTrue(viewModel.uiState.value.isAddingTask)

        viewModel.onInputNameChanged("Tập chống đẩy")
        assertEquals("Tập chống đẩy", viewModel.uiState.value.currentInputName)

        viewModel.onDismissAddDialog()
        assertFalse(viewModel.uiState.value.isAddingTask)
        assertEquals("", viewModel.uiState.value.currentInputName)
    }

    @Test
    fun testViewModel_addTask_emptyName_showsError() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("   ")
        viewModel.onConfirmAddTask()

        val state = withTimeout(3000) {
            viewModel.uiState.first { it.inputError != null }
        }
        assertTrue(state.isAddingTask)
        assertNotNull(state.inputError)
        assertEquals("Tên nhiệm vụ không được để trống", state.inputError)
    }

    @Test
    fun testViewModel_addTask_validName_addsToChain() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Đọc 20 trang sách")
        viewModel.onConfirmAddTask()

        val state = withTimeout(3000) {
            viewModel.uiState.first { !it.isAddingTask && it.chain.totalActiveTasks == 1 }
        }
        assertFalse(state.isAddingTask)
        assertNull(state.inputError)
        assertEquals(1, state.chain.totalActiveTasks)
        assertEquals("Đọc 20 trang sách", state.chain.currentTask?.name)
    }

    @Test
    fun testViewModel_completeTask_updatesChainSequentially() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task 1")
        viewModel.onConfirmAddTask()
        withTimeout(3000) { viewModel.uiState.first { !it.isAddingTask && it.chain.totalActiveTasks == 1 } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task 2")
        viewModel.onConfirmAddTask()
        withTimeout(3000) { viewModel.uiState.first { !it.isAddingTask && it.chain.totalActiveTasks == 2 } }

        val task1Id = viewModel.uiState.value.chain.currentTask!!.id
        assertEquals("Task 1", viewModel.uiState.value.chain.currentTask?.name)

        // Complete Task 1
        viewModel.onCompleteTask(task1Id)
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.chain.completedCountToday == 1 }
        }
        assertEquals(1, stateAfter.chain.completedCountToday)
        assertEquals("Task 2", stateAfter.chain.currentTask?.name)
    }

    @Test
    fun testViewModel_archiveTask_removesFromChain() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task to archive")
        viewModel.onConfirmAddTask()
        withTimeout(3000) { viewModel.uiState.first { !it.isAddingTask && it.chain.totalActiveTasks == 1 } }

        val taskId = viewModel.uiState.value.chain.currentTask!!.id
        viewModel.onArchiveTask(taskId)

        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.chain.totalActiveTasks == 0 }
        }
        assertEquals(0, stateAfter.chain.totalActiveTasks)
        assertNull(stateAfter.chain.currentTask)
    }

    @Test
    fun testViewModel_linkTaskToVaultApps_success() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        // Setup 1 task and 2 apps in Vault
        repository.createTask("Task A")
        repository.addVaultApp("com.android.chrome", "Chrome")
        repository.addVaultApp("com.facebook.katana", "Facebook")
        viewModel.refresh()

        val task = withTimeout(3000) { viewModel.uiState.first { it.chain.currentTask != null } }.chain.currentTask!!

        viewModel.onOpenLinkageDialog(task)
        val dialogState = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage != null && it.availableVaultApps.size == 2 }
        }
        assertEquals(2, dialogState.availableVaultApps.size)
        assertTrue(dialogState.selectedPackageNames.isEmpty())

        // Select Chrome
        viewModel.onToggleAppSelection("com.android.chrome")
        assertTrue(viewModel.uiState.value.selectedPackageNames.contains("com.android.chrome"))

        // Save
        viewModel.onSaveTaskLinkage()
        val stateAfterSave = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage == null && it.taskLinkedAppsMap[task.id]?.size == 1 }
        }
        assertNull(stateAfterSave.taskSelectedForLinkage)
        assertEquals(1, stateAfterSave.taskLinkedAppsMap[task.id]?.size)
        assertEquals("com.android.chrome", stateAfterSave.taskLinkedAppsMap[task.id]?.get(0)?.packageName)
    }

    @Test
    fun testViewModel_linkTaskToMultipleApps_success() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        repository.createTask("Task B")
        repository.addVaultApp("com.app.1", "App 1")
        repository.addVaultApp("com.app.2", "App 2")
        viewModel.refresh()

        val task = withTimeout(3000) { viewModel.uiState.first { it.chain.currentTask != null } }.chain.currentTask!!

        viewModel.onOpenLinkageDialog(task)
        withTimeout(3000) { viewModel.uiState.first { it.taskSelectedForLinkage != null } }

        viewModel.onToggleAppSelection("com.app.1")
        viewModel.onToggleAppSelection("com.app.2")
        assertEquals(2, viewModel.uiState.value.selectedPackageNames.size)

        viewModel.onSaveTaskLinkage()
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage == null && it.taskLinkedAppsMap[task.id]?.size == 2 }
        }
        assertEquals(2, stateAfter.taskLinkedAppsMap[task.id]?.size)
    }

    @Test
    fun testViewModel_unlinkApp_updatesState() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        val taskId = repository.createTask("Task C")
        repository.addVaultApp("com.app.1", "App 1")
        repository.linkTaskToApp(taskId, "com.app.1")
        viewModel.refresh()

        val task = withTimeout(3000) { viewModel.uiState.first { it.chain.currentTask != null } }.chain.currentTask!!
        assertEquals(1, viewModel.uiState.value.taskLinkedAppsMap[task.id]?.size)

        viewModel.onOpenLinkageDialog(task)
        withTimeout(3000) { viewModel.uiState.first { it.selectedPackageNames.contains("com.app.1") } }

        // Deselect
        viewModel.onToggleAppSelection("com.app.1")
        assertTrue(viewModel.uiState.value.selectedPackageNames.isEmpty())

        viewModel.onSaveTaskLinkage()
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage == null && it.taskLinkedAppsMap[task.id]?.isEmpty() == true }
        }
        assertEquals(0, stateAfter.taskLinkedAppsMap[task.id]?.size)
    }
}
