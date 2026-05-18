package com.example.avtonetfinder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScanManager {
    private val _scanProgress = MutableStateFlow<Map<Int, ScanProgress>>(emptyMap())
    val scanProgress: StateFlow<Map<Int, ScanProgress>> = _scanProgress.asStateFlow()

    private val _isAnyScanning = MutableStateFlow(false)
    val isAnyScanning: StateFlow<Boolean> = _isAnyScanning.asStateFlow()

    fun updateProgress(id: Int, status: ScanStatus, message: String = "", current: Int = 0, total: Int = 0) {
        val currentMap = _scanProgress.value.toMutableMap()
        currentMap[id] = ScanProgress(id, status, message, current, total)
        _scanProgress.value = currentMap
        
        _isAnyScanning.value = currentMap.values.any { 
            it.status != ScanStatus.IDLE && 
            it.status != ScanStatus.COMPLETED && 
            it.status != ScanStatus.FAILED && 
            it.status != ScanStatus.TIMEOUT 
        }
    }

    fun clearAll() {
        _scanProgress.value = emptyMap()
        _isAnyScanning.value = false
    }
}
