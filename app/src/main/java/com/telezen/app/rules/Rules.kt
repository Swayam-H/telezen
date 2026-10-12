package com.telezen.app.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RulesFile(
    val version: Int,
    val updated: String = "",
    val targets: List<TargetRule>,
) {
    val packages: Set<String> by lazy { targets.flatMap { it.packages }.toSet() }
    fun target(id: String) = targets.firstOrNull { it.id == id }
}

@Serializable
data class TargetRule(
    val id: String,
    val name: String,
    val description: String = "",
    val packages: List<String>,
    /** Highest app version (by major number) these rules were checked against, per package. */
    val testedVersions: Map<String, String> = emptyMap(),
    val wholeApp: Boolean = false,
    /** Warn when heavy use of the app never triggers this rule. Off for apps used mostly for other things. */
    val staleCheck: Boolean = true,
    val experimental: Boolean = false,
    val match: List<Matcher> = emptyList(),
)

@Serializable
data class Matcher(
    /** One of: viewId, desc, text, url. */
    val type: String,
    val ids: List<String> = emptyList(),
    val value: String? = null,
    val regex: String? = null,
    val selected: Boolean? = null,
    val visible: Boolean = true,
)

object RulesParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val types = setOf("viewId", "desc", "text", "url")

    fun parse(text: String): RulesFile {
        val rules = json.decodeFromString(RulesFile.serializer(), text)
        require(rules.targets.isNotEmpty()) { "Rules file has no targets" }
        require(rules.targets.map { it.id }.toSet().size == rules.targets.size) { "Duplicate target ids" }
        for (t in rules.targets) {
            require(t.packages.isNotEmpty()) { "Target ${t.id} has no packages" }
            require(t.wholeApp || t.match.isNotEmpty()) { "Target ${t.id} has no matchers" }
            for (m in t.match) {
                require(m.type in types) { "Unknown matcher type '${m.type}' in ${t.id}" }
                when (m.type) {
                    "viewId" -> require(m.ids.isNotEmpty()) { "viewId matcher without ids in ${t.id}" }
                    "desc", "text" -> require(!m.value.isNullOrBlank()) { "${m.type} matcher without value in ${t.id}" }
                    "url" -> {
                        require(m.ids.isNotEmpty() && !m.regex.isNullOrBlank()) { "url matcher needs ids and regex in ${t.id}" }
                        Regex(m.regex!!)
                    }
                }
            }
        }
        return rules
    }

    fun fullViewId(pkg: String, id: String) = if (':' in id) id else "$pkg:id/$id"
}

object Versions {
    fun major(version: String?): Int? =
        version?.trim()?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** True when [installed] has a higher major version than every known-good version. */
    fun isNewerThanKnown(installed: String?, known: List<String?>): Boolean {
        val inst = major(installed) ?: return false
        val best = known.mapNotNull { major(it) }.maxOrNull() ?: return false
        return inst > best
    }
}
