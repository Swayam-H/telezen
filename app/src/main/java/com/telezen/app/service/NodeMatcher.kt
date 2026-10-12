package com.telezen.app.service

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.telezen.app.rules.Matcher
import com.telezen.app.rules.RulesParser
import com.telezen.app.rules.TargetRule

object NodeMatcher {
    private const val FALLBACK_INTERVAL_MS = 2_000L
    private val regexCache = HashMap<String, Regex>()
    private var lastFallbackAt = 0L

    fun matches(root: AccessibilityNodeInfo, pkg: String, target: TargetRule): Boolean {
        if (target.wholeApp) return true
        return target.match.any { matchOne(root, pkg, it) }
    }

    private fun matchOne(root: AccessibilityNodeInfo, pkg: String, m: Matcher): Boolean = when (m.type) {
        "viewId" -> m.ids.any { id -> byId(root, pkg, id).any { accept(it, m) } }
        "desc" -> byText(root, m.value).any { n ->
            n.contentDescription?.toString()?.equals(m.value, ignoreCase = true) == true && accept(n, m)
        }
        "text" -> byText(root, m.value).any { n ->
            n.text?.toString()?.equals(m.value, ignoreCase = true) == true && accept(n, m)
        }
        "url" -> {
            val re = m.regex?.let { r -> regexCache.getOrPut(r) { Regex(r, RegexOption.IGNORE_CASE) } }
            re != null && m.ids.any { id ->
                byId(root, pkg, id).any { n -> n.text?.let { re.containsMatchIn(it) } == true }
            }
        }
        else -> false
    }

    private fun byId(root: AccessibilityNodeInfo, pkg: String, id: String): List<AccessibilityNodeInfo> =
        runCatching { root.findAccessibilityNodeInfosByViewId(RulesParser.fullViewId(pkg, id)) }.getOrNull().orEmpty()

    private fun byText(root: AccessibilityNodeInfo, value: String?): List<AccessibilityNodeInfo> =
        if (value.isNullOrBlank()) emptyList() else findText(root, value)

    /**
     * Nodes whose text or content description contains [value]. Jetpack Compose screens don't
     * implement the platform text search, so fall back to walking the tree ourselves.
     */
    fun findText(
        root: AccessibilityNodeInfo,
        value: String,
        limit: Int = 1500,
        force: Boolean = false,
    ): List<AccessibilityNodeInfo> {
        val platform = runCatching { root.findAccessibilityNodeInfosByText(value) }.getOrNull().orEmpty()
        if (platform.isNotEmpty()) return platform
        // The manual walk is costly; during video playback checks run several times a second.
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastFallbackAt < FALLBACK_INTERVAL_MS) return emptyList()
        lastFallbackAt = now
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst()
            seen++
            if (n.text?.contains(value, ignoreCase = true) == true ||
                n.contentDescription?.contains(value, ignoreCase = true) == true
            ) found += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return found
    }

    private fun accept(n: AccessibilityNodeInfo, m: Matcher): Boolean =
        (!m.visible || n.isVisibleToUser) && (m.selected == null || n.isSelected == m.selected)

    /** Compact dump of a window's view ids and labelled nodes, for "Report not working". */
    fun snapshot(root: AccessibilityNodeInfo, limit: Int = 400): String {
        val ids = LinkedHashSet<String>()
        val labels = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst()
            seen++
            n.viewIdResourceName?.let { ids += it.substringAfter(":id/") + if (n.isSelected) " [selected]" else "" }
            // Only labels of tappable or selected controls (tabs, buttons), never message or post text.
            val desc = n.contentDescription?.toString()
            if (!desc.isNullOrBlank() && desc.length < 30 && (n.isClickable || n.isSelected)) {
                labels += "desc=\"$desc\"" + if (n.isSelected) " [selected]" else ""
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return buildString {
            appendLine("ids: ${ids.joinToString(", ")}")
            append("labels: ${labels.joinToString(", ")}")
        }.take(6000)
    }
}
