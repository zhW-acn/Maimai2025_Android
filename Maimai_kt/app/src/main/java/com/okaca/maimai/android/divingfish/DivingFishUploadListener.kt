package com.okaca.maimai.android.divingfish

interface DivingFishUploadListener {
    fun onMessageReceived(message: String)
    fun onStartAuth()
    fun onFinishUpdate()
    fun onError(error: Throwable)
}
