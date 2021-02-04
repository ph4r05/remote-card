package cz.muni.fi.crocs.rcard.server

import cardTools.Util
import com.beust.klaxon.JsonObject
import cz.muni.fi.crocs.rcard.common.byteToInt
import cz.muni.fi.crocs.rcard.server.card.CardManager
import cz.muni.fi.crocs.rcard.server.card.CardType
import cz.muni.fi.crocs.rcard.server.card.RunConfig
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.*
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Hex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.smartcardio.CommandAPDU
import javax.smartcardio.ResponseAPDU
import kotlin.coroutines.CoroutineContext

/**
 * Card type - physical / sim
 * Idx - card reader index. Physical cards - real card index, simulator - registered shared sim instances
 * key - session card instances, simulators created for given instance
 */
data class CardConnectorIdx(val ctype: CardType, val idx: Int, val key: String? = null)
data class CardConnectorHolder(val connector: CardManager)

open class Handler(val vertx: Vertx, val app: App) : CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val coroutineContext: CoroutineContext by lazy { vertx.dispatcher() }
    private var cscope: CoroutineScope = CoroutineScope(app.coroutineContext + SupervisorJob())

    private val globalCtx = vertx.dispatcher() + SupervisorJob()
    private val workExecutor = Executors.newFixedThreadPool(app.workerThreads)
    private val workerCtx = workExecutor.asCoroutineDispatcher() + SupervisorJob()

    // TODO: multiple simulators? register sims for multiple card readers indices, same principle. Just needs API to do the registration?
    // TODO: non-persistent sims? on the connection? Add session ID -> user-specific. Created on the fly, no sharing.
    private val currentConnections = AtomicInteger(0)
    private val cardsMap = ConcurrentHashMap<CardConnectorIdx, CardConnectorHolder>()

    @Suppress("unused")
    suspend fun <T> onGlobalCtx(runner: suspend CoroutineScope.() -> T): T {
        return withContext(globalCtx, runner)
    }

    @Suppress("unused")
    suspend fun <T> onWorkerCtx(runner: suspend CoroutineScope.() -> T): T {
        return withContext(workerCtx, runner)
    }

    @Suppress("unused")
    fun <T> onGlobalCtxSync(runner: suspend CoroutineScope.() -> T): T {
        return runBlocking(globalCtx) { supervisorScope { runner.invoke(this) } }
    }

    @Suppress("unused")
    fun onGlobalCtxAsync(runner: suspend CoroutineScope.() -> Unit): Job {
        return launch(globalCtx, CoroutineStart.DEFAULT) { supervisorScope { runner.invoke(this) } }
    }

    open fun numClients(): Int {
        return currentConnections.get()
    }

    open fun onClientConnect(): Int {
        return currentConnections.incrementAndGet()
    }

    open fun onClientDisconnect(): Int {
        return currentConnections.decrementAndGet()
    }

    open fun reset(key: CardConnectorIdx) {
        getMgr(key).disconnect(true)
    }

    open fun disconnect(key: CardConnectorIdx) {
        getMgr(key).disconnect(true)
    }

    /**
     * Resolves card manager by the given key.
     * Current implementation stores managers in a simple map.
     * In order to support card simulators generated on the fly per session, improve this logic (with entry expiration)
     */
    open fun getMgr(key: CardConnectorIdx): CardManager {
        synchronized(cardsMap) {
            cardsMap[key]?.let { return it.connector }

            val mgr = onCreateManager(key) ?: throw RuntimeException("Card manager creation failed")
            cardsMap[key] = CardConnectorHolder(mgr)
            return mgr
        }
    }

    /**
     * Creates a new card manager, override for
     */
    open fun onCreateManager(key: CardConnectorIdx): CardManager? {
        return app.onCreateManager(key) ?: CardManager(true, null)
    }

    /**
     * Change configuration of the card before connecting.
     * Can initialize simulator here
     */
    open fun configureCard(key: CardConnectorIdx, mrg: CardManager, cfg: RunConfig){

    }

    open fun openNew(key: CardConnectorIdx) {
        if (key.idx > 1024){
            throw RuntimeException("Reader index too high")
        }

        val cfg = RunConfig().apply {
            testCardType = key.ctype
            targetReaderIndex = key.idx
        }

        val mgr = getMgr(key)

        configureCard(key, mgr, cfg)
        app.configureCard(key, mgr, cfg)

        mgr.connect(cfg)
    }

    open fun send(key: CardConnectorIdx, cmd: CommandAPDU): ResponseAPDU {
        return getMgr(key).transmit(cmd)
    }

    /**
     * Preinitialize card managers - create simulator instances, for example
     */
    open fun preinitManagers() {

    }

    /**
     * Main client entry point, processes request, returns json response
     */
    open suspend fun actionHandler(req: JsonObject, response: JsonObject? = null): JsonObject {
        val resp = response ?: JsonObject()
        resp["num_connections"] = numClients()
        resp["result"] = 0

        when (val action = req.string("action")) {
            "ping" -> {
                return resp
            }
            "shutdown" -> {
                if (app.allowTerminate){
                    vertx.setTimer(1000) {
                        app.shutdownServer(10)
                    }
                } else {
                    resp["result"] = -1
                    resp["error"] = "not allowed"
                }
                return resp
            }
            "reset" -> {
                reset(getTarget(req))
                return resp
            }
            "disconnect" -> {
                disconnect(getTarget(req))
                return resp
            }
            "connect" -> {
                return onConnect(req, resp)
            }
            "is_connected" -> {
                val mgr = getMgr(getTarget(req))
                val isCon = mgr.isConnected.get()
                resp["connected"] = isCon
                resp["num_connections"] = currentConnections.get()
                resp["ctype"] = when(mgr.lastChannelType){
                    CardType.JCARDSIMLOCAL -> "sim"
                    CardType.PHYSICAL -> "card"
                    else -> "?"
                }
                return resp
            }
            "atr" -> {
                resp["atr"] = getMgr(getTarget(req)).atr()?.bytes?.let { Util.bytesToHex(it) }
            }
            "send" -> {
                return onSend(req, resp)
            }
            "select" -> {  // sugar
                return onSelect(req, resp)
            }
            else -> {
                logger.info("Unknown action: $action")
                resp["error"] = "UnknownAction"
                resp["result"] = -1
            }
        }

        return resp
    }

    open fun targetToCardType(target: String): CardType {
        return when {
            "sim".equals(target, true) -> {
                CardType.JCARDSIMLOCAL
            }
            "card".equals(target, true) -> {
                CardType.PHYSICAL
            }
            else -> {
                throw RuntimeException("No such target: $target")
            }
        }
    }

    open fun getTarget(req: JsonObject): CardConnectorIdx {
        val target = req["target"] as? String ?: "card"
        val idx = if (app.allowPickReader) (req["idx"] as? Int) ?: 0 else 0
        val ctype = targetToCardType(target)
        val session = req["csess"] as? String ?: ""
        if (ctype != CardType.JCARDSIMLOCAL && session.isNotBlank()){
            logger.warn("csess allowed only for simulated cards")
            throw RuntimeException("csess allowed only for simulated cards")
        }

        return CardConnectorIdx(ctype, idx)
    }

    open fun onConnect(req: JsonObject, resp: JsonObject): JsonObject {
        val ckey = getTarget(req)
        logger.info("Opening a new card connection to ${ckey.ctype} index ${ckey.idx}")
        openNew(ckey)
        return resp
    }

    open suspend fun onSend(req: JsonObject, resp: JsonObject): JsonObject {
        val apduHex: String = req["apdu"] as? String ?: throw RuntimeException("No APDU field")
        val apdu = Hex.decode(apduHex)

        val len = byteToInt(apdu[4])
        val apduData = if (len > 0) Arrays.copyOfRange(apdu, 5, 5 + len) else byteArrayOf()
        // val needsLE = len + 5 < apdu.size
        val cmd = CommandAPDU(byteToInt(apdu[0]), byteToInt(apdu[1]), byteToInt(apdu[2]), byteToInt(apdu[3]), apduData, 255)
        val target = getTarget(req)
        return txmit(target, cmd, resp)
    }

    open suspend fun onSelect(req: JsonObject, resp: JsonObject): JsonObject {
        val aid = Hex.decode(req["aid"] as? String ?: throw RuntimeException("No aid field"))
        val cmd = CommandAPDU(0x00, 0xa4, 0x04, 0x00, aid)
        val target = getTarget(req)
        return txmit(target, cmd, resp)
    }

    open suspend fun txmit(target: CardConnectorIdx, cmd: CommandAPDU, resp: JsonObject): JsonObject{
        try {
            val apduResp = onWorkerCtx { send(target, cmd) }
            resp["response"] = Util.bytesToHex(apduResp.bytes)
            resp["sw"] = apduResp.sw
            resp["sw_hex"] = Integer.toHexString(apduResp.sw.and(0xffff))
            resp["sw1"] = apduResp.sW1
            resp["sw2"] = apduResp.sW2
        } catch(e: Exception){
            logger.error("Exception during executing card command", e)
            resp["response"] = -2
            resp["error"] = "Exception during execution: ${e.localizedMessage}"
        }

        return resp
    }


}