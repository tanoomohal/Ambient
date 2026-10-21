package com.tanoo.ambient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** Background for apps with no TV banner (e.g. phone apps), sampled from the icon's edge. */
    val tint: Int?,
)

object Apps {
    /** Used to seed the home row the first time, in this order, if installed. */
    private val defaults = listOf(
        "com.netflix.mediaclient",
        "com.google.android.youtube.tv",
        "com.apple.atve.androidtv.appletv",
        "com.disney.disneyplus",
        "com.hulu.livingroomplus",
        "com.wbd.stream",
        "com.plexapp.android",
        "com.peacocktv.peacockandroid",
    )

    private val hidden = setOf(
        "com.tanoo.ambient",
        "com.google.android.tvlauncher",
        "com.android.tv.settings",
    )

    /** Every app the user can launch, preferring TV (leanback) entry points. */
    fun loadAll(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        )
        val regular = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val seen = HashSet<String>()
        return (leanback + regular)
            .filter { it.activityInfo.packageName !in hidden && seen.add(it.activityInfo.packageName) }
            .map { ri ->
                val ai = ri.activityInfo
                val iconDrawable = ri.loadIcon(pm)
                AppEntry(
                    packageName = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    banner = (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toImage(320, 180),
                    icon = iconDrawable?.toImage(192, 192),
                    tint = iconDrawable?.let { edgeColor(it) },
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun ensureDefaults(context: Context) {
        val prefs = AmbientApp.prefs
        if (prefs.apps.isNotEmpty()) return
        val installed = defaults.filter { isInstalled(context, it) }
        prefs.setAppList(installed)
    }

    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openSystemSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun isInstalled(context: Context, pkg: String) = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Colour of the icon just inside its edge: the icon's own background (black for Netflix). */
    private fun edgeColor(d: Drawable): Int? = runCatching {
        val bmp = d.toBitmap(96, 96)
        val samples = listOf(bmp.getPixel(48, 6), bmp.getPixel(6, 48), bmp.getPixel(90, 48), bmp.getPixel(48, 90))
            .filter { android.graphics.Color.alpha(it) > 200 }
        samples.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: samples.firstOrNull()
    }.getOrNull()

    private fun Drawable.toImage(w: Int, h: Int): ImageBitmap? = runCatching {
        val iw = intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = intrinsicHeight.takeIf { it > 0 } ?: h
        toBitmap(iw.coerceAtMost(w * 2), ih.coerceAtMost(h * 2)).asImageBitmap()
    }.getOrNull()
}
