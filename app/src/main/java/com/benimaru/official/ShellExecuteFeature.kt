package com.benimaru.official

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import es.dmoral.toasty.Toasty
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val MAX_SCRIPT_BYTES = 1024 * 1024
private const val MAX_OUTPUT_CHARS = 200_000
private const val PREVIEW_LINES = 14

private val ANSI_ESCAPES = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

private val C_TERMINAL_BG = Color.parseColor("#0D1117")
private val C_TERMINAL_TEXT = Color.parseColor("#C9D1D9")
private val C_TERMINAL_DIM = Color.parseColor("#6E7681")
private val C_TERMINAL_PROMPT = Color.parseColor("#7EE787")
private val C_TERMINAL_SHEBANG = Color.parseColor("#79C0FF")
private val C_GOLD = Color.parseColor("#E0A800")
private val C_OK = Color.parseColor("#2E9E5B")
private val C_FAIL = Color.parseColor("#E53935")
private val C_WARN = Color.parseColor("#E09B00")

internal class PickedScript(val name: String, val text: String) {
    val bytes: ByteArray = text.toByteArray(Charsets.UTF_8)
    val lineCount: Int = text.trimEnd('\n').count { it == '\n' } + 1
}

private sealed class ScriptRead {
    class Ok(val script: PickedScript) : ScriptRead()
    object Unreadable : ScriptRead()
    object Binary : ScriptRead()
    object TooLarge : ScriptRead()
    object Empty : ScriptRead()
}


internal fun MainActivity.showShellExecuteSheet(initialUri: Uri? = null) {
    var script: PickedScript? = null
    var sheetRef: Sheet? = null
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    var pick: () -> Unit = {}

    fun handleUri(uri: Uri) {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { readScript(uri) }
            if (isFinishing || isDestroyed) return@launch
            when (r) {
                is ScriptRead.Ok -> {
                    script = r.script
                    sheetRef?.buttonViews?.firstOrNull()?.isEnabled = true
                    renderScriptPicker(box, r.script, pick)
                }
                ScriptRead.Unreadable -> toastErr(R.string.shell_err_read)
                ScriptRead.Binary -> toastErr(R.string.shell_err_binary)
                ScriptRead.TooLarge -> toastErr(R.string.shell_err_large)
                ScriptRead.Empty -> toastErr(R.string.shell_err_empty)
            }
        }
    }

    pick = {
        onScriptPicked = { uri -> if (uri != null) handleUri(uri) }
        pickerKind = PICKER_SHELL
        scriptPicker.launch(arrayOf("*/*"))
    }
    renderScriptPicker(box, null, pick)

    sheetRef = Sheet(this)
        .icon(R.drawable.ic_terminal, C_GOLD)
        .title(getString(R.string.title_shell))
        .message(getString(R.string.shell_msg))
        .content(box)
        .button(getString(R.string.shell_btn_execute), SheetStyle.PRIMARY) {
            script?.let { showShellWarning(it) }
        }
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .show()
    sheetRef?.buttonViews?.firstOrNull()?.isEnabled = false
    initialUri?.let { handleUri(it) }
}

private fun MainActivity.toastErr(res: Int) {
    Toasty.error(this, getString(res), Toast.LENGTH_SHORT, true).show()
}

