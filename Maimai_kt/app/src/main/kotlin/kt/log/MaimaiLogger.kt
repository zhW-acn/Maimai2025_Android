package kt.log

interface MaimaiLogger {
    fun debug(message: String)
    fun error(message: String, throwable: Throwable? = null)

    object None : MaimaiLogger {
        override fun debug(message: String) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
