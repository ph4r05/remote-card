package cz.muni.fi.crocs.rcard.gp

import apdu4j.CardBIBO
import apdu4j.TerminalManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import cz.muni.fi.crocs.rcard.client.CardManager
import cz.muni.fi.crocs.rcard.client.CardType
import cz.muni.fi.crocs.rcard.client.RunConfig
import cz.muni.fi.crocs.rcard.common.createSingleThreadDispatcher
import cz.muni.fi.crocs.rcard.common.runNoExc
import kotlinx.coroutines.CoroutineScope
import org.bouncycastle.asn1.x500.style.RFC4519Style.c
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import pro.javacard.gp.GPTool
import java.lang.IllegalArgumentException
import java.security.Security
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.smartcardio.Card
import javax.smartcardio.CardTerminal
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

open class GpWrapper : CliktCommand(treatUnknownOptionsAsArgs = true), CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val coroutineContext: CoroutineContext by lazy { createSingleThreadDispatcher() }

    // https://ajalt.github.io/clikt
    init { context { allowInterspersedArgs = false } }
    val cardType: String by option("--card-type",
        help="Card type to connect to")
        .default("remote")
    val remoteEndpoint: String? by option("--remote-card",
        help="Remote reader address endpoint")
    val readerIdx: Int by option("--remote-reader-idx",
        help="Remote reader index")
        .int().default(0)
    val remoteType: String by option("--remote-type",
        help="Remote reader type")
        .default("card")
    val viccPort: Int by option("--vicc-port",
        help="VSmartCard vicc port")
        .int().default(35963)
    val vsmartcardListen: Boolean by option("--listen", "--reversed",
        help="Listen on vicc-port")
        .flag(default=false)
    val arguments by argument().multiple()

    override fun run() {
        logger.info("Starting GP with arguments: ${arguments.joinToString(" ")}")
        val r = gpExec(arguments)
        exitProcess(r)
    }

    private fun resolveRemoteCard(): Card {
        val cfg = RunConfig.getDefaultConfig().apply {
            testCardType = when(cardType){
                "remote" -> CardType.REMOTE
                "card" -> CardType.PHYSICAL
                "vsmartcard" -> CardType.VSMARTCARD
                else -> throw RuntimeException("Unsupported card type $cardType")
            }
            remoteAddress = if (vsmartcardListen) null else remoteEndpoint
            targetReaderIndex = readerIdx
            remoteCardType = if ("sim" == remoteType) CardType.JCARDSIMLOCAL else CardType.PHYSICAL
            remoteViccPort = viccPort
        }

        logger.info("Connecting to the remote card")
        val mgr = CardManager(true, null)
        mgr.connect(cfg)
        return mgr.channel.card
    }

    private fun resolveCard(): Card {
        if (!remoteEndpoint.isNullOrBlank()){
            return resolveRemoteCard()
        }

        val tf = TerminalManager.getTerminalFactory()
        val reader = System.getenv("GP_READER")
        var t = TerminalManager.getInstance(tf.terminals()).dwim(reader, System.getenv("GP_READER_IGNORE"), emptyList())
        if (!t.isPresent) {
            System.err.println("Specify reader with -r/\$GP_READER")
            throw RuntimeException("Multiple readers found, Specify reader with -r/\$GP_READER")
        }
        t = t.map { e: CardTerminal? -> e }  // LoggingCardTerminal.getInstance(e)
        val c = t.get().connect("*")
        return c
    }

    private fun gpExec(args: List<String>): Int {
        var c: Card? = null
        try {
            c = resolveCard()
            return GPTool().run(CardBIBO.wrap(c), args.toTypedArray()).also {
                logger.debug("GP exec result: $it")
            }

        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid argument: ${e.message}")

        } catch (e: Exception) {
            logger.warn("Error: ${e.message}")

        } finally {
            c?.let{ runNoExc { c.disconnect(true) }}
        }

        return -10
    }

}

fun main(args: Array<String>) = GpWrapper().main(args)
