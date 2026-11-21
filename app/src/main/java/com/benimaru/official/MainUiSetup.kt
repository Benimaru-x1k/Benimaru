package com.benimaru.official

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import es.dmoral.toasty.Toasty

internal fun MainActivity.fontify(view: View) {
    ResourcesCompat.getFont(this, R.font.lexendregular)?.let { applyAppFont(view, it) }
}

internal fun MainActivity.xl(v: String): String {
    val id = when (v) {
        "Cross" -> R.string.xl_cross
        "Dot" -> R.string.xl_dot
        "Circle" -> R.string.xl_circle
        "Cross with Circle" -> R.string.xl_cross_circle
        "Square" -> R.string.xl_square
        "Target" -> R.string.xl_target
        "Gap Cross" -> R.string.xl_gap_cross
        "Diamond" -> R.string.xl_diamond
        "Triangle" -> R.string.xl_triangle
        "White" -> R.string.xl_white
        "Black" -> R.string.xl_black
        "Red" -> R.string.xl_red
        "Green" -> R.string.xl_green
        "Blue" -> R.string.xl_blue
        "Yellow" -> R.string.xl_yellow
        "Cyan" -> R.string.xl_cyan
        "Magenta" -> R.string.xl_magenta
        "Tiny" -> R.string.xl_tiny
        "Small" -> R.string.xl_small
        "Medium" -> R.string.xl_medium
        "Large" -> R.string.xl_large
        "Extra Large" -> R.string.xl_xlarge
        else -> return v
    }
    return getString(id)
}

private const val TAG_ICON = "tweak_icon"
private const val TAG_STAR = "tweak_star"

private enum class TweakCategory(val colorRes: Int) {
    PERFORMANCE(R.color.catPerformance),
    DISPLAY(R.color.catDisplay),
    NETWORK(R.color.catNetwork),
    GAMING(R.color.catGaming),
    TOOLS(R.color.catTools)
}

private class TweakCard(val cardId: Int, val statusId: Int?, val category: TweakCategory)

private val TWEAK_CARDS = listOf(
    TweakCard(R.id.cardFixedPerformance, R.id.tvStatusFixedPerf, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardOptimizeSystem, R.id.tvStatusOptimize, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardThermalThrottling, R.id.tvStatusThermal, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardFstrim, R.id.tvStatusFstrim, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardDisableDevOptions, R.id.tvStatusDevOptions, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardRamBooster, R.id.tvStatusRamBooster, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardClearCache, R.id.tvStatusCache, TweakCategory.PERFORMANCE),
    TweakCard(R.id.cardFreezeApps, R.id.tvStatusFreeze, TweakCategory.PERFORMANCE),

    TweakCard(R.id.cardAdjustRefreshRate, R.id.tvStatusRefresh, TweakCategory.DISPLAY),
    TweakCard(R.id.cardFastAnimations, R.id.tvStatusAnimations, TweakCategory.DISPLAY),
    TweakCard(R.id.cardDisableBlurs, R.id.tvStatusBlurs, TweakCategory.DISPLAY),
    TweakCard(R.id.cardChangeResolution, R.id.tvStatusResolution, TweakCategory.DISPLAY),

    TweakCard(R.id.cardImproveNetwork, R.id.tvStatusNetwork, TweakCategory.NETWORK),
    TweakCard(R.id.cardCustomDns, R.id.tvStatusDns, TweakCategory.NETWORK),

    TweakCard(R.id.cardImproveTouch, R.id.tvStatusTouch, TweakCategory.GAMING),
    TweakCard(R.id.cardGamingDnd, R.id.tvStatusDnd, TweakCategory.GAMING),
    TweakCard(R.id.cardCrosshair, R.id.tvStatusCrosshair, TweakCategory.GAMING),
    TweakCard(R.id.cardRealtimeMonitor, R.id.tvStatusMonitor, TweakCategory.GAMING),
    TweakCard(R.id.cardAutoClick, R.id.tvStatusAutoClick, TweakCategory.GAMING),
    TweakCard(R.id.cardGameMode, R.id.tvStatusGameMode, TweakCategory.GAMING),
    TweakCard(R.id.cardFixGyro, R.id.tvStatusGyro, TweakCategory.GAMING),
    TweakCard(R.id.cardShaderOptimization, R.id.tvStatusShader, TweakCategory.GAMING),

    TweakCard(R.id.cardShellExecute, R.id.tvStatusShell, TweakCategory.TOOLS),
    TweakCard(R.id.cardApkInstall, null, TweakCategory.TOOLS),
    TweakCard(R.id.cardFlagSecure, null, TweakCategory.TOOLS),
    TweakCard(R.id.cardFreeform, null, TweakCategory.TOOLS),
    TweakCard(R.id.cardTextureCompression, R.id.tvStatusTexture, TweakCategory.TOOLS)
)

