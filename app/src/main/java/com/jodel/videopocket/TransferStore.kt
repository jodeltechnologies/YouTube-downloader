package com.jodel.videopocket

import android.content.Context
import org.json.JSONObject
import java.util.UUID

class TransferStore(context: Context) {
    companion object { private val lock = Any() }
    private val prefs = context.getSharedPreferences("pocket", Context.MODE_PRIVATE)
    data class Transfer(val session: String, val url: String, val audio: Boolean, val height: Int, val state: String, val tree: String = "", val destinationName: String = "Downloads/Video Pocket")
    fun current(): Transfer? = try {
        val json = JSONObject(prefs.getString("transfer", "") ?: "")
        Transfer(json.getString("session"), json.getString("url"), json.getBoolean("audio"), json.getInt("height"), json.optString("state", "paused"), json.optString("tree", ""), json.optString("destinationName", "Downloads/Video Pocket"))
    } catch (_: Exception) { null }
    fun start(url: String, audio: Boolean, height: Int, tree: String = "", destinationName: String = "Downloads/Video Pocket"): Transfer = synchronized(lock) {
        val t = Transfer(UUID.randomUUID().toString(), url, audio, height, "running", tree, destinationName)
        write(t)
        t
    }
    fun state(session: String, state: String) = synchronized(lock) {
        current()?.takeIf { it.session == session }?.let { write(it.copy(state = state)) }
    }
    fun destination(tree: String, name: String) = synchronized(lock) {
        current()?.let { write(it.copy(tree = tree, destinationName = name)) }
    }
    fun clear(session: String) = synchronized(lock) {
        if (current()?.session == session) prefs.edit().remove("transfer").commit()
    }
    fun discard(session: String) = synchronized(lock) { prefs.edit().putString("discardSession", session).commit(); clear(session) }
    fun isDiscarded(session: String) = prefs.getString("discardSession", "") == session
    private fun write(t: Transfer) {
        prefs.edit().putString("transfer", JSONObject().put("session", t.session).put("url", t.url).put("audio", t.audio).put("height", t.height).put("state", t.state).put("tree", t.tree).put("destinationName", t.destinationName).toString()).commit()
    }
}
