package com.example.avtonetfinder

enum class ScanStatus {
    IDLE,
    LOADING,
    WAITING_FOR_DOM,
    PARSING,
    COMPLETED,
    FAILED,
    TIMEOUT
}

data class ScanProgress(
    val searchId: Int,
    val status: ScanStatus,
    val message: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0
)
