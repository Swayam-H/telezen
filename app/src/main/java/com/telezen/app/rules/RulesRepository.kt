package com.telezen.app.rules

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RulesRepository(private val context: Context) {
    private val overrideFile = File(context.filesDir, "rules_override.json")
    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<RulesFile> = _rules.asStateFlow()
    val current: RulesFile get() = _rules.value
    val isOverride: Boolean get() = overrideFile.exists()

    fun builtIn(): RulesFile =
        RulesParser.parse(context.assets.open("rules.json").bufferedReader().use { it.readText() })

    fun import(text: String): Result<RulesFile> = runCatching {
        val parsed = RulesParser.parse(text)
        overrideFile.writeText(text)
        _rules.value = parsed
        parsed
    }

    fun reset() {
        overrideFile.delete()
        _rules.value = builtIn()
    }

    private fun load(): RulesFile =
        if (overrideFile.exists()) runCatching { RulesParser.parse(overrideFile.readText()) }.getOrElse { builtIn() }
        else builtIn()
}
