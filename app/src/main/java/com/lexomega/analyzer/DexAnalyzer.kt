package com.lexomega.analyzer

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.FieldReference
import java.io.File
import java.util.zip.ZipFile

object DexAnalyzer {

    data class Result(
        val dexCount: Int,
        val matches: List<String>,
        val warnings: List<String>
    )

    private const val MAX_DEX_SIZE = 64L * 1024 * 1024
    private const val MAX_MATCHES = 300

    fun analyze(apk: File, indicators: List<String>): Result {
        val matches = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var dexCount = 0

        ZipFile(apk).use { zip ->
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                if (!entry.name.matches(
                    Regex("classes(\\d*)?\\.dex")
                )) continue

                if (entry.size < 0 || entry.size > MAX_DEX_SIZE) {
                    warnings.add("${entry.name}: تجاوز حد الحجم")
                    continue
                }

                try {
                    val dex = DexFileFactory.loadDexEntry(
                        apk,
                        entry.name,
                        false,
                        Opcodes.getDefault()
                    ).dexFile

                    dexCount++

                    for (classDef in dex.classes) {
                        val className = classDef.type
                            .removePrefix("L")
                            .removeSuffix(";")
                            .replace("/", ".")

                        for (method in classDef.methods) {
                            val implementation =
                                method.implementation ?: continue

                            val signature =
                                method.parameterTypes.joinToString(
                                    prefix = "(",
                                    postfix = ")${method.returnType}"
                                ) { it.toString() }

                            var address = 0

                            for (instruction in implementation.instructions) {
                                val reference =
                                    (instruction as? ReferenceInstruction)
                                        ?.reference

                                val referenceText = when (reference) {
                                    is StringReference -> reference.string
                                    is MethodReference ->
                                        "${reference.definingClass}->${reference.name}"
                                    is FieldReference ->
                                        "${reference.definingClass}->${reference.name}"
                                    else -> ""
                                }

                                val found = indicators.filter { term ->
                                    referenceText.contains(
                                        term,
                                        ignoreCase = true
                                    )
                                }

                                if (found.isNotEmpty() &&
                                    matches.size < MAX_MATCHES
                                ) {
                                    matches.add(
                                        """
                                        DEX: ${entry.name}
                                        Class: $className
                                        Method: ${method.name}
                                        Signature: $signature
                                        Indicators: ${found.distinct().joinToString(", ")}
                                        Opcode: ${instruction.opcode.name}
                                        Code address: 0x${address.toString(16)}
                                        --------------------------------
                                        """.trimIndent()
                                    )
                                }

                                address += instruction.codeUnits
                            }

                            if (matches.size >= MAX_MATCHES) break
                        }

                        if (matches.size >= MAX_MATCHES) break
                    }

                    if (matches.size >= MAX_MATCHES) {
                        warnings.add("تم الوصول إلى حد النتائج: $MAX_MATCHES")
                        break
                    }

                } catch (e: Exception) {
                    warnings.add(
                        "${entry.name}: ${e.message ?: "تعذر التحليل"}"
                    )
                }
            }
        }

        return Result(
            dexCount,
            matches,
            warnings
        )
    }
}
