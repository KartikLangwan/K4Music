package com.k4music.compose.preferences

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import android.util.Log

private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, error -> Log.e("K4Audio", "Unhandled background error", error) } + CoroutineName("PreferencesHolders"))

private val canWriteState get() = !Snapshot.current.readOnly && !Snapshot.current.root.readOnly

@Stable
data class SharedPreferencesProperty<T : Any>(
    private val name: String? = null,
    private val get: SharedPreferences.(key: String) -> T,
    private val set: SharedPreferences.Editor.(key: String, value: T) -> Unit,
    private val default: T
) : ReadWriteProperty<PreferencesHolder, T> {
    private val state = mutableStateOf(default)
    val stateFlow = MutableStateFlow(default)
    private var listener: OnSharedPreferenceChangeListener? = null

    private fun setState(newValue: T) {
        state.value = newValue
        stateFlow.update { newValue }
    }

    private inline val KProperty<*>.key get() = this@SharedPreferencesProperty.name ?: name

    private fun read(preferences: SharedPreferences, key: String): T = try {
        preferences.get(key)
    } catch (error: ClassCastException) {
        Log.w("Preferences", "Ignoring incompatible value for $key", error)
        default
    }

    override fun getValue(thisRef: PreferencesHolder, property: KProperty<*>): T {
        if (listener == null && canWriteState) {
            setState(read(thisRef, property.key))

            listener = OnSharedPreferenceChangeListener { preferences, key ->
                if (key != property.key || !canWriteState) return@OnSharedPreferenceChangeListener

                read(preferences, property.key).let {
                    if (it != state.value) setState(it)
                }
            }

            thisRef.registerOnSharedPreferenceChangeListener(listener)
        }
        return state.value
    }

    override fun setValue(thisRef: PreferencesHolder, property: KProperty<*>, value: T) {
        // Publish in tap order; apply writes memory immediately and persists off the UI thread.
        setState(value)
        thisRef.edit { set(property.key, value) }
    }
}

/**
 * A snapshottable, thread-safe, compose-first, extensible SharedPreferences wrapper that supports
 * virtually all types, and if it doesn't, one could simply type
 * `fun myNewType(...) = SharedPreferencesProperty(...)` and start implementing. Starts off as given
 * defaultValue until we are allowed to subscribe to SharedPreferences. Caution: the type of the
 * preference has to be [Stable], otherwise UB will occur.
 */
@Suppress("unused")
open class PreferencesHolder(
    application: Application,
    name: String,
    mode: Int = Context.MODE_PRIVATE
) : SharedPreferences by application.getSharedPreferences(name, mode) {
    fun boolean(
        defaultValue: Boolean,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getBoolean(it, defaultValue) },
        set = { k, v -> putBoolean(k, v) },
        default = defaultValue,
        name = name
    )

    fun string(
        defaultValue: String,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getString(it, null) ?: defaultValue },
        set = { k, v -> putString(k, v) },
        default = defaultValue,
        name = name
    )

    fun int(
        defaultValue: Int,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getInt(it, defaultValue) },
        set = { k, v -> putInt(k, v) },
        default = defaultValue,
        name = name
    )

    fun float(
        defaultValue: Float,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getFloat(it, defaultValue) },
        set = { k, v -> putFloat(k, v) },
        default = defaultValue,
        name = name
    )

    fun long(
        defaultValue: Long,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getLong(it, defaultValue) },
        set = { k, v -> putLong(k, v) },
        default = defaultValue,
        name = name
    )

    inline fun <reified T : Enum<T>> enum(
        defaultValue: T,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { key ->
            getString(key, null)
                ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
                ?: defaultValue
        },
        set = { k, v -> putString(k, v.name) },
        default = defaultValue,
        name = name
    )

    fun stringSet(
        defaultValue: Set<String>,
        name: String? = null
    ) = SharedPreferencesProperty(
        get = { getStringSet(it, null) ?: defaultValue },
        set = { k, v -> putStringSet(k, v) },
        default = defaultValue,
        name = name
    )

    @PublishedApi
    internal val defaultJson = Json {
        isLenient = true
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified Serializable : Any> json(
        defaultValue: Serializable,
        name: String? = null,
        json: Json = defaultJson
    ): SharedPreferencesProperty<Serializable> = SharedPreferencesProperty(
        get = { k ->
            // A half-written value from a killed process must fall back to the
            // default, never crash the launch reading it.
            getString(k, json.encodeToString(defaultValue))?.let {
                runCatching { json.decodeFromString<Serializable>(it) }.getOrNull()
            } ?: defaultValue
        },
        set = { k, v -> putString(k, json.encodeToString(v)) },
        default = defaultValue,
        name = name
    )
}
