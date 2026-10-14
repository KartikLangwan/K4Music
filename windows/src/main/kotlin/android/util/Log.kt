package android.util

import com.k4music.desktop.AppLog

/** Desktop stand-in for android.util.Log, used by the shared Android sources; writes to [AppLog]. */
object Log {
    fun d(tag: String, msg: String): Int = AppLog.write("D", tag, msg, null)
    fun i(tag: String, msg: String): Int = AppLog.write("I", tag, msg, null)
    fun w(tag: String, msg: String): Int = AppLog.write("W", tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable?): Int = AppLog.write("W", tag, msg, tr)
    fun e(tag: String, msg: String): Int = AppLog.write("E", tag, msg, null)
    fun e(tag: String, msg: String, tr: Throwable?): Int = AppLog.write("E", tag, msg, tr)
}
