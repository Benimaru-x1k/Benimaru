package com.benimaru.official

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout



internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

internal object Ui {
    fun accent(ctx: Context): Int = ContextCompat.getColor(ctx, R.color.primaryAccent)

    fun surface(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, Color.WHITE)

    fun onSurface(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

    fun onSurfaceVariant(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    fun outline(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY)

    fun tonalFill(ctx: Context): Int = ColorUtils.setAlphaComponent(onSurface(ctx), 0x12)

    fun rounded(color: Int, radiusPx: Int, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun ripple(ctx: Context, fill: Int, radiusPx: Int, strokeColor: Int = 0, strokePx: Int = 0): RippleDrawable {
        val mask = rounded(Color.BLACK, radiusPx)
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface(ctx), 0x22)),
            rounded(fill, radiusPx, strokeColor, strokePx),
            mask
        )
    }

    fun drawable(ctx: Context, @DrawableRes res: Int): Drawable? = AppCompatResources.getDrawable(ctx, res)
}

internal fun applyAppFont(view: View, font: Typeface) {
    when (view) {
        is TextInputLayout -> view.typeface = font
        is TextView -> view.setTypeface(font, view.typeface?.style ?: Typeface.NORMAL)
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) applyAppFont(view.getChildAt(i), font)
    }
}

enum class SheetStyle { PRIMARY, TONAL, DANGER, TEXT }

class Sheet(private val activity: Activity) {

    private class Btn(
        val label: String,
        val style: SheetStyle,
        val dismiss: Boolean,
        val onClick: (MaterialButton) -> Unit
    )

    private var titleText: CharSequence = ""
    private var messageText: CharSequence? = null
    private var iconRes = 0
    private var iconColor: Int? = null
    private val content = mutableListOf<View>()
    private val buttons = mutableListOf<Btn>()
    private var cancelable = true
    private var dismissListener: (() -> Unit)? = null

    lateinit var dialog: BottomSheetDialog
        private set
    var messageView: TextView? = null
        private set
    val buttonViews = mutableListOf<MaterialButton>()

    fun title(t: CharSequence) = apply { titleText = t }
    fun message(m: CharSequence?) = apply { messageText = m }
    fun icon(@DrawableRes res: Int, tint: Int? = null) = apply { iconRes = res; iconColor = tint }
    fun content(v: View) = apply { content += v }
    fun cancelable(b: Boolean) = apply { cancelable = b }
    fun onDismiss(cb: () -> Unit) = apply { dismissListener = cb }

    fun button(
        label: String,
        style: SheetStyle = SheetStyle.PRIMARY,
        dismiss: Boolean = true,
        onClick: (MaterialButton) -> Unit = {}
    ) = apply { buttons += Btn(label, style, dismiss, onClick) }

    fun isShowing(): Boolean = ::dialog.isInitialized && dialog.isShowing

    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) dialog.dismiss()
    }

    fun show(): Sheet {
        val ctx: Context = activity
        val accent = Ui.accent(ctx)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(24), ctx.dp(12), ctx.dp(24), ctx.dp(24))
        }


        root.addView(View(ctx).apply {
            background = Ui.rounded(Ui.outline(ctx), ctx.dp(2))
            layoutParams = LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = ctx.dp(16)
            }
        })


        if (iconRes != 0) {
            val tint = iconColor ?: accent
            val badge = FrameLayout(ctx).apply {
                background = Ui.rounded(ColorUtils.setAlphaComponent(tint, 0x26), ctx.dp(16))
                layoutParams = LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)).apply {
                    bottomMargin = ctx.dp(14)
                }
            }
            badge.addView(ImageView(ctx).apply {
                setImageDrawable(Ui.drawable(ctx, iconRes))
                imageTintList = ColorStateList.valueOf(tint)
                layoutParams = FrameLayout.LayoutParams(ctx.dp(26), ctx.dp(26), Gravity.CENTER)
            })
            root.addView(badge)
        }

        root.addView(TextView(ctx).apply {
            text = titleText
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.onSurface(ctx))
        })

        messageText?.let {
            messageView = TextView(ctx).apply {
                text = it
                textSize = 14f
                setLineSpacing(0f, 1.15f)
                setTextColor(Ui.onSurfaceVariant(ctx))
                setPadding(0, ctx.dp(6), 0, 0)
            }
            root.addView(messageView)
        }

        content.forEach { v ->
            val lp = (v.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (lp.topMargin == 0) lp.topMargin = ctx.dp(16)
            v.layoutParams = lp
            root.addView(v)
        }

        if (buttons.isNotEmpty()) {
            val horizontal = buttons.size == 2 && buttons.none { it.style == SheetStyle.TEXT }
            val bar = LinearLayout(ctx).apply {
                orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ctx.dp(24) }
            }
            buttons.forEachIndexed { i, b ->
                val btn = makeButton(ctx, b, accent)
                btn.layoutParams = if (horizontal) {
                    LinearLayout.LayoutParams(0, ctx.dp(52), 1f).apply {
                        if (i > 0) marginStart = ctx.dp(10)
                    }
                } else {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(52)).apply {
                        if (i > 0) topMargin = ctx.dp(8)
                    }
                }
                btn.setOnClickListener {
                    if (b.dismiss) dismiss()
                    b.onClick(btn)
                }
                buttonViews += btn
                bar.addView(btn)
            }
            root.addView(bar)
        }

        ResourcesCompat.getFont(ctx, R.font.lexendregular)?.let { applyAppFont(root, it) }

        val scroll = ScrollView(ctx).apply {
            isFillViewport = false
            addView(root)
        }

        dialog = BottomSheetDialog(ctx).apply {
            setContentView(scroll)
            setCancelable(cancelable)
            setCanceledOnTouchOutside(cancelable)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            behavior.skipCollapsed = true
            behavior.isHideable = cancelable
            behavior.isDraggable = cancelable
            setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
            setOnDismissListener { dismissListener?.invoke() }
            show()
        }
        return this
    }

    private fun makeButton(ctx: Context, b: Btn, accent: Int): MaterialButton {
        val btn = MaterialButton(ctx)
        return btn.apply {
            text = b.label
            isAllCaps = false
            textSize = 15f
            cornerRadius = ctx.dp(16)
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            when (b.style) {
                SheetStyle.PRIMARY -> {
                    backgroundTintList = ColorStateList.valueOf(accent)
                    setTextColor(Color.WHITE)
                }
                SheetStyle.DANGER -> {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
                    setTextColor(Color.WHITE)
                }
                SheetStyle.TONAL -> {
                    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x22))
                    setTextColor(accent)
                }
                SheetStyle.TEXT -> {
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    setTextColor(Ui.onSurfaceVariant(ctx))
                    elevation = 0f
                }
            }
        }
    }
}


