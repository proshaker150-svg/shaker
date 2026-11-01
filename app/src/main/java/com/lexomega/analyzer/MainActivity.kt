package com.lexomega.analyzer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface
import java.util.zip.ZipFile
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 24)
        }

        val title = TextView(this).apply {
            text = "LEX-Ω UNIVERSAL PRO ANALYZER"
            textSize = 21f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val choose = Button(this).apply {
            text = "SELECT APK"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/vnd.android.package-archive"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, 100)
            }
        }

        output = TextView(this).apply {
            text = "Ready. Select an authorized APK."
            textSize = 14f
            setPadding(8, 20, 8, 20)
        }

        layout.addView(title)
        layout.addView(choose)
        layout.addView(ScrollView(this).apply {
            addView(output)
        })

        setContentView(layout)
    }

    @Deprecated("Legacy callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { analyze(it) }
        }
    }

    private fun analyze(uri: Uri) {
        output.text = "Analyzing APK..."

        Thread {
            try {
                val temp = File(cacheDir, "selected.apk")

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { out ->
                        input.copyTo(out)
                    }
                } ?: throw Exception("Cannot read APK")

                val indicators = listOf(
                    "isPro",
                    "isPremium",
                    "premiumUser",
                    "isVip",
                    "vipLevel",
                    "subscription",
                    "entitlement",
                    "BillingClient",
                    "ProductDetails",
                    "purchaseToken",
                    "paywall",
                    "featureAccess",
                    "usageLimit",
                    "quota",
                    "trial"
                )

                val results = mutableListOf<String>()

                ZipFile(temp).use { zip ->
                    val entries = zip.entries()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()

                        if (!entry.name.matches(
                                Regex("classes(\\d*)?\\.dex")
                            )
                        ) continue

                        val bytes = zip.getInputStream(entry).use {
                            it.readBytes()
                        }

                        val content =
                            bytes.toString(Charsets.ISO_8859_1)

                        for (term in indicators) {
                            var index = content.indexOf(term)

                            while (index >= 0) {
                                results.add(
                                    "${entry.name}: $term at byte offset $index"
                                )

                                index = content.indexOf(
                                    term,
                                    index + term.length
                                )
                            }
                        }
                    }
                }

                val report = if (results.isEmpty()) {
                    "No matching indicators found."
                } else {
                    results.distinct().joinToString("\n")
                }

                runOnUiThread {
                    output.text = "SCAN COMPLETE\n\n$report"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    output.text = "ERROR: ${e.message}"
                }
            }
        }.start()
    }
}
