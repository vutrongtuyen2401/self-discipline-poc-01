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

    private fun createViewModel(): MissionHallViewModel {
        return MissionHallViewModel(
            getTasksUseCase,
            createTaskUseCase,
            completeTaskUseCase,
            archiveTaskUseCase
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
}
