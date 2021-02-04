package cz.muni.fi.crocs.rcard.common

import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.Executors
import kotlin.experimental.and

inline fun <T> T.guard(block: T.() -> Unit): T {
    if (this == null) block(); return this
}

fun createSingleThreadDispatcher(name: String? = null): ExecutorCoroutineDispatcher {
    return Executors.newSingleThreadExecutor {
        if (name == null) Thread(it) else Thread(it, name)
    }.asCoroutineDispatcher()
}

fun coroutine(runner: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) { runner.invoke((this)) }

fun coroutineBg(runner: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Default, CoroutineStart.DEFAULT) { runner.invoke((this)) }

suspend fun <T> CoroutineScope.await(block: () -> Deferred<T>): T = block().await()
suspend fun <T> CoroutineScope.awaitAsync(block: () -> T): T = async { block() }.await()

val LOGGER: Logger = LoggerFactory.getLogger("cz.muni.fi.crocs.rcard.common.Utils")

inline fun <R> runNoExc(show: Boolean = false, action: () -> R): R? = try {
    action()
} catch (e: Exception) {
    if (show) LOGGER.error("Exception", e)
    null
} catch (e: Throwable) {
    if (show) LOGGER.error("Exception", e)
    null
}

fun byteToInt(b: Byte): Int {
    return b.and(0xff.toByte()).toInt().let { if (it >= 0) it else 256 + it }
}
