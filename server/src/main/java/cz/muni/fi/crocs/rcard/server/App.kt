package cz.muni.fi.crocs.rcard.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.licel.jcardsim.smartcardio.CardSimulator
import com.licel.jcardsim.utils.AIDUtil
import cz.muni.fi.crocs.rcard.common.createSingleThreadDispatcher
import cz.muni.fi.crocs.rcard.common.runNoExc
import cz.muni.fi.crocs.rcard.server.card.CardManager
import cz.muni.fi.crocs.rcard.server.card.CardType
import cz.muni.fi.crocs.rcard.server.card.RunConfig
import cz.muni.fi.crocs.rcard.server.demo.DemoApplet
import cz.muni.fi.crocs.rcard.server.demo.DemoApplet2
import io.vertx.core.Vertx
import io.vertx.core.logging.LoggerFactory
import io.vertx.kotlin.core.undeployAwait
import kotlinx.coroutines.*
import org.apache.commons.codec.binary.Hex
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

open class App : CliktCommand(), CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val coroutineContext: CoroutineContext by lazy { createSingleThreadDispatcher() }

    val bc = BouncyCastleProvider()
    protected lateinit var cardHandler: Handler

    // https://ajalt.github.io/clikt
    val debug: Boolean by option("--debug",
        help="Debugging mode on APDU level")
        .flag(default=false)
    val verbose: Boolean by option("--verbose",
        help="Verbose log level")
        .flag(default=false)
    val port: Int by option(
        help="WebSocket port to listen on")
        .int().default(9900)
    val webPort: Int by option(
        help="REST port to listen on")
        .int().default(9901)
    val workerThreads: Int by option("--workers",
        help="Number of worker threads to use")
        .int().default(5)
    val defaultReaderIndex: Int by option("--reader-idx",
        help="Default card reader index")
        .int().default(0)
    val allowTerminate: Boolean by option("--allow-terminate",
        help="Allow user to terminate ")
        .flag(default=false)
    val allowPickReader: Boolean by option("--allow-pick-reader",
        help="Allow user to pick reader index")
        .flag(default=false)

    lateinit var vertx: Vertx
    private val appCtx = createSingleThreadDispatcher("AppCtx")
    private val shuttingDown = AtomicBoolean(false)
    private var verticleWs: String? = null
    private var verticleRest: String? = null

    private fun loadConfig() {

    }

    override fun run() {
        Security.addProvider(bc)
        vertx = Vertx.vertx()

        prepareLogging()
        prepareSim()
        loadConfig()

        cardHandler = Handler(vertx, this)
        cardHandler.preinitManagers()

        deployVerticles()
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown hook triggered")
            shutdownServer(35)
        })

        logger.info("Server kick-off")
    }

    open fun prepareSim(){
        System.setProperty("com.licel.jcardsim.object_deletion_supported", "1")
        System.setProperty("com.licel.jcardsim.sign.dsasigner.computedhash", "1")
    }

    open fun prepareLogging(){
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
            System.setProperty("org.slf4j.simpleLogger.showThreadName", "false")
            System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true")
            System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true")
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
        }
    }

    open fun deployVerticles(){
        logger.info("Deploying vertices")
        vertx.deployVerticle(newWsServer()) {
            verticleWs = it.result()
            logger.info("WebSocket deployed: $verticleWs")
            if (verticleWs.isNullOrBlank()){
                logger.error("WebSocket deployment failed, terminating the server")
                shutdownServer()
            }
        }

        vertx.deployVerticle(newRestServer()) {
            verticleRest = it.result()
            logger.info("REST deployed: $verticleRest")
            if (verticleRest.isNullOrBlank()){
                logger.error("REST deployment failed, terminating the server")
                shutdownServer()
            }
        }
    }

    open fun newWsServer(): WebsocketServer {
        return WebsocketServer(vertx, this)
    }

    open fun newRestServer(): RestServer {
        return RestServer(vertx, this)
    }

    open fun getHandler(): Handler {
        return cardHandler
    }

    /**
     * Creates a new card manager, override for
     */
    open fun onCreateManager(key: CardConnectorIdx): CardManager? {
        // Init simulator here if needed
        return null
    }

    /**
     * Change configuration of the card before connecting.
     * Can initialize simulator here
     */
    open fun configureCard(key: CardConnectorIdx, mrg: CardManager, cfg: RunConfig){
        if (key.ctype != CardType.JCARDSIMLOCAL){
            return
        }

        val simulator = CardSimulator()

        // Install first demo applet
        val appletAID = AIDUtil.create(DemoApplet.APPLET_AID_BYTE)
        simulator.installApplet(appletAID, DemoApplet::class.java)

        // Installing applet 2 - demo that we can have more, select between those
        val appletAID2 = AIDUtil.create(DemoApplet2.APPLET_AID_BYTE)
        simulator.installApplet(appletAID2, DemoApplet2::class.java)

        cfg.simulator = simulator

        // If AID is provided to the config, we can have applet selected on connection
        //cfg.aid = DemoApplet.APPLET_AID_BYTE

        logger.info("Created sim for AID ${Hex.encodeHexString(DemoApplet.APPLET_AID_BYTE)} for DemoApplet")
    }

    open fun shutdownServer(code: Int = 3){
        runBlocking(appCtx) {
            if (shuttingDown.getAndSet(true)){
                return@runBlocking
            }

            undeployVerticles()
            logger.warn("Vertices stopped, terminating in 1 second")
            vertx.setTimer(1_000L) {
                logger.info("Terminating")
                exitProcess(code)
            }
        }
    }

    open suspend fun undeployVerticles(){
        logger.info("Stopping the server")
        verticleWs?.let {
            logger.info("Undeploying verticleWs: $verticleWs")
            runNoExc { withTimeout(5_000) { vertx.undeployAwait(it) } }
            verticleWs = null
        }
        verticleRest?.let {
            logger.info("Undeploying verticleRest: $verticleRest")
            runNoExc { withTimeout(5_000) { vertx.undeployAwait(it) } }
            verticleRest = null
        }
    }

    companion object {
        init {
            System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory") //::javaClass.name)
        }
    }
}

fun main(args: Array<String>) = App().main(args)
