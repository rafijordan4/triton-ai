package com.tritonai.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    // Replace this with your deployed HTTPS backend URL (no trailing slash).
    private val apiBaseUrl = "https://YOUR-BACKEND-URL.onrender.com"

    private val bg = Color.rgb(12, 15, 22)
    private val panel = Color.rgb(23, 28, 39)
    private val accent = Color.rgb(173, 143, 255)
    private val primaryText = Color.rgb(244, 245, 250)
    private val mutedText = Color.rgb(157, 165, 183)

    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var status: TextView
    private val history = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
        addBubble("assistant", "Halo! Aku Triton AI. Ada yang bisa kubantu hari ini?")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(14))
        }
        val title = TextView(this).apply {
            text = "TRITON AI"
            textSize = 23f
            setTextColor(primaryText)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val subtitle = TextView(this).apply {
            text = "AI assistant • Online"
            textSize = 13f
            setTextColor(mutedText)
            setPadding(0, dp(4), 0, 0)
        }
        header.addView(title)
        header.addView(subtitle)
        root.addView(header)

        val divider = View(this).apply { setBackgroundColor(Color.rgb(43, 49, 65)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(12), 0, dp(12))
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "Siap membantu"
            textSize = 12f
            setTextColor(mutedText)
            setPadding(dp(2), dp(4), dp(2), dp(8))
        }
        root.addView(status)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Tulis pesan..."
            setHintTextColor(mutedText)
            setTextColor(primaryText)
            textSize = 15f
            maxLines = 4
            minLines = 1
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(panel, dp(16))
            setSingleLine(false)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = Button(this).apply {
            text = "Kirim"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(20, 17, 31))
            background = rounded(accent, dp(14))
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { sendMessage() }
        }
        val buttonParams = LinearLayout.LayoutParams(-2, dp(50))
        buttonParams.marginStart = dp(8)
        composer.addView(sendButton, buttonParams)
        root.addView(composer)

        setContentView(root)
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        if (apiBaseUrl.contains("YOUR-BACKEND-URL")) {
            status.text = "Atur URL backend di MainActivity.kt terlebih dahulu."
            addBubble("assistant", "Backend belum dikonfigurasi. Deploy folder backend, lalu ganti apiBaseUrl di kode aplikasi.")
            return
        }

        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        addBubble("user", prompt)
        history.add("user" to prompt)
        status.text = "Triton sedang berpikir..."
        sendButton.isEnabled = false

        val historySnapshot = history.dropLast(1).takeLast(12)
        thread {
            var answer = ""
            var error: String? = null
            try {
                val conn = (URL("$apiBaseUrl/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val historyJson = JSONArray()
                historySnapshot.forEach { (role, content) ->
                    historyJson.put(JSONObject().put("role", role).put("content", content))
                }
                val body = JSONObject()
                    .put("message", prompt)
                    .put("history", historyJson)
                    .toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    val detail = try { JSONObject(responseText).optString("error", "HTTP $code") } catch (_: Exception) { "HTTP $code" }
                    throw Exception(detail)
                }
                answer = JSONObject(responseText).optString("reply", "")
                if (answer.isBlank()) throw Exception("Respons AI kosong.")
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "Tidak bisa terhubung ke server."
            }

            runOnUiThread {
                sendButton.isEnabled = true
                if (error == null) {
                    history.add("assistant" to answer)
                    addBubble("assistant", answer)
                    status.text = "Online • Selesai"
                } else {
                    addBubble("assistant", "Gagal: $error")
                    status.text = "Periksa koneksi atau konfigurasi backend"
                }
            }
        }
    }

    private fun addBubble(role: String, text: String) {
        val isUser = role == "user"
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(primaryText)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (isUser) Color.rgb(66, 54, 105) else panel, dp(16))
            setTextIsSelectable(true)
            maxWidth = resources.displayMetrics.widthPixels * 5 / 6
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(4))
            addView(bubble, LinearLayout.LayoutParams(-2, -2))
        }
        messages.addView(row)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
