package com.jodel.videopocket

object NetworkRetry {
    const val MAX_ATTEMPTS = 10
    fun shouldRetry(raw: String?, online: Boolean, attempts: Int): Boolean {
        if (attempts >= MAX_ATTEMPTS) return false
        val text = raw.orEmpty().lineSequence().lastOrNull { it.trim().startsWith("ERROR:") }?.lowercase() ?: raw.orEmpty().lowercase()
        val permanent = listOf("403", "404", "private video", "video unavailable", "age-restricted", "requested format", "sign in to confirm", "not a bot", "no space left", "500 mb")
        if (permanent.any { it in text }) return false
        return !online || listOf("timed out", "timeout", "connection", "network", "socket", "reset by peer", "broken pipe", "remote end closed", "resolve", "temporary failure", "http error 408", "http error 500", "http error 502", "http error 503", "http error 504", "incomplete read", "incompleteread").any { it in text }
    }
}
