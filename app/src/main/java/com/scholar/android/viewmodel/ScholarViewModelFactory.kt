package com.scholar.android.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scholar.android.repository.ScholarRepository

/**
 * Factory for creating ScholarViewModel instances with custom dependencies.
 * Enables dependency injection for testing and configuration.
 */
class ScholarViewModelFactory(
    private val application: Application,
    private val repository: ScholarRepository = ScholarRepository()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScholarViewModel::class.java)) {
            return ScholarViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
