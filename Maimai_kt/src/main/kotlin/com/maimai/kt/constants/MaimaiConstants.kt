package com.maimai.kt.constants

object EnvNames {
    const val REGION_ID = "MAIMAI_REGION_ID"
    const val REGION_NAME = "MAIMAI_REGION_NAME"
    const val PLACE_ID = "MAIMAI_PLACE_ID"
    const val PLACE_NAME = "MAIMAI_PLACE_NAME"
    const val CLIENT_ID = "MAIMAI_CLIENT_ID"
    const val TITLE_ENDPOINT = "MAIMAI_TITLE_ENDPOINT"
    const val AIME_ENDPOINT = "MAIMAI_AIME_ENDPOINT"
    const val AIME_COMMON_KEY = "MAIMAI_AIME_COMMON_KEY"
    const val CHIP_ID = "MAIMAI_CHIP_ID"
    const val TITLE_TIMEOUT_MILLIS = "MAIMAI_TITLE_TIMEOUT_MILLIS"
    const val MAX_RETRIES = "MAIMAI_MAX_RETRIES"
    const val VERIFY_TLS = "MAIMAI_VERIFY_TLS"
    const val USE_2024_API = "MAIMAI_USE_2024_API"
    const val WAIT_BEFORE_UPSERT_MILLIS = "MAIMAI_WAIT_BEFORE_UPSERT_MILLIS"
    const val MUSIC_DB_PATH = "MAIMAI_MUSIC_DB_PATH"
}

object DefaultValues {
    const val REGION_ID = 3
    const val REGION_NAME = "Shanghai"
    const val PLACE_ID = 1187
    const val PLACE_NAME = ""
    const val CLIENT_ID = "A63E01C2588"
    const val TITLE_ENDPOINT = "https://maimai-gm.wahlap.com:42081/Maimai2Servlet/"
    const val AIME_ENDPOINT = "http://ai.sys-allnet.cn/wc_aime/api/get_data"
    const val AIME_COMMON_KEY = "XcW5FW4cPArBXEk4vzKz3CIrMuA5EVVW"
    const val CHIP_ID = ""
    const val TITLE_TIMEOUT_MILLIS = 10_000L
    const val MAX_RETRIES = 3
    const val VERIFY_TLS = false
    const val USE_2024_API = false
    const val WAIT_BEFORE_UPSERT_MILLIS = 60_000L
    const val MUSIC_DB_PATH = "../Data/musicDB.json"
}

object CryptoConstants {
    const val AES_KEY_2024 = "n7bx6:@Fg_:2;5E89Phy7AyIcpxEQ:R@"
    const val AES_KEY_LEGACY = "o2U8F6<adcYl25f_qwx_n]5_qxRcbLN>"
    const val AES_IV_2024 = ";;KjR1C3hgB1ovXa"
    const val AES_IV_LEGACY = "AL<G:k:X6Vu7@_U]"
    const val OBFUSCATE_2024 = "BEs2D5vW"
    const val OBFUSCATE_LEGACY = "LatuAa81"
}

object ApiNames {
    const val USER_LOGIN = "UserLoginApi"
    const val USER_LOGOUT = "UserLogoutApi"
    const val GET_USER_PREFIX = "GetUser"
    const val API_SUFFIX = "Api"
    const val GET_USER_PREVIEW = "GetUserPreviewApi"
    const val GET_USER_MUSIC = "GetUserMusicApi"
    const val UPSERT_USER_ALL = "UpsertUserAllApi"
    const val UPSERT_CHARGE_LOG = "UpsertUserChargelogApi"
}

