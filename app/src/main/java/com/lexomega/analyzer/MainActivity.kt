package com.lexomega.analyzer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var report: TextView
    private lateinit var codeSection: TextView
    private lateinit var fixSection: TextView
    private lateinit var benefitSection: TextView

    private val indicators = listOf(
        "isPro", "isPremium", "premiumUser", "isVip",
        "vipLevel", "subscription", "entitlement",
        "BillingClient", "ProductDetails", "purchaseToken",
        "paywall", "featureAccess", "usageLimit",
        "quota", "trial", "SubscriptionManager"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.rgb(235, 239, 245))
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    private fun makeButton(value: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = value
            textSize = 16f
            setOnClickListener { action() }
        }
    }

    private fun baseLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(20))
            setBackgroundColor(Color.rgb(15, 20, 30))
        }
    }

    private fun showHome() {
        root = baseLayout()

        root.addView(makeText(
            "LEX-Ω UNIVERSAL ANALYZER",
            23f,
            true
        ).apply {
            gravity = Gravity.CENTER
        })

        root.addView(makeText(
            "محرك تحليل التطبيقات Android APK",
            16f
        ).apply {
            gravity = Gravity.CENTER
        })

        root.addView(makeText(
            "اختر ملف APK تملكه أو لديك تصريح بتحليله. " +
            "سيبحث المحرك عن مؤشرات الميزات والاشتراكات " +
            "ويعرض الأدلة والتوصيات وحدود التحليل.",
            15f
        ))

        root.addView(makeButton("اختيار ملف APK") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/vnd.android.package-archive"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, 100)
        })

        root.addView(makeText(
            "الإصدار 2.0 | Native Kotlin | Android",
            12f
        ))

        setContentView(root)
    }

    private fun showAnalysisScreen(fileName: String) {
        root = baseLayout()

        root.addView(makeText(
            "LEX-Ω | تقرير التحليل",
            22f,
            true
        ))

        root.addView(makeText(
            "الملف: $fileName",
            13f
        ))

        codeSection = makeText(
            "1 ـ مكان الكود\n\nفي انتظار نتائج التحليل...",
            15f
        )

        fixSection = makeText(
            "2 ـ التعديل المناسب\n\nسيتم عرض توصيات الإصلاح بناءً على الأدلة المكتشفة.",
            15f
        )

        benefitSection = makeText(
            "3 ـ فوائد التعديل\n\nسيتم توضيح أثر التوصيات على الأمان والاستقرار والأداء.",
            15f
        )

        report = makeText("جاري تجهيز التقرير...", 13f)

        addSection("مكان الكود", codeSection)
        addSection("التعديل المناسب", fixSection)
        addSection("فوائد التعديل", benefitSection)

        root.addView(makeText("ملخص الفحص", 18f, true))
        root.addView(report)

        root.addView(makeButton("اختيار APK آخر") {
            showHome()
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addSection(title: String, content: TextView) {
        root.addView(makeText(title, 18f, true))
        root.addView(content)
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
        showAnalysisScreen(uri.lastPathSegment ?: "APK")

        Thread {
            try {
                val temp = File(cacheDir, "selected.apk")

                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("تعذر قراءة ملف APK")

                val found = mutableListOf<String>()
                var dexCount = 0

                ZipFile(temp).use { zip ->
                    val entries = zip.entries()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()

                        if (!entry.name.matches(
                            Regex("classes(\\d*)?\\.dex")
                        )) continue

                        dexCount++

                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            val content = bytes.toString(Charsets.ISO_8859_1)

                            for (term in indicators) {
                                var index = content.indexOf(term)

                                while (index >= 0) {
                                    found.add(
                                        "${entry.name} | $term | byte offset $index"
                                    )
                                    index = content.indexOf(
                                        term,
                                        index + term.length
                                    )
                                }
                            }
                        }
                    }
                }

                val unique = found.distinct()

                val codeText = if (unique.isEmpty()) {
                    "لم يتم العثور على المؤشرات المحددة داخل ملفات DEX."
                } else {
                    unique.take(100).joinToString("\n")
                }

                val fixText =
                    "هذه مؤشرات أولية وليست إثباتاً على وجود ثغرة. " +
                    "راجع موضع التحقق من الاستحقاق، واجعل قرار منح " +
                    "الميزات المدفوعة معتمداً على تحقق موثوق من الخادم. " +
                    "لا تعدّل منطق الدفع لتجاوز التحقق أو منح استحقاق غير مستحق."

                val benefitText =
                    "• تقليل مخاطر التلاعب بالاستحقاقات.\n" +
                    "• تحسين سلامة التحقق من الاشتراك.\n" +
                    "• تقليل أخطاء انتهاء وتجديد الاشتراك.\n" +
                    "• تحسين وضوح منطق الوصول إلى الميزات."

                runOnUiThread {
                    codeSection.text =
                        "1 ـ مكان الكود\n\n" +
                        "ملفات DEX المكتشفة: $dexCount\n" +
                        "عدد المطابقات: ${unique.size}\n\n" +
                        codeText +
                        "\n\nملاحظة: إزاحات DEX ليست أرقام أسطر Smali."

                    fixSection.text =
                        "2 ـ التعديل المناسب\n\n$fixText"

                    benefitSection.text =
                        "3 ـ فوائد التعديل\n\n$benefitText"

                    report.text =
                        "اكتمل الفحص الأولي.\n" +
                        "المؤشرات المكتشفة: ${unique.size}\n" +
                        "ملفات DEX: $dexCount\n" +
                        "هذا الفحص لا يفك تشفير DEX إلى فئات ودوال."
                }

            } catch (e: Exception) {
                runOnUiThread {
                    report.text = "حدث خطأ أثناء التحليل: ${e.message}"
                }
            }
        }.start()
    }
}