private fun MainActivity.renderScriptPicker(box: LinearLayout, script: PickedScript?, pick: () -> Unit) {
    val ctx: Context = this
    box.removeAllViews()

    if (script == null) {
        box.addView(
            optionRow(
                ctx,
                getString(R.string.shell_pick_title),
                getString(R.string.shell_pick_sub),
                Ui.drawable(ctx, R.drawable.ic_download)
            ) { pick() }.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
                background = Ui.ripple(
                    ctx, ColorUtils.setAlphaComponent(C_GOLD, 0x14), ctx.dp(16),
                    ColorUtils.setAlphaComponent(C_GOLD, 0x99), ctx.dp(1)
                )
            }
        )
        return
    }

    box.addView(
        optionRow(
            ctx,
            script.name,
            getString(R.string.shell_file_meta, formatBytes(script.bytes.size), script.lineCount),
            Ui.drawable(ctx, R.drawable.ic_terminal)
        ) { pick() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
            background = Ui.ripple(
                ctx, ColorUtils.setAlphaComponent(C_GOLD, 0x14), ctx.dp(16),
                ColorUtils.setAlphaComponent(C_GOLD, 0x99), ctx.dp(1)
            )
            addView(TextView(ctx).apply {
                text = getString(R.string.shell_change)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.accent(ctx))
            })
        }
    )

    box.addView(TextView(ctx).apply {
        text = getString(R.string.shell_preview)
        textSize = 11f
        letterSpacing = 0.1f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Ui.onSurfaceVariant(ctx))
        setPadding(ctx.dp(4), ctx.dp(16), 0, ctx.dp(6))
    })
    box.addView(TextView(ctx).apply {
        typeface = Typeface.MONOSPACE
        textSize = 11.5f
        setLineSpacing(0f, 1.1f)
        maxLines = PREVIEW_LINES
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(C_TERMINAL_TEXT)
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        background = Ui.rounded(C_TERMINAL_BG, ctx.dp(14))
        text = highlight(script.text)
    })
}

private fun highlight(source: String): CharSequence {
    val sb = SpannableStringBuilder()
    val lines = source.trimEnd('\n').split("\n").take(PREVIEW_LINES)
    val width = lines.size.toString().length
    lines.forEachIndexed { i, line ->
        val start = sb.length
        sb.append((i + 1).toString().padStart(width)).append("  ")
        sb.setSpan(ForegroundColorSpan(C_TERMINAL_DIM), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val ls = sb.length
        sb.append(line.take(120))
        val color = when {
            line.startsWith("#!") -> C_TERMINAL_SHEBANG
            line.trimStart().startsWith("#") -> C_TERMINAL_DIM
            else -> C_TERMINAL_TEXT
        }
        sb.setSpan(ForegroundColorSpan(color), ls, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (i < lines.lastIndex) sb.append('\n')
    }
    return sb
}

private fun formatBytes(n: Int): String = when {
    n < 1024 -> "$n B"
    else -> String.format(java.util.Locale.US, "%.1f KB", n / 1024.0)
}

private fun MainActivity.readScript(uri: Uri): ScriptRead {
    return try {
        var name = "script.sh"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
        }
        val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it) } ?: return ScriptRead.Unreadable
        if (bytes.size > MAX_SCRIPT_BYTES) return ScriptRead.TooLarge
        if (bytes.any { it == 0.toByte() }) return ScriptRead.Binary

        val text = String(bytes, Charsets.UTF_8)
            .removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        if (text.isBlank()) return ScriptRead.Empty
        ScriptRead.Ok(PickedScript(name, text))
    } catch (e: Exception) {
        ScriptRead.Unreadable
    }
}

private fun readLimited(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        if (out.size() > MAX_SCRIPT_BYTES) break
    }
    return out.toByteArray()
}


private fun killScriptTree(remote: String) {
    val cmd = "kill_tree() { for c in \$(pgrep -P \$1 2>/dev/null); do kill_tree \$c; done; kill -9 \$1 2>/dev/null; }; " +
        "pid=\$(cat $remote.pid 2>/dev/null); [ -n \"\$pid\" ] && kill_tree \$pid; true"
    runCatching { Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null).waitFor() }
}

private fun MainActivity.showShellWarning(script: PickedScript) {
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    Sheet(this)
        .icon(R.drawable.ic_warning, C_FAIL)
        .title(getString(R.string.shell_warn_title))
        .message(getString(R.string.shell_warn_msg))
        .content(infoCard(this, getString(R.string.shell_warn_trust), tint = C_WARN))
        .button(getString(R.string.shell_btn_reset_run), SheetStyle.DANGER) {
            resetAllFunctions(prefs) { runShellScript(script) }
        }
        .button(getString(R.string.shell_btn_run_anyway), SheetStyle.TONAL) { runShellScript(script) }
        .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
        .show()
}


