package com.example.selfdisciplinepoc01.ui.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryService
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
class VaultViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CoreDataRepository
    private lateinit var fakeDiscoveryService: FakeInstalledAppDiscoveryService
    private lateinit var viewModel: VaultViewModel

    private class FakeInstalledAppDiscoveryService : InstalledAppDiscoveryService {
        val installedList = mutableListOf(
            DiscoveredApp("com.android.chrome", "Chrome", false),
            DiscoveredApp("com.tencent.tmgp.sgame", "Liên Quân Mobile", false),
            DiscoveredApp("com.android.calculator2", "Calculator", false)
        )

        override suspend fun getDiscoveredApps(vaultPackageNames: Set<String>): List<DiscoveredApp> {
            return installedList.map {
                it.copy(isAlreadyInVault = it.packageName in vaultPackageNames)
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CoreDataRepositoryImpl(database)
        fakeDiscoveryService = FakeInstalledAppDiscoveryService()

        viewModel = VaultViewModel(
            getVaultAppsUseCase = GetVaultAppsUseCase(repository),
            addVaultAppUseCase = AddVaultAppUseCase(repository),
            removeVaultAppUseCase = RemoveVaultAppUseCase(repository),
            discoveryService = fakeDiscoveryService
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_initialState_emptyVault() = runBlocking {
        val state = withTimeout(3000) {
            viewModel.uiState.first { !it.isLoading }
        }
        assertFalse(state.isLoading)
        assertTrue(state.vaultApps.isEmpty())
        assertFalse(state.isAddingApp)
        assertNull(state.appPendingRemoval)
    }

    @Test
    fun testViewModel_openAndDismissAddDialog() = runBlocking {
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        val state = withTimeout(3000) {
            viewModel.uiState.first { it.isAddingApp && !it.isLoadingDiscovered }
        }
        assertTrue(state.isAddingApp)
        assertEquals(3, state.discoveredApps.size)

        viewModel.onDismissAddDialog()
        assertFalse(viewModel.uiState.value.isAddingApp)
    }

    @Test
    fun testViewModel_searchQuery_filtersDiscoveredApps() = runBlocking {
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoadingDiscovered } }

        viewModel.onSearchQueryChanged("chrome")
        assertEquals(1, viewModel.uiState.value.filteredDiscoveredApps.size)
        assertEquals("Chrome", viewModel.uiState.value.filteredDiscoveredApps[0].appName)

        viewModel.onSearchQueryChanged("xyz_not_found")
        assertEquals(0, viewModel.uiState.value.filteredDiscoveredApps.size)
    }

    @Test
    fun testViewModel_addApp_persistsInVault() = runBlocking {
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        val appToAdd = DiscoveredApp("com.android.chrome", "Chrome", false)
        viewModel.onAddApp(appToAdd)

        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.vaultApps.size == 1 }
        }
        assertEquals(1, stateAfter.vaultApps.size)
        assertEquals("com.android.chrome", stateAfter.vaultApps[0].packageName)
        assertEquals("Chrome", stateAfter.vaultApps[0].appName)
        assertNotNull(stateAfter.bannerMessage)
    }

    @Test
    fun testViewModel_removeAppPromptAndConfirm() = runBlocking {
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        val appToAdd = DiscoveredApp("com.android.chrome", "Chrome", false)
        viewModel.onAddApp(appToAdd)
        withTimeout(3000) { viewModel.uiState.first { it.vaultApps.size == 1 } }

        val vaultApp = viewModel.uiState.value.vaultApps[0]
        viewModel.onPromptRemoveApp(vaultApp)
        assertEquals(vaultApp, viewModel.uiState.value.appPendingRemoval)

        viewModel.onConfirmRemoveApp()

        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.vaultApps.isEmpty() && it.appPendingRemoval == null }
        }
        assertTrue(stateAfter.vaultApps.isEmpty())
        assertNull(stateAfter.appPendingRemoval)
    }
}