private fun reorderCards(container: ViewGroup, ordered: List<View>, firstIndex: Int) {
    var inOrder = true
    for ((offset, card) in ordered.withIndex()) {
        if (container.getChildAt(firstIndex + offset) !== card) {
            inOrder = false
            break
        }
    }
    if (inOrder) return

    val transition = container.layoutTransition
    container.layoutTransition = null
    var index = firstIndex
    for (card in ordered) {
        if (container.getChildAt(index) !== card) {
            container.removeView(card)
            container.addView(card, index)
        }
        index++
    }
    container.layoutTransition = transition
}

private fun searchableText(card: View): String {
    val sb = StringBuilder()
    fun walk(v: View) {
        if (v is TextView && v.id == View.NO_ID) sb.append(v.text).append(' ')
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }
    walk(card)
    return sb.toString().lowercase()
}

internal fun MainActivity.setupFilterChips() {
    val chipGroup = findViewById<ChipGroup>(R.id.chipGroupCategories)
    val container = findViewById<LinearLayout>(R.id.cardContainer)
    val noResults = findViewById<TextView>(R.id.tvNoResults)
    val etSearch = findViewById<EditText>(R.id.etSearch)
    val btnClear = findViewById<View>(R.id.btnClearSearch)
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)

    val customTypeface = ResourcesCompat.getFont(this, R.font.lexendregular)
    val chipIds = listOf(
        R.id.chipAll, R.id.chipFavorites, R.id.chipActive, R.id.chipPerformance,
        R.id.chipDisplay, R.id.chipNetwork, R.id.chipGaming, R.id.chipTools
    )
    chipIds.forEach { id ->
        findViewById<Chip>(id)?.typeface = customTypeface
    }
    etSearch.typeface = customTypeface

    val infoById = TWEAK_CARDS.associateBy { it.cardId }

    val cards = ArrayList<View>()
    for (i in 0 until container.childCount) {
        val child = container.getChildAt(i)
        if (infoById.containsKey(child.id)) cards.add(child)
    }

    val favorites = HashSet<String>(prefs.getStringSet(Prefs.KEY_FAVORITE_CARDS, emptySet<String>()) ?: emptySet<String>())
    fun keyOf(card: View): String = resources.getResourceEntryName(card.id)
    fun isFavorite(card: View): Boolean = favorites.contains(keyOf(card))

    fun isActiveTweak(info: TweakCard): Boolean {
        val statusId = info.statusId ?: return false
        val tv = findViewById<TextView>(statusId) ?: return false
        return tweakStateOf(statusId, tv.text.toString()) != TweakState.NEUTRAL
    }

    fun matchesChip(card: View, info: TweakCard): Boolean = when (chipGroup.checkedChipId) {
        R.id.chipFavorites -> isFavorite(card)
        R.id.chipActive -> isActiveTweak(info)
        R.id.chipPerformance -> info.category == TweakCategory.PERFORMANCE
        R.id.chipDisplay -> info.category == TweakCategory.DISPLAY
        R.id.chipNetwork -> info.category == TweakCategory.NETWORK
        R.id.chipGaming -> info.category == TweakCategory.GAMING
        R.id.chipTools -> info.category == TweakCategory.TOOLS
        else -> true
    }

    fun applyFilters() {
        val query = etSearch.text.toString().trim().lowercase()

        val ordered = cards.sortedBy { if (isFavorite(it)) 0 else 1 }
        reorderCards(container, ordered, container.indexOfChild(noResults) + 1)

        var visibleCount = 0
        for (card in ordered) {
            val info = infoById.getValue(card.id)
            val show = matchesChip(card, info) && (query.isEmpty() || searchableText(card).contains(query))
            card.visibility = if (show) View.VISIBLE else View.GONE
            if (show) visibleCount++
            card.findViewWithTag<View>(TAG_STAR)?.visibility = if (isFavorite(card)) View.VISIBLE else View.GONE
        }

        if (visibleCount == 0) {
            noResults.setText(
                when {
                    query.isNotEmpty() -> R.string.empty_search
                    chipGroup.checkedChipId == R.id.chipFavorites -> R.string.empty_favorites
                    chipGroup.checkedChipId == R.id.chipActive -> R.string.empty_active
                    else -> R.string.empty_search
                }
            )
            noResults.visibility = View.VISIBLE
        } else {
            noResults.visibility = View.GONE
        }
    }

    for (card in cards) {
        val info = infoById.getValue(card.id)
        card.findViewWithTag<ImageView>(TAG_ICON)?.let { icon ->
            ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(ContextCompat.getColor(this, info.category.colorRes))
            )
        }
        card.setOnLongClickListener {
            val key = keyOf(card)
            val added = favorites.add(key)
            if (!added) favorites.remove(key)
            prefs.edit().putStringSet(Prefs.KEY_FAVORITE_CARDS, HashSet<String>(favorites)).apply()
            hapticTick()
            Toasty.info(
                this,
                getString(if (added) R.string.toast_fav_added else R.string.toast_fav_removed),
                Toast.LENGTH_SHORT,
                true
            ).show()
            applyFilters()
            true
        }
    }

    chipGroup.setOnCheckedChangeListener { _, _ -> applyFilters() }

    etSearch.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            applyFilters()
        }
    })
    btnClear.setOnClickListener { etSearch.setText("") }
    etSearch.setOnEditorActionListener { v, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            v.clearFocus()
            WindowCompat.getInsetsController(window, v).hide(WindowInsetsCompat.Type.ime())
            true
        } else {
            false
        }
    }

    refreshCardFilter = {
        if (chipGroup.checkedChipId == R.id.chipActive) applyFilters()
    }

    applyFilters()
}

