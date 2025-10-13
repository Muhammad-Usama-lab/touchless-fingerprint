package com.biometrics.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.biometrics.model.BiometricsResult

class BiometricsSharedViewModel : ViewModel() {

    private val _result = MutableLiveData<BiometricsResult>()
    val result: LiveData<BiometricsResult> = _result

    var token: String? = null

    fun postResult(result: BiometricsResult) {
        _result.postValue(result)
    }
}
