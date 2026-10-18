package com.benimaru.official

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

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

internal fun MainActivity.setupFilterChips() {
    val chipGroup = findViewById<ChipGroup>(R.id.chipGroupCategories)

    val customTypeface = ResourcesCompat.getFont(this, R.font.lexendregular)
    val chipIds = listOf(R.id.chipAll, R.id.chipPerformance, R.id.chipDisplay, R.id.chipNetwork, R.id.chipGaming)
    chipIds.forEach { id ->
        findViewById<Chip>(id)?.typeface = customTypeface
    }

    val performanceCards = listOf(R.id.cardFixedPerformance, R.id.cardOptimizeSystem, R.id.cardThermalThrottling, R.id.cardFstrim, R.id.cardDisableDevOptions, R.id.cardRamBooster, R.id.cardClearCache, R.id.cardFreezeApps)
    val displayCards = listOf(R.id.cardAdjustRefreshRate, R.id.cardFastAnimations, R.id.cardDisableBlurs, R.id.cardChangeResolution)
    val networkCards = listOf(R.id.cardImproveNetwork, R.id.cardCustomDns)
    val gamingCards = listOf(R.id.cardImproveTouch, R.id.cardGamingDnd, R.id.cardCrosshair, R.id.cardRealtimeMonitor, R.id.cardAutoClick, R.id.cardGameMode)

    val allCards = performanceCards + displayCards + networkCards + gamingCards

    chipGroup.setOnCheckedChangeListener { _, checkedId ->
        allCards.forEach { findViewById<View>(it)?.visibility = View.GONE }

        val visibleIds = when (checkedId) {
            R.id.chipPerformance -> performanceCards
            R.id.chipDisplay -> displayCards
            R.id.chipNetwork -> networkCards
            R.id.chipGaming -> gamingCards
            else -> allCards
        }

        visibleIds.forEach { id ->
            findViewById<View>(id)?.visibility = View.VISIBLE
        }
    }
}

internal enum class TweakState { ACTIVE, NEUTRAL, WARN }

internal fun MainActivity.setupStatusStyling() {
    val statusIds = listOf(
        R.id.tvStatusFixedPerf, R.id.tvStatusOptimize, R.id.tvStatusRefresh, R.id.tvStatusNetwork,
        R.id.tvStatusTouch, R.id.tvStatusAnimations, R.id.tvStatusBlurs, R.id.tvStatusDnd,
        R.id.tvStatusDevOptions, R.id.tvStatusResolution, R.id.tvStatusDns, R.id.tvStatusCrosshair,
        R.id.tvStatusMonitor, R.id.tvStatusGameMode, R.id.tvStatusThermal, R.id.tvStatusFstrim,
        R.id.tvStatusRamBooster, R.id.tvStatusCache, R.id.tvStatusFreeze, R.id.tvStatusAutoClick
    )
    val states = HashMap<Int, TweakState>()
    val green = Color.parseColor("#2E9E5B")
    val amber = Color.parseColor("#E09B00")

    fun stateOf(id: Int, text: String): TweakState {
        val t = text.removePrefix("Status:").trim()
        return when {
            t.isEmpty() || t.startsWith(getString(R.string.w_default)) || t.startsWith(getString(R.string.w_ready)) ||
                t.startsWith(getString(R.string.w_unknown)) || t.startsWith(getString(R.string.w_checking)) -> TweakState.NEUTRAL
            id == R.id.tvStatusDevOptions -> if (t.startsWith(getString(R.string.w_enabled))) TweakState.WARN else TweakState.ACTIVE
            id == R.id.tvStatusThermal -> TweakState.WARN
            id == R.id.tvStatusBlurs -> if (t.startsWith(getString(R.string.w_disabled))) TweakState.ACTIVE else TweakState.NEUTRAL
            t.startsWith(getString(R.string.w_disabled)) -> TweakState.NEUTRAL
            else -> TweakState.ACTIVE
        }
    }

    fun refresh() {
        val active = states.values.count { it != TweakState.NEUTRAL }
        findViewById<TextView>(R.id.tvActiveSummary)?.text = getString(R.string.summary_active, active, statusIds.size)
    }

    fun apply(tv: TextView, id: Int) {
        val st = stateOf(id, tv.text.toString())
        states[id] = st
        val color = when (st) {
            TweakState.ACTIVE -> green
            TweakState.WARN -> amber
            TweakState.NEUTRAL -> Color.parseColor("#8A8F98")
        }
        tv.setTextColor(color)
        AppCompatResources.getDrawable(this, R.drawable.ic_status_dot)?.let { d ->
            val dot = DrawableCompat.wrap(d.mutate())
            DrawableCompat.setTint(dot, color)
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        }
        refresh()
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
