package com.ostomate.app.resources

import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the string resource files themselves. Compose Multiplatform resources are not Android
 * resources: they unescape only \n, \t, \uXXXX and \\, so an Android-style \' or \" reaches the
 * screen with its backslash ("isn\'t").
 */
class StringResourcesTest {
    // The host test JVM runs in the composeApp module directory.
    private val resourceDirs =
        listOf(
            File("src/commonMain/composeResources"),
            File("../shared/src/commonMain/composeResources"),
        )

    private val stringFiles: List<File> =
        resourceDirs.flatMap { dir ->
            dir.listFiles().orEmpty().filter { it.name.startsWith("values") }.map { it.resolve("strings.xml") }
        }.filter { it.isFile }

    @Test
    fun noStringUsesAnEscapeComposeResourcesShowsLiterally() {
        assertTrue(stringFiles.size >= 3, "expected the composeApp and shared string files, found $stringFiles")
        val offenders =
            stringFiles.flatMap { file ->
                strings(file).filter { (_, text) -> literalEscapes(text).isNotEmpty() }.map { (name, text) ->
                    "${file.path}: $name has ${literalEscapes(text)} in \"$text\""
                }
            }
        assertEquals(emptyList(), offenders)
    }

    private fun literalEscapes(text: String): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\') {
                val next = text.getOrNull(i + 1)
                if (next == null || next !in UNESCAPED) found += "\\${next ?: ""}"
                i += 2
            } else {
                i++
            }
        }
        return found
    }

    private fun strings(file: File): List<Pair<String, String>> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private companion object {
        const val UNESCAPED = "ntu\\"
    }
}
