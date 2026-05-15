package com.maimai.kt.service

import com.maimai.kt.api.TitleApiClient
import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.constants.UserDataKinds
import com.maimai.kt.payload.MusicDetail
import com.maimai.kt.payload.UserAllBuilder
import com.maimai.kt.payload.mergePatch

class FullPlayService(
    private val api: TitleApiClient,
    private val users: UserRepository,
) {
    private val builder = UserAllBuilder(api.config)

    suspend fun submit(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        musicDetails: List<MusicDetail>,
        patch: Map<String, Any?>,
        waitBeforeSubmit: Boolean = true,
        debug: Boolean = false,
    ): MutableMap<String, Any?> {
        val cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>
        val userData = users.getData(userId, cookie)
        val currentState = users.getRequiredState(userId, cookie)
        val userAll = builder.build(userId, loginResult, loginTimestamp, userData, musicDetails)
        builder.attachCurrentState(userAll, currentState)
        mergePatch(userAll, patch)
        if (debug) return userAll
        return api.upsertUserAll(userId, userAll, cookie)
    }

    suspend fun chargeList(userId: Long, cookie: Map<String, String>): List<MutableMap<String, Any?>> =
        users.get(userId, UserDataKinds.CHARGE, cookie)[PayloadKeys.USER_CHARGE_LIST] as List<MutableMap<String, Any?>>
}
