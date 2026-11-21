package com.jodel.videopocket

import android.content.Context
import org.json.JSONObject
import java.util.UUID

class TransferStore(context: Context) {
    companion object { private val lock = Any() }
    private val prefs = context.getSharedPreferences("pocket", Context.MODE_PRIVATE)
    data class Transfer(val session: String, val url: String, val audio: Boolean, val height: Int, val state: String)
    fun current(): Transfer? = try {
        val json = JSONObject(prefs.getString("transfer", "") ?: "")
        Transfer(json.getString("session"), json.getString("url"), json.getBoolean("audio"), json.getInt("height"), json.optString("state", "paused"))
    } catch (_: Exception) { null }
    fun start(url: String, audio: Boolean, height: Int): Transfer = synchronized(lock) {
        val t = Transfer(UUID.randomUUID().toString(), url, audio, height, "running")
        write(t)
        t
    }
    fun state(session: String, state: String) = synchronized(lock) {
        current()?.takeIf { it.session == session }?.let { write(it.copy(state = state)) }
    }
    fun clear(session: String) = synchronized(lock) {
        if (current()?.session == session) prefs.edit().remove("transfer").commit()
    }
    fun discard(session: String) = synchronized(lock) { prefs.edit().putString("discardSession", session).commit(); clear(session) }
    fun isDiscarded(session: String) = prefs.getString("discardSession", "") == session
    private fun write(t: Transfer) {
        prefs.edit().putString("transfer", JSONObject().put("session", t.session).put("url", t.url).put("audio", t.audio).put("height", t.height).put("state", t.state).toString()).commit()
    }
}
