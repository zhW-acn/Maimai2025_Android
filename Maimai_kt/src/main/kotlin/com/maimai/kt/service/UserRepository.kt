package com.maimai.kt.service

import com.maimai.kt.api.TitleApiClient
import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.constants.UserDataKinds

class UserRepository(private val api: TitleApiClient) {
    suspend fun get(userId: Long, thing: String, cookie: Map<String, String>? = null): MutableMap<String, Any?> =
        api.getUser(userId, thing, cookie)

    suspend fun getData(userId: Long, cookie: Map<String, String>? = null): MutableMap<String, Any?> =
        get(userId, UserDataKinds.DATA, cookie)[PayloadKeys.USER_DATA] as MutableMap<String, Any?>

    suspend fun getRequiredState(userId: Long, cookie: Map<String, String>? = null): Map<String, MutableMap<String, Any?>> =
        mapOf(
            UserDataKinds.EXTEND to get(userId, UserDataKinds.EXTEND, cookie),
            UserDataKinds.OPTION to get(userId, UserDataKinds.OPTION, cookie),
            UserDataKinds.RATING to get(userId, UserDataKinds.RATING, cookie),
            UserDataKinds.ACTIVITY to get(userId, UserDataKinds.ACTIVITY, cookie),
            UserDataKinds.CHARGE to get(userId, UserDataKinds.CHARGE, cookie),
            UserDataKinds.MISSION_DATA to get(userId, UserDataKinds.MISSION_DATA, cookie),
        )
}
