package com.example.entangle.data

import android.content.Context

/**
 * SettingsManager: Persistent User Preferences
 * 
 * Manages:
 * - Theme preferences (Dark/Light)
 * - Text scaling
 * - Security settings
 */

// Use SharedPreferences for simplicity in this project structure
// or DataStore if preferred. Using SharedPreferences here for speed as no DataStore dep was added.
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("entangle_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
        private const val KEY_TEXT_SIZE_SCALE = "text_size_scale"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_CHAT_BACKGROUND_INDEX = "chat_background_index"
        private const val KEY_SENDER_BUBBLE_INDEX = "sender_bubble_index"
        private const val KEY_RECEIVER_BUBBLE_INDEX = "receiver_bubble_index"
    }

    // Theme (Observable)
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_THEME, true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DARK_THEME, enabled).apply()
        _isDarkTheme.value = enabled
    }

    // Text Size (1.0f = default)
    private val _textSizeScale = MutableStateFlow(prefs.getFloat(KEY_TEXT_SIZE_SCALE, 1.0f))
    val textSizeScale: StateFlow<Float> = _textSizeScale.asStateFlow()

    fun setTextSizeScale(scale: Float) {
        prefs.edit().putFloat(KEY_TEXT_SIZE_SCALE, scale).apply()
        _textSizeScale.value = scale
    }

    // Chat Background Index
    private val _chatBackgroundIndex = MutableStateFlow(prefs.getInt(KEY_CHAT_BACKGROUND_INDEX, 0))
    val chatBackgroundIndex: StateFlow<Int> = _chatBackgroundIndex.asStateFlow()

    fun setChatBackgroundIndex(index: Int) {
        prefs.edit().putInt(KEY_CHAT_BACKGROUND_INDEX, index).apply()
        _chatBackgroundIndex.value = index
    }

    // Message Bubble Indices
    private val _senderBubbleIndex = MutableStateFlow(prefs.getInt(KEY_SENDER_BUBBLE_INDEX, 0))
    val senderBubbleIndex: StateFlow<Int> = _senderBubbleIndex.asStateFlow()

    fun setSenderBubbleIndex(index: Int) {
        prefs.edit().putInt(KEY_SENDER_BUBBLE_INDEX, index).apply()
        _senderBubbleIndex.value = index
    }

    private val _receiverBubbleIndex = MutableStateFlow(prefs.getInt(KEY_RECEIVER_BUBBLE_INDEX, 0))
    val receiverBubbleIndex: StateFlow<Int> = _receiverBubbleIndex.asStateFlow()

    fun setReceiverBubbleIndex(index: Int) {
        prefs.edit().putInt(KEY_RECEIVER_BUBBLE_INDEX, index).apply()
        _receiverBubbleIndex.value = index
    }

    // Biometric Security
    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
}
