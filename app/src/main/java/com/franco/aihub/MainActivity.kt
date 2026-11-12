package com.franco.aihub

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

class MainActivity : Activity() {

    private class Entry(val pkg: String, val label: String, val icon: Drawable)

    private lateinit var grid: GridLayout
    private lateinit var greeting: TextView
    private lateinit var hint: TextView
    private lateinit var trash: TextView
    private var firstRender = true

    private val cDim = Color.parseColor("#8A8AA0")
    private val cCard = Color.parseColor("#1C1C28")
    private val cStroke = Color.parseColor("#2D2D40")
    private val cAccent = Color.parseColor("#8B5CF6")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun saved(): List<String> =
        getSharedPreferences("hub", MODE_PRIVATE).getString("apps", "")!!
            .split(",").filter { it.isNotBlank() }

    private fun save(list: List<String>) {
        getSharedPreferences("hub", MODE_PRIVATE).edit()
            .putString("apps", list.joinToString(",")).apply()
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor)
        return g
    }

    private fun makeText(s: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun greetingText(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Late night"
            h < 12 -> "Good morning"
            h < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun trashStyle(active: Boolean) {
        if (active) {
            trash.background = rounded(Color.parseColor("#5A1F2E"), 20, Color.parseColor("#FF6B81"))
        } else {
            trash.background = rounded(Color.parseColor("#2A1620"), 20, Color.parseColor("#7A2A3A"))
        }
    }

    private fun persistOrder() {
        val order = (0 until grid.childCount).mapNotNull { grid.getChildAt(it).tag as? String }
        save(order)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0B12")
        window.navigationBarColor = Color.parseColor("#171126")

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0B0B12"), Color.parseColor("#171126"))
        )

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(48), dp(20), dp(140))

        greeting = makeText("", 14f, cDim)
        val title = makeText("Which AI today?", 30f, Color.WHITE, true)
        title.setPadding(0, dp(4), 0, dp(20))

        grid = GridLayout(this)
        grid.columnCount = 3

        hint = makeText("", 12f, cDim)
        hint.gravity = Gravity.CENTER_HORIZONTAL
        hint.setPadding(0, dp(20), 0, 0)

        col.addView(greeting)
        col.addView(title)
        col.addView(grid)
        col.addView(hint)
        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        trash = makeText("Drop here to remove", 14f, Color.parseColor("#FF9AAA"), true)
        trash.gravity = Gravity.CENTER
        trash.alpha = 0f
        trashStyle(false)
        val tlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM)
        tlp.setMargins(dp(24), 0, dp(24), dp(32))
        root.addView(trash, tlp)

        trash.setOnDragListener { _, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    trashStyle(true)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    trashStyle(false)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val dragged = ev.localState as? View
                    if (dragged != null) grid.removeView(dragged)
                    true
                }
                else -> true
            }
        }

        root.setOnDragListener { _, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    trash.animate().alpha(1f).setDuration(150).start()
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    trash.animate().alpha(0f).setDuration(150).start()
                    val dragged = ev.localState as? View
                    if (dragged != null) dragged.alpha = 1f
                    persistOrder()
                    true
                }
                else -> false
            }
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        greeting.text = greetingText()
        grid.removeAllViews()
        val pm = packageManager
        val tileW = (resources.displayMetrics.widthPixels - dp(40)) / 3
        var i = 0
        for (pkg in saved()) {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            val label: String
            val icon: Drawable
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                icon = pm.getApplicationIcon(pkg)
            } catch (e: Exception) {
                continue
            }
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            val t = tile(label, tileW, iv) { startActivity(intent) }
            t.tag = pkg
            enableDrag(t)
            grid.addView(t)
            if (firstRender) enter(t, i)
            i++
        }
        val plus = makeText("+", 30f, cAccent, true)
        plus.gravity = Gravity.CENTER
        val addTile = tile("Add apps", tileW, plus) { showPicker() }
        grid.addView(addTile)
        if (firstRender) enter(addTile, i)
        firstRender = false
        hint.text = if (i == 0) "Tap + to add your AI apps" else "Hold and drag to rearrange"
    }

    private fun enter(v: View, i: Int) {
        v.alpha = 0f
        v.translationY = dp(16).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(260).start()
    }

    private fun tile(label: String, tileW: Int, content: View, onClick: () -> Unit): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(0, dp(10), 0, dp(10))
        val lp = GridLayout.LayoutParams()
        lp.width = tileW
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        t.layoutParams = lp

        val card = FrameLayout(this)
        card.background = rounded(cCard, 22, cStroke)
        card.layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        content.layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER)
        card.addView(content)
        t.addView(card)

        val tv = makeText(label, 12f, Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setPadding(dp(4), dp(8), dp(4), 0)
        t.addView(tv)

        t.setOnClickListener {
            card.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            onClick()
        }
        return t
    }

    private fun enableDrag(t: View) {
        t.setOnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            v.startDragAndDrop(
                ClipData.newPlainText("app", v.tag as String),
                View.DragShadowBuilder(v), v, 0
            )
            v.alpha = 0.3f
            true
        }
        t.setOnDragListener { v, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_ENTERED -> {
                    val dragged = ev.localState as? View
                    if (dragged != null && dragged !== v) {
                        val to = grid.indexOfChild(v)
                        grid.removeView(dragged)
                        grid.addView(dragged, to)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun showPicker() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(main, 0)
            .map { Entry(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        val current = saved()
        val selected = current.toMutableSet()
        var shown: List<Entry> = all

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(dp(20), dp(20), dp(20), dp(12))
        val r = dp(28).toFloat()
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#14141D"))
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = bg

        val title = makeText("Choose your apps", 20f, Color.WHITE, true)
        title.setPadding(0, 0, 0, dp(12))

        val search = EditText(this)
        search.hint = "Search apps"
        search.setHintTextColor(cDim)
        search.setTextColor(Color.WHITE)
        search.background = rounded(cCard, 14, cStroke)
        search.setPadding(dp(16), dp(12), dp(16), dp(12))
        search.isSingleLine = true
        search.inputType = InputType.TYPE_CLASS_TEXT

        val list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(Color.TRANSPARENT))

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val e = shown[position]
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(4), dp(10), dp(4), dp(10))
                val iv = ImageView(this@MainActivity)
                iv.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                iv.setImageDrawable(e.icon)
                val tv = makeText(e.label, 16f, Color.WHITE)
                tv.setPadding(dp(14), 0, dp(8), 0)
                tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val cb = CheckBox(this@MainActivity)
                cb.isChecked = e.pkg in selected
                cb.isClickable = false
                cb.isFocusable = false
                row.addView(iv)
                row.addView(tv)
                row.addView(cb)
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val e = shown[pos]
            if (!selected.add(e.pkg)) selected.remove(e.pkg)
            adapter.notifyDataSetChanged()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                shown = if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val done = makeText("Done", 16f, Color.WHITE, true)
        done.gravity = Gravity.CENTER
        done.background = rounded(cAccent, 16)
        done.setPadding(dp(16), dp(14), dp(16), dp(14))
        done.setOnClickListener {
            val ordered = current.filter { it in selected } +
                all.map { it.pkg }.filter { it in selected && it !in current }
            save(ordered)
            dialog.dismiss()
            render()
        }

        sheet.addView(title)
        sheet.addView(search)
        val listLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        listLp.topMargin = dp(12)
        sheet.addView(list, listLp)
        val doneLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        doneLp.topMargin = dp(12)
        sheet.addView(done, doneLp)

        dialog.setContentView(sheet)
        val sheetHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
    }
}
