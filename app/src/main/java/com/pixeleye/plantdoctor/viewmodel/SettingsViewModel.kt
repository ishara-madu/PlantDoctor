package com.pixeleye.plantdoctor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pixeleye.plantdoctor.data.UserPreferences
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    private val _currentPrefs = MutableStateFlow(UserPreferences())
    val currentPrefs: StateFlow<UserPreferences> = _currentPrefs.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarEvent = MutableStateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?>(null)
    val snackbarEvent: StateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?> = _snackbarEvent.asStateFlow()

    init {
        viewModelScope.launch {
            repository.userPreferences.collect { prefs ->
                _currentPrefs.value = prefs
            }
        }
    }

    fun savePreferences(country: String, language: String, selectedAiLanguage: String) {
        viewModelScope.launch {
            _isSaving.value = true
            repository.saveUserPreferences(
                country = country,
                language = language,
                selectedAiLanguage = selectedAiLanguage,
                onboardingCompleted = true
            )
            _isSaving.value = false
            showSnackbar("Preferences saved successfully", com.pixeleye.plantdoctor.ui.components.SnackbarType.SUCCESS)
        }
    }

    fun consumeSnackbarEvent() {
        _snackbarEvent.value = null
    }

    fun showSnackbar(message: String, type: com.pixeleye.plantdoctor.ui.components.SnackbarType) {
        _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(message, type)
    }

    class Factory(
        private val repository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}
