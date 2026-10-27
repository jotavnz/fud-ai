package com.apoorvdarshan.calorietracker.services.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** One health app on the phone and whether its data is reaching Health Connect. */
data class HealthSourceApp(
    val packageName: String,
    val name: String,
    /** True when Health Connect has data from it in the last two weeks. */
    val sharing: Boolean,
    /** Apps that cannot share with Health Connect at all (Huawei Health). */
    val unsupported: Boolean = false
)

/**
 * Finds the health and watch apps installed on this phone: every app that works with Health
 * Connect (they declare its permission-rationale screen), plus known ones that do not
 * (Huawei Health). Pure package-manager reads; nothing leaves the phone.
 */
object HealthSourceApps {
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    const val HUAWEI_HEALTH = "com.huawei.health"

    /** Apps that never write to Health Connect, so the user needs a bridge app. */
    private val unsupported = setOf(HUAWEI_HEALTH)

    /** Health Connect itself and Google's controller are the hub, not sources. */
    private val notSources = setOf(
        HealthConnectManager.HEALTH_CONNECT_PROVIDER_PACKAGE,
        "com.google.android.healthconnect.controller",
        "com.google.android.apps.healthdata"
    )

    fun installed(context: Context, recentOrigins: Set<String>): List<HealthSourceApp> {
        val pm = context.packageManager
        val clients = buildSet {
            listOf(
                Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"),
                Intent("android.intent.action.VIEW_PERMISSION_USAGE").addCategory("android.intent.category.HEALTH_PERMISSIONS")
            ).forEach { intent ->
                runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
                    .forEach { add(it.activityInfo.packageName) }
            }
            unsupported.filter { isInstalled(pm, it) }.forEach { add(it) }
        }
        return clients
            .filter { it !in notSources && !isFudAiHealthOrigin(it, context.packageName) }
            .map { pkg ->
                HealthSourceApp(
                    packageName = pkg,
                    name = healthSourceName(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() },
                    sharing = pkg in recentOrigins,
                    unsupported = pkg in unsupported
                )
            }
            // Apps that need attention first, then by name.
            .sortedWith(compareBy<HealthSourceApp>({ it.sharing }, { it.name.lowercase() }))
    }

    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
}
