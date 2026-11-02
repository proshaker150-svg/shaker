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
                        val buffer = ByteArray(8192)
                        var total = 0L
                        val maxSize = 200L * 1024 * 1024

                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break

                            total += count
                            if (total > maxSize) {
                                throw Exception("حجم APK يتجاوز 200 MB")
                            }

                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw Exception("تعذر قراءة ملف APK")

                val result = DexAnalyzer.analyze(temp, indicators)

                val codeText = if (result.matches.isEmpty()) {
                    "لم يتم العثور على مؤشرات مطابقة."
                } else {
                    result.matches.joinToString("\n\n")
                }

                val warningsText = if (result.warnings.isEmpty()) {
                    "لا توجد تحذيرات."
                } else {
                    result.warnings.joinToString("\n")
                }

                val fixText =
                    "راجع الكلاس والدالة المحددين في الأدلة. " +
                    "تحقق من صحة شروط الصلاحيات والاستحقاق، " +
                    "واجعل التحقق من الاشتراك معتمداً على مصدر موثوق. " +
                    "المؤشرات المكتشفة ليست وحدها إثباتاً لوجود ثغرة."

                val benefitText =
                    "• تحديد الكلاسات والدوال المرتبطة بالمؤشرات.\n" +
                    "• تسهيل مراجعة منطق الاشتراكات.\n" +
                    "• اكتشاف مواضع الثقة غير الآمنة المحتملة.\n" +
                    "• تحسين حماية الصلاحيات والاستحقاقات."

                runOnUiThread {
                    codeSection.text =
                        "1 ـ مكان الكود\n\n" +
                        "ملفات DEX: ${result.dexCount}\n" +
                        "عدد النتائج: ${result.matches.size}\n\n" +
                        codeText +
                        "\n\nالتحذيرات:\n$warningsText"

                    fixSection.text =
                        "2 ـ التعديل الأمني المقترح\n\n$fixText"

                    benefitSection.text =
                        "3 ـ فوائد الإصلاح\n\n$benefitText"

                    report.text =
                        "اكتمل تحليل DEX.\n" +
                        "ملفات DEX: ${result.dexCount}\n" +
                        "النتائج: ${result.matches.size}\n" +
                        "عناوين التعليمات ليست أرقام أسطر Smali."
                }

            } catch (e: Exception) {
                runOnUiThread {
                    report.text =
                        "حدث خطأ أثناء التحليل: ${e.message}"
                }
            }
        }.start()
    }

}