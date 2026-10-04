package com.k4music.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launch-crash guard. Every component name the manifest declares must
 * resolve to a real class: manifest lives in the namespace package, Kotlin
 * classes live in their own package, and a rename that touches one without
 * the other used to ship as "app opens and dies" (ClassNotFoundException).
 */
class ManifestComponentsTest {

    @Test
    fun `every manifest component resolves to a declared class`() {
        var dir: File? = File(".").absoluteFile
        var manifest: File? = null
        while (dir != null && manifest == null) {
            manifest = listOf(
                File(dir, "src/main/AndroidManifest.xml"),
                File(dir, "app/src/main/AndroidManifest.xml")
            ).firstOrNull(File::exists)
            dir = dir.parentFile
        }
        val text = manifest!!.readText(Charsets.UTF_8)
        val srcMain = manifest.parentFile // .../src/main

        val components = Regex("android:name=\"(com\\.k4music[^\"]+)\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("no app components found in manifest", components.isNotEmpty())

        val declared = File(srcMain, "kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val src = file.readText(Charsets.UTF_8)
                val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE)
                    .find(src)?.groupValues?.get(1) ?: return@flatMap emptySequence()
                Regex("(?:class|object|interface)\\s+(\\w+)")
                    .findAll(src)
                    .map { pkg to it.groupValues[1] }
            }
            .toSet()

        for (component in components) {
            val pkg = component.substringBeforeLast('.')
            val outer = component.substringAfterLast('.').substringBefore('$')
            assertTrue(
                "manifest declares $component but no class $outer exists in package $pkg",
                (pkg to outer) in declared
            )
        }
    }
}
