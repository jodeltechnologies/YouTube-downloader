package com.jodel.videopocket

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.*
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val blue = Color.rgb(70, 92, 229)
    private val ink = Color.rgb(25, 36, 60)
    private val muted = Color.rgb(109, 122, 147)
    private val prefs by lazy { getSharedPreferences("pocket", Context.MODE_PRIVATE) }
    private lateinit var url: EditText
    private lateinit var format: Spinner
    private lateinit var quality: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var update: Button
    private lateinit var autoShare: Switch
    private lateinit var clipboardCapture: Switch
    private lateinit var recent: LinearLayout
    private lateinit var open: Button
    private var active: WorkInfo? = null
    private var completedUri: String? = null
    private var sharedIntent: Intent? = null
    private var initialWorkLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        url.setText(savedInstanceState?.getString("url") ?: prefs.getString("draft", ""))
        if (savedInstanceState == null) sharedIntent = intent
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("pocket-download").observe(this) { infos ->
            active = infos.firstOrNull { !it.state.isFinished }
            val work = active ?: infos.firstOrNull { it.id.toString() == prefs.getString("lastWork", "") } ?: infos.lastOrNull()
            val busy = active != null
            start.isEnabled = !busy
            update.isEnabled = !busy
            stop.visibility = if (busy) View.VISIBLE else View.GONE
            if (work != null) {
                when (work.state) {
                    WorkInfo.State.RUNNING -> {
                        status.text = work.progress.getString("message") ?: "Preparing download engine…"
                        val value = work.progress.getInt("percent", -1)
                        progress.isIndeterminate = value < 0
                        if (value >= 0) progress.progress = value
                        progress.visibility = View.VISIBLE
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> { status.text = "Waiting for an internet connection…"; progress.isIndeterminate = true; progress.visibility = View.VISIBLE }
                    WorkInfo.State.SUCCEEDED -> { status.text = work.outputData.getString("message") ?: "Download complete."; progress.visibility = View.GONE; completedUri = work.outputData.getString("uri") }
                    WorkInfo.State.FAILED -> { status.text = work.outputData.getString("message") ?: "Download failed. Please try again."; progress.visibility = View.GONE }
                    WorkInfo.State.CANCELLED -> { status.text = "Download cancelled."; progress.visibility = View.GONE }
                }
            }
            open.visibility = if (!busy && completedUri != null) View.VISIBLE else View.GONE
            renderHistory()
            if (!initialWorkLoaded) {
                initialWorkLoaded = true
                sharedIntent?.let { handleIncoming(it) }
                sharedIntent = null
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("url", url.text.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        prefs.edit().putString("draft", url.text.toString()).apply()
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::clipboardCapture.isInitialized && clipboardCapture.isChecked) captureClipboard(false)
    }
    private fun handleIncoming(incoming: Intent) {
        val text = when (incoming.action) {
            Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            Intent.ACTION_VIEW -> incoming.dataString ?: ""
            else -> return
        }
        val link = YouTubeLink.capture(text)
        if (link == null) { status.text = "No supported YouTube video link was found."; return }
        url.setText(link)
        status.text = "YouTube link captured."
        if (autoShare.isChecked && active == null) beginDownload()
        else if (active != null) status.text = "Link captured. Wait for the current download to finish."
    }
    private fun captureClipboard(manual: Boolean) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val link = YouTubeLink.capture(text)
        if (link == null) { if (manual) toast("Copy a YouTube video link first."); return }
        if (!manual && link == prefs.getString("lastClipboard", "")) return
        prefs.edit().putString("lastClipboard", link).apply()
        url.setText(link)
        status.text = "YouTube link detected. Tap Download to save it."
    }
    private fun beginDownload() {
        if (active != null) { toast("A download is already running."); return }
        val link = YouTubeLink.canonical(url.text.toString()) ?: YouTubeLink.capture(url.text.toString())
        if (link == null) { url.error = "Enter a valid YouTube or Shorts link"; return }
        url.setText(link)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        completedUri = null
        open.visibility = View.GONE
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(url.windowToken, 0)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("url" to link, "audio" to (format.selectedItemPosition == 1), "height" to listOf(360, 480, 720, 1080)[quality.selectedItemPosition]))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        prefs.edit().putString("lastWork", request.id.toString()).apply()
        start.isEnabled = false
        status.text = "Starting download…"
        WorkManager.getInstance(this).enqueueUniqueWork("pocket-download", ExistingWorkPolicy.KEEP, request)
    }
    private fun openFile(value: String, audio: Boolean? = null) {
        try {
            val uri = Uri.parse(value)
            val type = audio?.let { if (it) "audio/mpeg" else "video/mp4" } ?: contentResolver.getType(uri) ?: "video/*"
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { toast("The file is unavailable or no player is installed. Check Downloads/Video Pocket.") }
    }
    private fun renderHistory() {
        recent.removeAllViews()
        val entries = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (entries.length() == 0) { recent.addView(text("Your saved videos and audio will appear here.", 14, muted)); return }
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val row = column(14).apply { background = rounded(Color.WHITE, 14); setPadding(dp(16), dp(14), dp(16), dp(14)) }
            row.addView(text(item.optString("name"), 15, ink, true))
            row.addView(text((if (item.optBoolean("audio")) "MP3 audio" else "MP4 video") + " · " + DateFormat.getDateInstance().format(Date(item.optLong("date"))), 12, muted))
            row.addView(button("Open file", false) { openFile(item.optString("uri"), item.optBoolean("audio")) })
            recent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }
    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(245,247,251)); isFillViewport = true }
        val root = column(0).apply { setPadding(dp(22), dp(22), dp(22), dp(30)) }
        scroll.addView(root)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.addView(text("▶  video pocket", 25, ink, true))
        root.addView(text("BY JODEL TECHNOLOGIES", 10, muted).apply { letterSpacing = .16f })
        root.addView(space(28))
        root.addView(text("Your videos. With you.", 14, blue, true))
        root.addView(text("Save it now.\nWatch it anywhere.", 34, ink, true).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        root.addView(space(8))
        root.addView(text("Downloads run on your phone. No account or hosting subscription needed.", 15, muted))
        root.addView(space(24))
        val card = column(0).apply { background = rounded(Color.WHITE, 18); setPadding(dp(18), dp(20), dp(18), dp(20)) }
        root.addView(card)
        card.addView(text("Start with a YouTube link", 19, ink, true))
        card.addView(text("Paste it here, or share a video to Video Pocket.", 13, muted))
        card.addView(space(18))
        card.addView(text("Video link", 14, ink, true))
        url = EditText(this).apply { hint = "https://youtu.be/…"; textSize = 16f; setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI; background = rounded(Color.rgb(245,247,252), 10); setPadding(dp(12),dp(13),dp(12),dp(13)); contentDescription = "YouTube video link" }
        card.addView(url)
        card.addView(button("Paste copied link", false) { captureClipboard(true) })
        card.addView(space(8))
        card.addView(text("Save as", 14, ink, true))
        format = spinner(listOf("Video · MP4", "Audio · MP3"))
        card.addView(format)
        card.addView(space(12))
        card.addView(text("Video quality", 14, ink, true))
        quality = spinner(listOf("360p · Save data", "480p · Standard", "720p · HD", "1080p · Full HD")).apply { setSelection(prefs.getInt("quality", 2).coerceIn(0,3)) }
        card.addView(quality)
        format.setSelection(prefs.getInt("format", 0).coerceIn(0,1))
        format.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { quality.isEnabled = position == 0; prefs.edit().putInt("format", position).apply() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        quality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { prefs.edit().putInt("quality", position).apply() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        card.addView(space(15))
        start = button("↓  Download to phone", true) { beginDownload() }
        card.addView(start)
        status = text("Files are saved in Downloads/Video Pocket.", 14, muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        card.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { visibility = View.GONE; max = 100 }
        card.addView(progress, LinearLayout.LayoutParams(-1, dp(12)))
        stop = button("Cancel download", false) { WorkManager.getInstance(this).cancelUniqueWork("pocket-download") }.apply { visibility = View.GONE }
        card.addView(stop)
        open = button("Open downloaded file", false) { completedUri?.let { openFile(it) } }.apply { visibility = View.GONE }
        card.addView(open)
        root.addView(space(24))
        root.addView(text("Link capture", 19, ink, true))
        autoShare = Switch(this).apply { text = "Download shared links automatically"; textSize = 14f; setTextColor(ink); isChecked = prefs.getBoolean("autoShare", true); setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("autoShare", value).apply() } }
        root.addView(autoShare)
        clipboardCapture = Switch(this).apply { text = "Detect copied links when I open this app"; textSize = 14f; setTextColor(ink); isChecked = prefs.getBoolean("clipboard", false); setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("clipboard", value).apply(); if (value) captureClipboard(false) } }
        root.addView(clipboardCapture)
        root.addView(text("In YouTube or your browser, tap Share and choose Video Pocket. Clipboard detection works while this app is open. Android may show a clipboard notice.", 13, muted))
        root.addView(space(20))
        update = button("Update download engine", false) {
            if (active == null) {
                val work = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("action" to "update")).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
                prefs.edit().putString("lastWork", work.id.toString()).apply()
                WorkManager.getInstance(this).enqueueUniqueWork("pocket-download", ExistingWorkPolicy.KEEP, work)
            }
        }
        root.addView(update)
        root.addView(text("If YouTube changes and downloads fail, update the engine and try again. Live streams and videos over two hours are excluded. Internet is required.", 12, muted))
        root.addView(space(24))
        root.addView(text("Recent downloads", 20, ink, true))
        recent = column(0)
        root.addView(recent)
        root.addView(space(24))
        root.addView(text("Download videos you own or have permission to save. This app is independent of YouTube and ChatGPT.", 12, muted))
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun column(padding: Int) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding)) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(4), 0, dp(7)); setLineSpacing(dp(3).toFloat(), 1f) }
    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply { text = label; textSize = 14f; isAllCaps = false; minHeight = dp(48); setTextColor(if(primary) Color.WHITE else blue); background = rounded(if(primary) blue else Color.rgb(235,239,253),10); layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(9); bottomMargin = dp(9) }; setOnClickListener { action() } }
    private fun spinner(values: List<String>) = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values); layoutParams = LinearLayout.LayoutParams(-1,dp(50)); background = rounded(Color.rgb(245,247,252),10); setPadding(dp(8),0,dp(8),0) }
    private fun toast(message: String) = Toast.makeText(this,message,Toast.LENGTH_LONG).show()
}