internal enum class TweakState { ACTIVE, NEUTRAL, WARN }

internal fun MainActivity.tweakStateOf(id: Int, text: String): TweakState {
    fun en(res: Int) = AppTranslator.english(this, res)
    val t = AppTranslator.toEnglish(text).removePrefix("Status:").trim()
    return when {
        t.isEmpty() || t.startsWith(en(R.string.w_default)) || t.startsWith(en(R.string.w_ready)) ||
            t.startsWith(en(R.string.w_unknown)) || t.startsWith(en(R.string.w_checking)) -> TweakState.NEUTRAL
        id == R.id.tvStatusDevOptions -> if (t.startsWith(en(R.string.w_enabled)) || t.contains(en(R.string.w_debug_on))) TweakState.WARN else TweakState.ACTIVE
        id == R.id.tvStatusThermal -> TweakState.WARN
        id == R.id.tvStatusBlurs -> if (t.startsWith(en(R.string.w_disabled))) TweakState.ACTIVE else TweakState.NEUTRAL
        t.startsWith(en(R.string.w_disabled)) -> TweakState.NEUTRAL
        else -> TweakState.ACTIVE
    }
}

internal fun MainActivity.setupStatusStyling() {
    val statusIds = listOf(
        R.id.tvStatusFixedPerf, R.id.tvStatusOptimize, R.id.tvStatusRefresh, R.id.tvStatusNetwork,
        R.id.tvStatusTouch, R.id.tvStatusAnimations, R.id.tvStatusBlurs, R.id.tvStatusDnd,
        R.id.tvStatusDevOptions, R.id.tvStatusResolution, R.id.tvStatusDns, R.id.tvStatusCrosshair,
        R.id.tvStatusMonitor, R.id.tvStatusGameMode, R.id.tvStatusThermal, R.id.tvStatusFstrim,
        R.id.tvStatusRamBooster, R.id.tvStatusCache, R.id.tvStatusFreeze, R.id.tvStatusAutoClick,
        R.id.tvStatusGyro, R.id.tvStatusShader, R.id.tvStatusTexture
    )
    val states = HashMap<Int, TweakState>()
    val green = ContextCompat.getColor(this, R.color.successGreen)
    val amber = ContextCompat.getColor(this, R.color.warningAmber)
    val neutral = ContextCompat.getColor(this, R.color.neutralGray)

    fun refresh() {
        val active = states.values.count { it != TweakState.NEUTRAL }
        findViewById<TextView>(R.id.tvActiveSummary)?.text = getString(R.string.summary_active, active, statusIds.size)
    }

    fun apply(tv: TextView, id: Int) {
        val st = tweakStateOf(id, tv.text.toString())
        states[id] = st
        val color = when (st) {
            TweakState.ACTIVE -> green
            TweakState.WARN -> amber
            TweakState.NEUTRAL -> neutral
        }
        tv.setTextColor(color)
        AppCompatResources.getDrawable(this, R.drawable.ic_status_dot)?.let { d ->
            val dot = DrawableCompat.wrap(d.mutate())
            DrawableCompat.setTint(dot, color)
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        }
        refresh()
        refreshCardFilter?.invoke()
    }

    statusIds.forEach { id ->
        val tv = findViewById<TextView>(id) ?: return@forEach
        apply(tv, id)
        tv.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { apply(tv, id) }
        })
    }
}

internal fun MainActivity.setupBottomBarScrollBehavior() {
    val scrollView = findViewById<NestedScrollView>(R.id.mainScrollView)
    val bottomBar = findViewById<View>(R.id.floatingBottomBar)
    var isBottomBarVisible = true

    scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
        if (scrollY > oldScrollY && isBottomBarVisible) {
            isBottomBarVisible = false
            val margin = (bottomBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
            bottomBar.animate()
                .translationY(bottomBar.height.toFloat() + margin)
                .setDuration(250)
                .start()
        } else if (scrollY < oldScrollY && !isBottomBarVisible) {
            isBottomBarVisible = true
            bottomBar.animate()
                .translationY(0f)
                .setDuration(250)
                .start()
        }
    }
}
