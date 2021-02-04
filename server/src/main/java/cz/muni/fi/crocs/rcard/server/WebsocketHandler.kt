package cz.muni.fi.crocs.rcard.server

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import io.vertx.core.AsyncResult
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.awaitEvent
import kotlinx.coroutines.*
import java.io.IOException

open class WebsocketHandler(private val parent: WebsocketServer, private val webSocket: ServerWebSocket) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val app = parent.app
    private val vertx = parent.vertx
    private var timerId: Long? = null
    private var periodicId: Long? = null
    private val clContext = parent.generateSessionId()
    private var cscope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob())

    // TODO: session tracking

    protected fun onGlobalCtxAsync(runner: suspend CoroutineScope.() -> Unit): Job {
        return cscope.launch(cscope.coroutineContext, CoroutineStart.DEFAULT) { supervisorScope { runner.invoke(cscope) } }
    }

    open fun initHooks(){
        logger.info("[SERVER][$clContext] received request" +
                "\t\npath:\t${webSocket.path()}" +
                "\t\nquery:\t${webSocket.query()}" +
                "\t\nheaders:\t${webSocket.headers()}" +
                "\t\nuri:\t${webSocket.uri()}")

        onGlobalCtxAsync {
            webSocket.textMessageHandler { textMessage ->
                onGlobalCtxAsync { onTextReceived(textMessage) }
            }

            webSocket.binaryMessageHandler { buffer ->
                onGlobalCtxAsync { onBinaryReceived(buffer) }
            }

            webSocket.closeHandler {
                onGlobalCtxAsync { onClose() }
            }

            webSocket.pongHandler { buffer ->
                onGlobalCtxAsync { onPongHandler(buffer) }
            }

            onOpen()
        }
    }

    protected open fun getHandler(): Handler {
        return parent.getHandler()
    }

    protected open fun onOpen() {
        getHandler().onClientConnect()
        resetPeriodic {
            try {
                logger.info("[$clContext] Send Ping")
                webSocket.writePing(Buffer.buffer("ping"))
            } catch(e: IllegalStateException){

            }
        }
    }

    protected open fun onClose(){
        logger.info("closing $clContext")
        getHandler().onClientDisconnect()
        cscope.cancel()
    }

    private fun resetTimer(close: () -> Unit) = synchronized(this) {
        timerId?.let { vertx.cancelTimer(it) }
        timerId = vertx.setTimer(15000L) { close() }
    }

    private fun resetPeriodic(sendPing: () -> Unit) = synchronized(this) {
        periodicId?.let { vertx.cancelTimer(it) }
        periodicId = vertx.setPeriodic(10000L) { sendPing() }
    }

    private fun cancelPeriodicPing() = synchronized(this) {
        periodicId?.let { vertx.cancelTimer(it) }
    }

    protected fun tryCloseWebSocket() {
        try {
            webSocket.close()
        } catch(e: Exception) {
            when {
                isWsClosedException(e) -> {
                    logger.debug("Exception in ws.close() - WebSocket is closed")
                }
                isConnectionResetByPeerException(e) -> {
                    logger.debug("Exception in ws.close() - ")
                }
                else -> {
                    logger.debug("Exception in ws.close() - Connection reset by peer", e)
                }
            }
        }
    }

    protected fun isWsClosedException(e: Exception): Boolean {
        val ex = e as? IllegalStateException ?: return false
        return "WebSocket is closed".equals(ex.message, true)
    }

    protected fun isConnectionResetByPeerException(e: Exception): Boolean {
        val ex = e as? IOException ?: return false
        return "Connection reset by peer".equals(ex.message, true)
    }

    protected fun onPongHandler(@Suppress("UNUSED_PARAMETER") buffer: Buffer){
        logger.info("[$clContext] Received Pong")
        resetTimer {
            cancelPeriodicPing()
            tryCloseWebSocket()
        }
    }

    private fun onBinaryReceived(@Suppress("UNUSED_PARAMETER") message: Buffer) {
        logger.info("[SERVER] binary message received")
    }

    protected open suspend fun onTextReceived(message: String) {
        logger.info("[SERVER] message received@[$clContext]: $message ")
        try {
            val sb = kotlin.text.StringBuilder(message)
            val json = Parser.default().parse(sb) as JsonObject
            handleReceivedMessage(json)
        } catch (e: Exception){
            logger.warn("General Exception: ${e.localizedMessage}", e)
            val resp = buildResp()
            resp["rcode"] = -1
            resp["error"] = "Exception: ${e.message}"
            sendTxt(resp)
        }
    }

    protected open suspend fun handleReceivedMessage(json: JsonObject) {
        try {
            val resp = textMsgHandler(json)
            if (resp != null) {
                sendTxt(resp)
            }
        } catch(e: Exception){
            logger.warn("General Exception: ${e.localizedMessage}", e)
            val resp = buildResp(json)
            resp["rcode"] = -1
            resp["error"] = "General exception: ${e.message}"
            sendTxt(resp)
        }
    }

    protected open suspend fun textMsgHandler(req: JsonObject): JsonObject? {
        val handler = getHandler()
        val resp = buildResp(req)
        return handler.actionHandler(req, resp)
    }

    protected open suspend fun sendTxt(resp: JsonObject, shouldAwait: Boolean = true){
        val jsonStr = resp.toJsonString()

        if (shouldAwait) {
            awaitEvent<AsyncResult<Void>> { handler ->
                logger.info("Sending: $jsonStr")
                webSocket.writeTextMessage(jsonStr, handler)
            }
        } else {
            logger.info("Sending: $jsonStr")
            webSocket.writeTextMessage(jsonStr)
        }
    }

    protected open fun buildResp(req: JsonObject?=null): JsonObject {
        val resp = JsonObject()
        resp["session"] = clContext
        resp["result"] = 0

        if (req?.containsKey("rid") == true){
            resp["rid"] = req["rid"]
        }

        return resp
    }

}