package com.example.selfdisciplinepoc01.domain.discovery

import android.content.Context
import android.content.Intent
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp

/**
 * Service for discovering launchable installed applications on the device.
 *
 * Utilizes standard Intent.ACTION_MAIN with Intent.CATEGORY_LAUNCHER.
 * Manifest contains <queries> declaration to support Android 11+ and Android 15.
 * Requires NO dangerous permissions.
 */
interface InstalledAppDiscoveryService {
    suspend fun getDiscoveredApps(vaultPackageNames: Set<String>): List<DiscoveredApp>
}

class InstalledAppDiscoveryServiceImpl(
    private val context: Context
) : InstalledAppDiscoveryService {

    override suspend fun getDiscoveredApps(vaultPackageNames: Set<String>): List<DiscoveredApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val currentPackage = context.packageName

        val appMap = mutableMapOf<String, DiscoveredApp>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            // Skip self application
            if (pkg == currentPackage) continue

            if (!appMap.containsKey(pkg)) {
                val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                appMap[pkg] = DiscoveredApp(
                    packageName = pkg,
                    appName = label,
                    isAlreadyInVault = pkg in vaultPackageNames
                )
            }
        }

        return appMap.values.sortedBy { it.appName.lowercase() }
    }
}