object PayloadKeys {
    const val COOKIE = "_cookie"
    const val USER_ID = "userId"
    const val AIME_USER_ID = "userID"
    const val USER_DATA = "userData"
    const val USER_CHARGE = "userCharge"
    const val USER_CHARGE_LIST = "userChargeList"
    const val USER_CHARGE_LOG = "userChargelog"
    const val USER_MUSIC_DETAIL_LIST = "userMusicDetailList"
    const val UPSERT_USER_ALL = "upsertUserAll"
    const val IS_NEW_MUSIC_DETAIL_LIST = "isNewMusicDetailList"
    const val ACCESS_CODE = "accessCode"
    const val REGION_ID = "regionId"
    const val PLACE_ID = "placeId"
    const val CLIENT_ID = "clientId"
    const val DATE_TIME = "dateTime"
    const val IS_CONTINUE = "isContinue"
    const val GENERIC_FLAG = "genericFlag"
    const val TOKEN = "token"
    const val TYPE = "type"
    const val SEGA_ID_AUTH_KEY = "segaIdAuthKey"
    const val NEXT_INDEX = "nextIndex"
    const val MAX_COUNT = "maxCount"
    const val RETURN_CODE = "returnCode"
    const val RESULT_CODE = "resultCode"
    const val STATUS = "status"
    const val CODE = "code"
    const val CHIP_ID = "chipID"
    const val OPEN_GAME_ID = "openGameID"
    const val KEY = "key"
    const val QR_CODE = "qrCode"
    const val TIMESTAMP = "timestamp"
    const val CHARGE_ID = "chargeId"
    const val PRICE = "price"
    const val PURCHASE_DATE = "purchaseDate"
    const val PLAY_COUNT = "playCount"
    const val PLAYER_RATING = "playerRating"
    const val STOCK = "stock"
    const val VALID_DATE = "validDate"
    const val MUSIC_ID = "musicId"
    const val LEVEL = "level"
    const val LAST_ROM_VERSION = "lastRomVersion"
    const val LAST_DATA_VERSION = "lastDataVersion"
    const val USER_ITEM_LIST = "userItemList"
    const val IS_NEW_ITEM_LIST = "isNewItemList"
    const val ITEM_KIND = "itemKind"
    const val ITEM_ID = "itemId"
    const val IS_VALID = "isValid"
}

object VersionDefaults {
    const val ROM_VERSION = "1.50.00"
    const val DATA_VERSION = "1.50.01"
}

object UserDataKinds {
    const val DATA = "Data"
    const val EXTEND = "Extend"
    const val OPTION = "Option"
    const val RATING = "Rating"
    const val ACTIVITY = "Activity"
    const val CHARGE = "Charge"
    const val MISSION_DATA = "MissionData"
}

object AimeConstants {
    const val OPEN_GAME_ID_MAID = "MAID"
    const val USER_AGENT = "WC_AIME_LIB"
    const val SGWC_PREFIX = "SGWCMAID"
    const val QR_PAYLOAD_LENGTH = 64
    const val SGWC_LENGTH = 84
    const val HEX_PATTERN = "^[0-9A-F]+$"
}

object HttpConstants {
    const val CONNECTION = "Connection"
    const val KEEP_ALIVE = "Keep-Alive"
    const val HOST = "Host"
    const val USER_AGENT = "User-Agent"
    const val CONTENT_TYPE = "Content-Type"
    const val APPLICATION_JSON = "application/json"
    const val MAI_ENCODING = "Mai-Encoding"
    const val MAI_ENCODING_VALUE = "1.53"
    const val ACCEPT_ENCODING = "Accept-Encoding"
    const val CHARSET = "Charset"
    const val UTF_8 = "UTF-8"
    const val CONTENT_ENCODING = "Content-Encoding"
    const val DEFLATE = "deflate"
    const val TITLE_HOST = "maimai-gm.wahlap.com:42081"
    const val TITLE_USER_AGENT_SEPARATOR = "#"
    const val COOKIE_SEPARATOR = ";"
    const val KEY_VALUE_SEPARATOR = "="
    const val TLS = "TLS"
}

object LoginCodes {
    const val SUCCESS = 1
    const val PLAYING = 100
    const val QR_REFRESH_REQUIRED = 102
}

object DatePatterns {
    const val AIME_TIMESTAMP = "yyMMddHHmmss"
    const val DATE_TIME = "yyyy-MM-dd HH:mm:ss"
}

object ZoneIds {
    const val TOKYO = "Asia/Tokyo"
    const val SHANGHAI = "Asia/Shanghai"
}
