package com.benimaru.official

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File
import java.util.Locale
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject


object AppTranslator {

    private const val CACHE_VERSION = 2

    private class Loaded(
        val lang: String,
        val strings: Map<Int, String>,
        val plurals: Map<Int, Array<String>>,
        val enToTr: Map<String, String>,
        val trToEn: Map<String, String>
    )

    @Volatile private var loaded: Loaded? = null

    private val wrapCache = WeakHashMap<Resources, Pair<Configuration, TranslatingResources>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var setupJob: Job? = null
    private val main = Handler(Looper.getMainLooper())

    private val LIBRARY_PREFIXES = listOf(
        "abc_", "m3_", "mtrl_", "material_", "common_", "androidx_", "appbar_", "bottomsheet_", "bottom_sheet_",
        "character_counter_", "clear_text_", "error_icon_", "exposed_dropdown_", "fab_transformation_",
        "icon_content_", "item_view_role_", "password_toggle_", "path_password_", "searchbar_", "searchview_",
        "side_sheet_", "status_bar_notification_", "call_notification_", "copy", "state_busy", "state_collapsed",
        "state_expanded", "state_off", "state_on", "state_mixed", "tab", "default_popup_window_title",
        "dropdown_menu", "range_end", "range_start", "in_progress", "not_selected", "selected", "template_percent",
        "navigation_menu", "close_drawer", "close_sheet", "app_name"
    )
    private val APP_PREFIX_EXCEPTIONS = setOf("tab_apps", "tab_system", "tab_frozen")

    private val PROTECTED = Regex(
        "%(?:\\d+\\$)?[sd]|%%|\\.(?:sh|apks|xapk|apkm|apk|obb)\\b|\\b(?:Benimaru|Shizuku|Telegram|Fstrim|Android|DNS|RAM|CPU|DPI|FPS|AFK|SDK|API|GB|MB|Hz|APKS|XAPK|APKM|APK|OBB)\\b"
    )
    private val TOKEN = Regex("\\{\\s*(\\p{Nd}+)\\s*\\}")

    fun targetLanguage(): String? {
        val loc = Locale.getDefault()
        val code = when (val l = loc.language) {
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            else -> l
        }
        if (code.isEmpty() || code == "en") return null
        return TranslateLanguage.fromLanguageTag(code)
    }

    private val HANDWRITTEN_PROBES = intArrayOf(R.string.btn_cancel, R.string.btn_save, R.string.btn_apply)

    // True when the current language ships its own values-xx/strings.xml (e.g. Portuguese).
    // Those human translations must win; machine translation would only make them worse.
    private fun hasHandwrittenResources(context: Context): Boolean {
        val app = context.applicationContext
        val cur = app.resources
        val cfg = Configuration(cur.configuration).apply { setLocale(Locale.ENGLISH) }
        val en = app.createConfigurationContext(cfg).resources
        return HANDWRITTEN_PROBES.any { rawText(cur, it).toString() != en.getText(it).toString() }
    }

    fun languageDisplayName(): String {
        val loc = Locale.getDefault()
        return loc.getDisplayLanguage(loc).replaceFirstChar { it.uppercase(loc) }
    }


    @Synchronized
    fun init(context: Context) {
        val lang = targetLanguage()
        if (lang == null || hasHandwrittenResources(context)) {
            loaded = null
            if (lang != null) cacheFile(context.applicationContext, lang).delete()
            return
        }
        if (loaded?.lang == lang) return
        loaded = null
        val app = context.applicationContext
        val english = collectEnglish(app)
        loaded = readCache(app, lang, english)
    }

    fun needsSetup(context: Context): Boolean {
        val lang = targetLanguage() ?: return false
        if (hasHandwrittenResources(context)) return false
        return loaded?.lang != lang
    }

    val isActive: Boolean get() = loaded != null


    @Synchronized
    fun startSetup(context: Context, onPhase: (Int) -> Unit, onDone: (Boolean) -> Unit) {
        val lang = targetLanguage()
        if (lang == null || hasHandwrittenResources(context)) {
            main.post { onDone(false) }
            return
        }
        setupJob?.cancel()
        val app = context.applicationContext
        setupJob = scope.launch {
            var ok = false
            try {
                main.post { onPhase(-1) }
                translateAll(app, lang) { pct -> main.post { onPhase(pct) } }
                ok = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ok = false
            } finally {
                val result = ok
                main.post { onDone(result) }
            }
        }
    }

    fun cancelSetup() {
        setupJob?.cancel()
    }

