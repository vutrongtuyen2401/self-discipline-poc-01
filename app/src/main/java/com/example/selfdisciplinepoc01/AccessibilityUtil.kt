package com.example.selfdisciplinepoc01

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityUtil {

    /**
     * Checks whether the specified AccessibilityService is currently enabled in system settings.
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            ) == 1
        } catch (e: Settings.SettingNotFoundException) {
            false
        }

        if (!accessibilityEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val flattenedName = expectedComponentName.flattenToString()
        val shortFlattenedName = expectedComponentName.flattenToShortString()

        return enabledServices.split(":").any { service ->
            service.equals(flattenedName, ignoreCase = true) ||
                    service.equals(shortFlattenedName, ignoreCase = true)
        }
    }

    /**
     * Opens the Android Accessibility Settings screen where the user can enable the service.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
