package com.franco.aihub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var grid: GridLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun saved(): List<String> =
        getSharedPreferences("hub", MODE_PRIVATE).getString("apps", "")!!
            .split(",").filter { it.isNotBlank() }

    private fun save(list: List<String>) {
        getSharedPreferences("hub", MODE_PRIVATE).edit()
            .putString("apps", list.joinToString(",")).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0E0E12")
        window.navigationBarColor = Color.parseColor("#0E0E12")

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0E0E12"))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(32), dp(16), dp(24))

        val title = TextView(this)
        title.text = "AI"
        title.textSize = 28f
        title.setTextColor(Color.WHITE)
        title.setPadding(dp(8), 0, 0, dp(16))

        grid = GridLayout(this)
        grid.columnCount = 3

        col.addView(title)
        col.addView(grid)
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        grid.removeAllViews()
        val pm = packageManager
        val tileW = (resources.displayMetrics.widthPixels - dp(32)) / 3
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
            grid.addView(
                tile(label, icon, tileW,
                    { startActivity(intent) },
                    { confirmRemove(pkg, label) })
            )
        }
        grid.addView(tile("Add apps", null, tileW, { showPicker() }, null))
    }

    private fun tile(
        label: String,
        icon: Drawable?,
        w: Int,
        onClick: () -> Unit,
        onLong: (() -> Unit)?
    ): View {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(dp(4), dp(12), dp(4), dp(12))
        val lp = GridLayout.LayoutParams()
        lp.width = w
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        t.layoutParams = lp

        if (icon != null) {
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
            iv.setImageDrawable(icon)
            t.addView(iv)
        } else {
            val plus = TextView(this)
            plus.layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
            plus.text = "+"
            plus.textSize = 32f
            plus.gravity = Gravity.CENTER
            plus.setTextColor(Color.WHITE)
            plus.setBackgroundColor(Color.parseColor("#2A2A33"))
            t.addView(plus)
        }

        val tv = TextView(this)
        tv.text = label
        tv.textSize = 12f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.setPadding(0, dp(8), 0, 0)
        t.addView(tv)

        t.setOnClickListener { onClick() }
        if (onLong != null) {
            t.setOnLongClickListener { onLong(); true }
        }
        return t
    }

    private fun confirmRemove(pkg: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove $label from this list?")
            .setMessage("The app itself stays installed.")
            .setPositiveButton("Remove") { _, _ ->
                save(saved().filter { it != pkg })
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPicker() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val current = saved()
        val checked = BooleanArray(apps.size) { apps[it].first in current }

        AlertDialog.Builder(this)
            .setTitle("Choose your AI apps")
            .setMultiChoiceItems(
                apps.map { it.second }.toTypedArray(), checked
            ) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                val chosen = apps.indices.filter { checked[it] }.map { apps[it].first }
                val ordered = current.filter { it in chosen } + chosen.filter { it !in current }
                save(ordered)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
