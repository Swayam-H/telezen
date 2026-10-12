package com.telezen.app.data

import android.content.Context
import com.telezen.app.core.StrictPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

sealed interface UpdateResult {
    data object Ok : UpdateResult
    data class Rejected(val reason: String) : UpdateResult
}

val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

class SettingsStore(context: Context) {
    private val file = File(context.filesDir, "settings.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): UpdateResult {
        val old = _state.value
        val new = transform(old)
        if (new == old) return UpdateResult.Ok
        StrictPolicy.violation(old, new, System.currentTimeMillis())?.let {
            return UpdateResult.Rejected(it)
        }
        _state.value = new
        save(new)
        return UpdateResult.Ok
    }

    fun exportJson(): String = SettingsJson.encodeToString(
        AppSettings.serializer(),
        current.copy(pinHash = null, pinSalt = null, strictUntil = 0L),
    )

    fun importJson(text: String): UpdateResult {
        val parsed = try {
            SettingsJson.decodeFromString(AppSettings.serializer(), text)
        } catch (e: Exception) {
            return UpdateResult.Rejected("That file isn't a TeleZen settings export.")
        }
        return update {
            parsed.copy(
                onboardingDone = true,
                pinHash = it.pinHash,
                pinSalt = it.pinSalt,
                strictUntil = it.strictUntil,
            )
        }
    }

    private fun load(): AppSettings = try {
        if (file.exists()) SettingsJson.decodeFromString(AppSettings.serializer(), file.readText())
        else AppSettings()
    } catch (e: Exception) {
        AppSettings()
    }

    private fun save(settings: AppSettings) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(SettingsJson.encodeToString(AppSettings.serializer(), settings))
        tmp.renameTo(file)
    }
}
