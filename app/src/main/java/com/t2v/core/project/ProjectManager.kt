package com.t2v.core.project

import com.t2v.core.text.TextProcessor
import com.t2v.tts.VoiceConfig
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Импорт документов. Прямой порт `app/core/project_manager.py`.
 *
 * Поддерживает:
 *   - .txt
 *   - .md
 *   - .docx (через ZIP-парсинг, без python-docx)
 */
class ProjectManager(
    private val textProcessor: TextProcessor = TextProcessor(),
) {

    sealed class ImportResult {
        data class Ok(val document: ImportedDocument) : ImportResult()
        data class Failed(val message: String) : ImportResult()
    }

    fun importFile(file: File): ImportResult {
        return try {
            when (file.extension.lowercase()) {
                "txt" -> ImportResult.Ok(ImportedDocument(file.nameWithoutExtension, file.readText(Charsets.UTF_8), "txt"))
                "md" -> ImportResult.Ok(ImportedDocument(file.nameWithoutExtension, file.readText(Charsets.UTF_8), "md"))
                "docx" -> {
                    val text = readDocx(file)
                    ImportResult.Ok(ImportedDocument(file.nameWithoutExtension, text, "docx"))
                }
                else -> ImportResult.Failed("Unsupported format: " + file.extension)
            }
        } catch (t: Throwable) {
            ImportResult.Failed(t.message ?: "Import failed")
        }
    }

    fun importText(name: String, text: String): ImportedDocument =
        ImportedDocument(name, text, "pasted")

    /**
     * Минимальный парсер DOCX. Файл — это ZIP, в нём — word/document.xml.
     * Текст извлекается по параграфам <w:p>, внутри — <w:t>.
     */
    private fun readDocx(file: File): String {
        val zis = java.util.zip.ZipInputStream(file.inputStream())
        val sb = StringBuilder()
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                // Извлекаем параграфы
                val paraRegex = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
                for (para in paraRegex.findAll(xml)) {
                    val textRegex = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                    val line = textRegex.findAll(para.value).joinToString("") { it.groupValues[1] }
                    if (line.isNotEmpty()) sb.append(line).append("\n\n")
                }
                break
            }
            entry = zis.nextEntry
        }
        zis.close()
        if (sb.isEmpty()) error("Empty or invalid DOCX: ${file.name}")
        return sb.toString().trim()
    }
}

@Serializable
data class ImportedDocument(
    val title: String,
    val rawText: String,
    val source: String,  // "txt" | "md" | "docx" | "pasted"
) {
    /** Параметры проекта по умолчанию. */
    fun defaultVoiceConfig(): VoiceConfig = VoiceConfig.EMPTY
}