internal fun styledField(
    ctx: Context,
    hint: String,
    inputType: Int,
    password: Boolean = false,
    topMarginDp: Int = 12
): Pair<TextInputLayout, TextInputEditText> {
    val layout = TextInputLayout(ctx).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        val r = ctx.dp(14).toFloat()
        setBoxCornerRadii(r, r, r, r)
        if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(topMarginDp) }
    }
    val edit = TextInputEditText(layout.context).apply {
        this.inputType = inputType
        maxLines = 1
    }
    layout.addView(edit)
    return layout to edit
}


internal fun infoCard(ctx: Context, text: CharSequence, tint: Int? = null): TextView =
    TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setLineSpacing(0f, 1.15f)
        setTextColor(if (tint != null) Ui.onSurface(ctx) else Ui.onSurfaceVariant(ctx))
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        background = Ui.rounded(
            if (tint != null) ColorUtils.setAlphaComponent(tint, 0x1F) else Ui.tonalFill(ctx),
            ctx.dp(14)
        )
    }


internal fun optionRow(
    ctx: Context,
    title: CharSequence,
    subtitle: CharSequence? = null,
    icon: Drawable? = null,
    tintIcon: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
): LinearLayout {
    val accent = Ui.accent(ctx)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        background = if (selected)
            Ui.ripple(ctx, ColorUtils.setAlphaComponent(accent, 0x1F), ctx.dp(16), accent, ctx.dp(1))
        else
            Ui.ripple(ctx, Ui.tonalFill(ctx), ctx.dp(16))
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dp(8) }

        if (icon != null) {
            addView(ImageView(ctx).apply {
                setImageDrawable(icon)
                if (tintIcon) imageTintList = ColorStateList.valueOf(accent)
                layoutParams = LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)).apply { marginEnd = ctx.dp(14) }
            })
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(ctx).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.onSurface(ctx))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (subtitle != null) {
            texts.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(Ui.onSurfaceVariant(ctx))
                setPadding(0, ctx.dp(2), 0, 0)
            })
        }
        addView(texts)

        if (selected) {
            addView(TextView(ctx).apply {
                text = "✓"
                textSize = 18f
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        setOnClickListener { onClick() }
    }
}


class ProgressSheet(private val activity: Activity) {
    private val ctx: Context = activity
    private val bar = LinearProgressIndicator(ctx).apply {
        isIndeterminate = false
        max = 100
        trackThickness = ctx.dp(8)
        trackCornerRadius = ctx.dp(4)
        setIndicatorColor(Ui.accent(ctx))
        trackColor = ColorUtils.setAlphaComponent(Ui.accent(ctx), 0x26)
    }
    private val label = TextView(ctx).apply {
        textSize = 13f
        setTextColor(Ui.onSurfaceVariant(ctx))
        setPadding(0, ctx.dp(10), 0, 0)
        text = "Starting…"
    }
    private val sheet = Sheet(activity)

    fun title(t: String) = apply { sheet.title(t) }
    fun message(m: String) = apply { sheet.message(m) }
    fun icon(@DrawableRes res: Int) = apply { sheet.icon(res) }
    fun cancelButton(label: String, onCancel: () -> Unit) = apply {
        sheet.button(label, SheetStyle.TONAL) { onCancel() }
    }

    fun show(indeterminate: Boolean = false): ProgressSheet {
        bar.isIndeterminate = indeterminate
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(label)
        }
        sheet.cancelable(false).content(box).show()
        return this
    }

    fun update(percent: Int, text: String) {
        if (bar.isIndeterminate) bar.isIndeterminate = false
        bar.setProgressCompat(percent.coerceIn(0, 100), true)
        label.text = text
    }

    fun setIndeterminate(text: String) {
        bar.isIndeterminate = true
        label.text = text
    }

    fun dismiss() = sheet.dismiss()
}
