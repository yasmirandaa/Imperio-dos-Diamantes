
package com.ifpr.androidapptemplatear.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedModelViewModel : ViewModel() {

    val selectedModel = MutableLiveData<String>()

    val pontuacao = MutableLiveData(0)

    private val modelosEncontrados = mutableSetOf<String>()

    fun selectModel(modelPath: String) {
        selectedModel.value = modelPath
    }

    fun registrarEncontro(modelPath: String) {
        val modeloNovo = modelosEncontrados.add(modelPath)

        if (modeloNovo) {
            pontuacao.value = (pontuacao.value ?: 0) + 10
        }
    }
}
