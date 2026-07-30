package com.example.myapplication

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SmsData(
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SmsRepository {
    private val _smsFlow = MutableSharedFlow<SmsData>(extraBufferCapacity = 10)
    val smsFlow: SharedFlow<SmsData> = _smsFlow.asSharedFlow()

    suspend fun emitSms(sender: String, message: String) {
        _smsFlow.emit(SmsData(sender, message))
    }
}