package com.example.posapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Layar penampil error saat aplikasi crash (dipicu oleh [CrashHandler]).
 *
 * SENGAJA tidak memakai Jetpack Compose, Hilt, atau tema aplikasi sama sekali — dibuat murni
 * dengan View bawaan Android. Alasannya: kalau penyebab crash ada di lapisan Compose/DI/tema,
 * activity ini tetap harus bisa tampil apa adanya, bukan ikut crash juga.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashText = intent.getStringExtra(EXTRA_CRASH_TEXT)
            ?: "Tidak ada detail error yang tersimpan."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B1B1B"))
            setPadding(32, 64, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Aplikasi mengalami crash"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Salin atau bagikan pesan di bawah ini untuk membantu perbaikan."
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 8, 0, 24)
        }
        root.addView(subtitle)

        val shareButton = Button(this).apply {
            text = "Bagikan Detail Error"
            setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, crashText)
                }
                startActivity(Intent.createChooser(shareIntent, "Bagikan detail error"))
            }
        }
        root.addView(shareButton)

        val closeButton = Button(this).apply {
            text = "Tutup Aplikasi"
            setOnClickListener {
                finishAffinity()
            }
        }
        root.addView(closeButton)

        val errorView = TextView(this).apply {
            text = crashText
            setTextColor(Color.parseColor("#FF8A80"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 0)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(errorView)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    companion object {
        const val EXTRA_CRASH_TEXT = "crash_text"
    }
}