    private suspend fun translateAll(app: Context, lang: String, onProgress: (Int) -> Unit) {
        val english = collectEnglish(app)
        val translator: Translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(lang)
                .build()
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()

            val memo = HashMap<String, String>()
            val total = english.strings.size + english.plurals.size * 2
            var done = 0
            fun tick() {
                done++
                if (done % 8 == 0 || done == total) onProgress((done * 100 / total).coerceIn(0, 100))
            }

            val outStrings = LinkedHashMap<String, String>()
            for ((name, text) in english.strings) {
                outStrings[name] = translateText(translator, text, memo)
                tick()
            }
            val outPlurals = LinkedHashMap<String, Array<String>>()
            for ((name, forms) in english.plurals) {
                outPlurals[name] = arrayOf(
                    translateText(translator, forms[0], memo).also { tick() },
                    translateText(translator, forms[1], memo).also { tick() }
                )
            }
            writeCache(app, lang, english, outStrings, outPlurals)
        } finally {
            translator.close()
        }
        synchronized(this) {
            loaded = null
            if (targetLanguage() == lang) loaded = readCache(app, lang, english)
        }
        if (loaded == null) throw IllegalStateException("Translation cache could not be loaded")
    }

    private suspend fun translateText(translator: Translator, text: String, memo: HashMap<String, String>): String {
        if (text.none { it.isLetter() }) return text
        val out = StringBuilder()
        for ((i, line) in text.split("\n").withIndex()) {
            if (i > 0) out.append('\n')
            if (line.none { it.isLetter() }) {
                out.append(line)
            } else {
                val cached = memo[line]
                if (cached != null) {
                    out.append(cached)
                } else {
                    val translated = translateLine(translator, line)
                    memo[line] = translated
                    out.append(translated)
                }
            }
        }
        return out.toString()
    }

    private suspend fun translateLine(translator: Translator, line: String): String {
        val lead = line.takeWhile { it.isWhitespace() }
        val trail = line.takeLastWhile { it.isWhitespace() }
        val core = line.trim()
        if (core.isEmpty()) return line

        val protectedParts = PROTECTED.findAll(core).map { it.value }.toList()

        try {
            var i = 0
            val tokenized = PROTECTED.replace(core) { "{${i++}}" }
            val shout = core.length > 2 && core.any { it.isLetter() } && core == core.uppercase(Locale.ROOT)
            val translatedRaw = translator.translate(if (shout) tokenized.lowercase(Locale.ROOT) else tokenized).await()
            val raw = if (shout) translatedRaw.uppercase(Locale.getDefault()) else translatedRaw
            val restored = restoreTokens(raw, protectedParts)
            if (restored != null && restored.isNotBlank()) return lead + restored + trail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }

        return try {
            val sb = StringBuilder()
            var last = 0
            for (m in PROTECTED.findAll(core)) {
                sb.append(translatePiece(translator, core.substring(last, m.range.first)))
                sb.append(m.value)
                last = m.range.last + 1
            }
            sb.append(translatePiece(translator, core.substring(last)))
            lead + sb.toString() + trail
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            line
        }
    }

    private suspend fun translatePiece(translator: Translator, piece: String): String {
        val core = piece.trim()
        if (core.none { it.isLetter() }) return piece
        val out = translator.translate(core).await()
        return piece.takeWhile { it.isWhitespace() } + out + piece.takeLastWhile { it.isWhitespace() }
    }

    private fun restoreTokens(translated: String, originals: List<String>): String? {
        if (originals.isEmpty()) return translated
        val seen = BooleanArray(originals.size)
        var bad = false
        val out = TOKEN.replace(translated) { m ->
            val idx = m.groupValues[1].map { Character.digit(it, 10) }.fold(0) { acc, d -> acc * 10 + d }
            if (idx !in originals.indices || seen[idx]) {
                bad = true
                m.value
            } else {
                seen[idx] = true
                originals[idx]
            }
        }
        return if (bad || seen.any { !it }) null else out
    }


    private class English(
        val strings: Map<String, String>,
        val plurals: Map<String, Array<String>>,
        val signature: String
    )

    private fun isLibraryName(name: String): Boolean {
        if (name in APP_PREFIX_EXCEPTIONS) return false
        return LIBRARY_PREFIXES.any { name == it || name.startsWith(it) }
    }

    private fun collectEnglish(app: Context): English {
        val res = app.resources
        val strings = LinkedHashMap<String, String>()
        for (f in R.string::class.java.fields) {
            val name = f.name
            if (isLibraryName(name)) continue
            val id = try { f.getInt(null) } catch (e: Exception) { continue }
            val value = try { rawText(res, id).toString() } catch (e: Exception) { continue }
            strings[name] = value
        }
        val plurals = LinkedHashMap<String, Array<String>>()
        for (f in R.plurals::class.java.fields) {
            val name = f.name
            if (isLibraryName(name)) continue
            val id = try { f.getInt(null) } catch (e: Exception) { continue }
            try {
                plurals[name] = arrayOf(rawPlural(res, id, 1).toString(), rawPlural(res, id, 2).toString())
            } catch (e: Exception) {
                continue
            }
        }
        val sig = StringBuilder().apply {
            strings.forEach { (k, v) -> append(k).append('\u0001').append(v).append('\u0002') }
            plurals.forEach { (k, v) -> append(k).append('\u0001').append(v[0]).append(v[1]).append('\u0002') }
        }.toString().hashCode().toString()
        return English(strings, plurals, sig)
    }

    private fun rawText(res: Resources, id: Int): CharSequence =
        if (res is TranslatingResources) res.rawText(id) else res.getText(id)

    private fun rawPlural(res: Resources, id: Int, qty: Int): CharSequence =
        if (res is TranslatingResources) res.rawPlural(id, qty) else res.getQuantityText(id, qty)

    private fun cacheFile(app: Context, lang: String) = File(app.filesDir, "i18n_$lang.json")

    private fun writeCache(
        app: Context,
        lang: String,
        english: English,
        strings: Map<String, String>,
        plurals: Map<String, Array<String>>
    ) {
        val root = JSONObject()
            .put("v", CACHE_VERSION)
            .put("sig", english.signature)
            .put("s", JSONObject().apply { strings.forEach { (k, v) -> put(k, v) } })
            .put("p", JSONObject().apply { plurals.forEach { (k, v) -> put(k, JSONArray().put(v[0]).put(v[1])) } })
        val tmp = File(app.filesDir, "i18n_$lang.json.tmp")
        tmp.writeText(root.toString(), Charsets.UTF_8)
        tmp.renameTo(cacheFile(app, lang))
    }

    private fun readCache(app: Context, lang: String, english: English): Loaded? {
        val file = cacheFile(app, lang)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optInt("v") != CACHE_VERSION || root.optString("sig") != english.signature) return null

            val res = app.resources
            val pkg = app.packageName
            val s = root.getJSONObject("s")
            val p = root.getJSONObject("p")

            val byId = HashMap<Int, String>()
            val enToTr = HashMap<String, String>()
            val trToEn = HashMap<String, String>()
            for ((name, en) in english.strings) {
                val tr = s.optString(name, "")
                if (tr.isEmpty()) continue
                val id = res.getIdentifier(name, "string", pkg)
                if (id == 0) continue
                byId[id] = tr
                if (tr != en) {
                    enToTr[en] = tr
                    trToEn.putIfAbsent(tr, en)
                }
            }
            val pById = HashMap<Int, Array<String>>()
            for (name in english.plurals.keys) {
                val arr = p.optJSONArray(name) ?: continue
                val id = res.getIdentifier(name, "plurals", pkg)
                if (id == 0 || arr.length() < 2) continue
                pById[id] = arrayOf(arr.getString(0), arr.getString(1))
            }
            Loaded(lang, byId, pById, enToTr, trToEn)
        } catch (e: Exception) {
            file.delete()
            null
        }
    }


    internal fun text(id: Int): String? = loaded?.strings?.get(id)

    internal fun plural(id: Int, quantity: Int): String? {
        val forms = loaded?.plurals?.get(id) ?: return null
        return if (quantity == 1) forms[0] else forms[1]
    }

    fun english(context: Context, id: Int): String {
        val r = context.resources
        return if (r is TranslatingResources) r.rawText(id).toString() else r.getString(id)
    }

    fun toEnglish(text: String): String = loaded?.trToEn?.get(text) ?: text

    @Synchronized
    fun wrap(base: Resources): Resources {
        if (loaded == null || base is TranslatingResources) return base
        val cached = wrapCache[base]
        if (cached != null && cached.first == base.configuration) return cached.second
        val wrapper = TranslatingResources(base)
        wrapCache[base] = Configuration(base.configuration) to wrapper
        return wrapper
    }

    fun translateTree(root: View) {
        val st = loaded ?: return
        fun go(v: View) {
            if (v is TextView && v !is EditText) {
                val t = v.text?.toString()
                if (!t.isNullOrEmpty()) st.enToTr[t]?.let { v.text = it }
            }
            if (v is TextView) {
                val h = v.hint?.toString()
                if (!h.isNullOrEmpty()) st.enToTr[h]?.let { v.hint = it }
            }
            v.contentDescription?.toString()?.let { cd -> st.enToTr[cd]?.let { v.contentDescription = it } }
            if (v is ViewGroup) for (i in 0 until v.childCount) go(v.getChildAt(i))
        }
        go(root)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}


@Suppress("DEPRECATION")
class TranslatingResources(base: Resources) : Resources(base.assets, base.displayMetrics, base.configuration) {

    internal fun rawText(id: Int): CharSequence = super.getText(id)
    internal fun rawPlural(id: Int, quantity: Int): CharSequence = super.getQuantityText(id, quantity)

    override fun getText(id: Int): CharSequence = AppTranslator.text(id) ?: super.getText(id)

    override fun getText(id: Int, def: CharSequence?): CharSequence? = AppTranslator.text(id) ?: super.getText(id, def)

    override fun getQuantityText(id: Int, quantity: Int): CharSequence =
        AppTranslator.plural(id, quantity) ?: super.getQuantityText(id, quantity)
}