internal fun MainActivity.runShellScript(script: PickedScript) {
    if (!hasShizukuPermission()) return
    val ctx: Context = this

    val terminal = TextView(ctx).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setLineSpacing(0f, 1.1f)
        setTextColor(C_TERMINAL_TEXT)
        setTextIsSelectable(true)
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    }
    val scroll = ScrollView(ctx).apply {
        background = Ui.rounded(C_TERMINAL_BG, ctx.dp(14))
        isVerticalScrollBarEnabled = true
        addView(terminal, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(300)).apply { topMargin = ctx.dp(12) }
        setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    val dot = View(ctx).apply {
        background = Ui.rounded(C_GOLD, ctx.dp(5))
        layoutParams = LinearLayout.LayoutParams(ctx.dp(10), ctx.dp(10)).apply { marginEnd = ctx.dp(10) }
    }
    val statusText = TextView(ctx).apply {
        text = getString(R.string.shell_running)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Ui.onSurface(ctx))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val elapsedText = TextView(ctx).apply {
        textSize = 13f
        setTextColor(Ui.onSurfaceVariant(ctx))
    }
    val statusRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        background = Ui.rounded(Ui.tonalFill(ctx), ctx.dp(14))
        addView(dot)
        addView(statusText)
        addView(elapsedText)
    }

    val content = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(statusRow)
        addView(scroll)
    }

    val sheet = Sheet(this)
        .icon(R.drawable.ic_terminal, C_GOLD)
        .title(getString(R.string.title_shell))
        .message(script.name)
        .content(content)
        .cancelable(false)
        .button(getString(R.string.shell_btn_stop), SheetStyle.DANGER, dismiss = false)
        .button(getString(R.string.shell_btn_copy), SheetStyle.TONAL, dismiss = false)
        .show()
    if (!sheet.isShowing()) return
    sheet.dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val primaryBtn: MaterialButton = sheet.buttonViews[0]
    val copyBtn: MaterialButton = sheet.buttonViews[1]
    copyBtn.isEnabled = false

    val buffer = StringBuilder()
    var trimmed = false
    var dirty = false
    val lock = Any()

    fun emit(chunk: String) {
        val clean = ANSI_ESCAPES.replace(chunk, "").replace("\r\n", "\n").replace('\r', '\n')
        synchronized(lock) {
            buffer.append(clean)
            if (buffer.length > MAX_OUTPUT_CHARS) {
                buffer.delete(0, buffer.length - MAX_OUTPUT_CHARS)
                trimmed = true
            }
            dirty = true
        }
    }

    fun snapshot(): CharSequence {
        val text = synchronized(lock) { buffer.toString() }
        val sb = SpannableStringBuilder()
        if (trimmed) {
            val s = sb.length
            sb.append(getString(R.string.shell_trimmed)).append('\n')
            sb.setSpan(ForegroundColorSpan(C_TERMINAL_DIM), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append(text)
        return sb
    }

    fun renderOutput() {
        val nearBottom = scroll.getChildAt(0).bottom - (scroll.height + scroll.scrollY) < ctx.dp(48)
        terminal.text = snapshot()
        if (nearBottom) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    val processRef = AtomicReference<Process?>(null)
    val stoppedFlag = AtomicBoolean(false)
    val startedAt = SystemClock.elapsedRealtime()
    val remote = "/data/local/tmp/benimaru_exec_${System.currentTimeMillis()}.sh"

    emit("$ sh ${script.name}\n")

    fun stopRemote() {
        stoppedFlag.set(true)
        val p = processRef.get()
        thread(name = "shell-stop") {
            killScriptTree(remote)
            runCatching { p?.destroy() }
        }
    }

    val destroyObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            stopRemote()
        }
    }
    lifecycle.addObserver(destroyObserver)

    primaryBtn.setOnClickListener {
        primaryBtn.isEnabled = false
        stopRemote()
    }

    val ticker = lifecycleScope.launch {
        while (isActive) {
            val shouldDraw = synchronized(lock) { dirty.also { dirty = false } }
            if (shouldDraw) renderOutput()
            elapsedText.text = getString(
                R.string.shell_elapsed,
                String.format(java.util.Locale.US, "%.1f", (SystemClock.elapsedRealtime() - startedAt) / 1000.0)
            )
            dot.alpha = if (((SystemClock.elapsedRealtime() - startedAt) / 500) % 2 == 0L) 1f else 0.35f
            delay(100)
        }
    }

    lifecycleScope.launch(Dispatchers.IO) {
        var exit = -1
        var staged = false
        try {
            val stage = Shizuku.newProcess(
                arrayOf("sh", "-c", "cat > $remote && chmod 755 $remote"), null, null
            )
            stage.outputStream.use { it.write(script.bytes) }
            val stageCode = stage.waitFor()
            if (stageCode != 0) {
                emit("\n" + getString(R.string.shell_stage_fail) + "\n")
                exit = -2
            } else {
                staged = true
                if (stoppedFlag.get()) {
                    exit = -3
                } else {
                    val p = Shizuku.newProcess(
                        arrayOf("sh", "-c", "cd /data/local/tmp && echo \$\$ > $remote.pid && exec sh $remote 2>&1"), null, null
                    )
                    processRef.set(p)
                    runCatching { p.outputStream.close() }
                    if (stoppedFlag.get()) {
                        killScriptTree(remote)
                        runCatching { p.destroy() }
                    }
                    p.inputStream.reader(Charsets.UTF_8).use { r ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            emit(String(buf, 0, n))
                        }
                    }
                    exit = p.waitFor()
                }
            }
        } catch (e: Exception) {
            if (!stoppedFlag.get()) emit("\n[error] ${e.message ?: e.javaClass.simpleName}\n")
        } finally {
            withContext(NonCancellable) {
                if (staged || exit != -2) {
                    runCatching {
                        Shizuku.newProcess(arrayOf("sh", "-c", "rm -f $remote $remote.pid"), null, null).waitFor()
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            ticker.cancel()
            lifecycle.removeObserver(destroyObserver)
            if (isFinishing || isDestroyed) return@withContext
            val stopped = stoppedFlag.get()

            synchronized(lock) {
                if (buffer.isEmpty() || buffer.toString() == "$ sh ${script.name}\n") {
                    buffer.append(getString(R.string.shell_no_output)).append('\n')
                }
            }
            renderOutput()
            sheet.dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val color: Int
            val label: String
            if (stopped) {
                color = C_WARN
                label = getString(R.string.shell_stopped)
            } else if (exit == 0) {
                color = C_OK
                label = getString(R.string.shell_done_ok, exit)
            } else {
                color = C_FAIL
                label = getString(R.string.shell_done_fail, exit)
            }
            dot.alpha = 1f
            dot.background = Ui.rounded(color, ctx.dp(5))
            statusText.text = label
            statusText.setTextColor(color)
            statusRow.background = Ui.rounded(ColorUtils.setAlphaComponent(color, 0x1F), ctx.dp(14))

            findViewById<TextView>(R.id.tvStatusShell)?.text =
                if (stopped) getString(R.string.status_shell_stopped) else getString(R.string.status_shell_last, exit)

            primaryBtn.isEnabled = true
            primaryBtn.text = getString(R.string.btn_close)
            primaryBtn.backgroundTintList = ColorStateList.valueOf(Ui.accent(ctx))
            primaryBtn.setOnClickListener { sheet.dismiss() }
            copyBtn.isEnabled = true
            copyBtn.setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("shell-output", terminal.text.toString()))
                Toasty.success(this@runShellScript, getString(R.string.shell_copied), Toast.LENGTH_SHORT, true).show()
            }
            sheet.dialog.setCancelable(true)
            sheet.dialog.setCanceledOnTouchOutside(true)
            sheet.dialog.behavior.isHideable = true
            sheet.dialog.behavior.isDraggable = true
        }
    }
}
