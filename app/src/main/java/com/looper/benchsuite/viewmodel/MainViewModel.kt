package com.looper.benchsuite.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.looper.benchsuite.MyApp
import com.looper.benchsuite.R

class MainViewModel : ViewModel() {
    private val _title = MutableLiveData(MyApp.getAppContext()!!.getString(R.string.app_name))
    val title: LiveData<String> = _title

    private val _showBackButton = MutableLiveData(false)
    val showBackButton: LiveData<Boolean> = _showBackButton

    private val _showAboutButton = MutableLiveData(true)
    val showAboutButton: LiveData<Boolean> = _showAboutButton

    fun updateTitle(newTitle: String) {
        _title.value = newTitle
    }

    fun showBackButton(show: Boolean) {
        _showBackButton.value = show
    }

    fun showAboutButton(show: Boolean) {
        _showAboutButton.value = show
    }
}