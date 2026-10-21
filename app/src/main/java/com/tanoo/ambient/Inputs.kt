package com.tanoo.ambient

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/**
 * An HDMI source. On XGIMI each physical port is a hardware input (".../HW1"), and when
 * HDMI-CEC sees a device on that port the framework adds a child input (".../HDMI120004")
 * whose label is the device's CEC name, e.g. "Apple TV".
 */
data class HdmiSource(
    val id: String,
    val label: String,
    val portLabel: String,
    val connected: Boolean,
    val hasCecDevice: Boolean,
)

object Inputs {
    private const val TAG = "Inputs"

    fun list(context: Context): List<HdmiSource> {
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val all = try { tim.tvInputList } catch (e: Exception) { Log.w(TAG, "tvInputList", e); return emptyList() }
        val hdmi = all.filter { it.type == TvInputInfo.TYPE_HDMI }
        val byId = hdmi.associateBy { it.id }

        fun portName(info: TvInputInfo): String {
            val root = info.parentId?.let { byId[it] } ?: info
            val port = Regex("""Hdmi(\d)""").find(root.serviceInfo.name)?.groupValues?.get(1)
                ?: Regex("""HW(\d+)""").find(root.id)?.groupValues?.get(1)
            return if (port != null) "HDMI $port" else root.loadLabel(context).toString()
        }

        // Prefer the CEC child for a port when present; it carries the device name.
        val parentsWithChildren = hdmi.mapNotNull { it.parentId }.toSet()
        return hdmi.filter { it.parentId != null || it.id !in parentsWithChildren }.map { info ->
            val connected = try {
                tim.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED
            } catch (e: Exception) { true }
            val port = portName(info)
            val cec = info.parentId != null
            val label = if (cec) info.loadLabel(context).toString().ifBlank { port } else port
            HdmiSource(info.id, label, port, connected, cec)
        }
    }

    /** The source the launcher treats as "the" HDMI input: the user's pick, else the first CEC device. */
    fun preferred(context: Context): HdmiSource? {
        val sources = list(context)
        val chosen = AmbientApp.prefs.hdmiInputId
        return sources.firstOrNull { it.id == chosen }
            ?: sources.firstOrNull { it.hasCecDevice && it.connected }
            ?: sources.firstOrNull { it.hasCecDevice }
            ?: sources.firstOrNull { it.connected }
            ?: sources.firstOrNull()
    }

    fun switchTo(context: Context, source: HdmiSource): Boolean {
        // XGIMI plays HDMI in its own player rather than handling the standard passthrough
        // VIEW intent (nothing resolves it on this firmware). It wants the hardware input id.
        val hwId = hardwareIdFor(context, source.id)
        // The player reads the input from the TIF passthrough URI, else from "sw" ("HDMI1").
        val port = Regex("""HW(\d+)$""").find(hwId)?.groupValues?.get(1)
        val xgimi = Intent("com.xgimi.action.hdmiPlayer", TvContract.buildChannelUriForPassthroughInput(hwId))
            .setClassName("com.xgimi.xhplayer", "com.xgimi.xhplayer.InputActivity")
            .apply { if (port != null) putExtra("sw", "HDMI$port") }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val standard = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(source.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (intent in listOf(xgimi, standard)) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No handler: $intent")
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed: $intent", e)
            }
        }
        return false
    }

    /** CEC child inputs ("…/HDMI120004") map back to their port's hardware input ("…/HW1"). */
    private fun hardwareIdFor(context: Context, id: String): String {
        val tim = context.getSystemService(TvInputManager::class.java)
        val info = runCatching { tim?.getTvInputInfo(id) }.getOrNull()
        info?.parentId?.let { return it }
        val service = id.substringBeforeLast('/')
        return runCatching { tim?.tvInputList }.getOrNull()
            ?.firstOrNull { it.id.startsWith("$service/HW") }?.id ?: id
    }
}
