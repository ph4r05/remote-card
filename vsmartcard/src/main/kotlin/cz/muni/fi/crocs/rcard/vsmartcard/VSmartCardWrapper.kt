package cz.muni.fi.crocs.rcard.gp

import apdu4j.TerminalManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import cz.muni.fi.crocs.rcard.client.CardManager
import cz.muni.fi.crocs.rcard.client.CardType
import cz.muni.fi.crocs.rcard.client.RunConfig
import cz.muni.fi.crocs.rcard.client.protocols.VSmartCard
import cz.muni.fi.crocs.rcard.common.createSingleThreadDispatcher
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import javax.smartcardio.Card
import javax.smartcardio.CardTerminal
import kotlin.coroutines.CoroutineContext

/**
 * Wrapper for VSmartCard
 * http://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html
 *
 * Substitutes VICC.
 * http://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html#running-vpicc
 *
 * The VPCD connects to this endpoint, or the endpoint waits for connection from vpcd if --reversed is used.
 * This wrapper enables to present local/jcardsim/remote card to the vpcd so it can be accessed transparently on a
 * local system.
 *
 * Main benefit: enables use of REST-server backed
 * remote cards on a local system transparently as physical cards. The setup:
 *
 * PhysicalCard <---> Server <----REST----> VSmartCardWrapper <---> VPCD <---> Smart card application
 *
 * This VSmartCardWrapper runs locally, VPCD connects to the localhost.
 * As it is simple TCP communication protocol, it is safer to run locally, while REST can go over configurable TLS.
 *
 * The minimal setup without REST server would be:
 * PhysicalCard <---> VSmartCardWrapper <---> VPCD <---> Smart card application
 *
 * Here VSmartCardWrapper substitutes VICC. Or:
 * JCardSim <---> VSmartCardWrapper <---> VPCD <---> Smart card application
 */
open class VSmartCardWrapper : CliktCommand(), CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)
    override val coroutineContext: CoroutineContext by lazy { createSingleThreadDispatcher() }

    // https://ajalt.github.io/clikt
    val remoteEndpoint: String? by option("--remote-card",
        help="Remote reader address endpoint")
    val readerIdx: Int by option("--remote-reader-idx", "--reader-idx", "-r",
        help="Remote reader index")
        .int().default(0)
    val remoteType: String by option("--remote-type",
        help="Remote reader type")
        .default("card")
    val host: String by option("--host", "--hostname", "-H",
        help="VSmartCard host to connect to")
        .default("127.0.0.1")
    val port: Int by option("--port", "-P",
        help="VSmartCard port to connect to")
        .int().default(35963)
    val reversed: Boolean by option("--reversed", "-R",
        help="Use reversed connection mode. Waits for an incoming connection from vpcd.")
        .flag(default=false)
    val directPhysical: Boolean by option("--direct-phy",
        help="When using physical card, use direct connection, avoids CardManager")
        .flag(default=false)

    override fun run() {
        logger.info("Starting VSmartCard $host:$port, card: $remoteType, index: $readerIdx")
        val card = resolveCard()
        val vSmartCard = VSmartCard(card.basicChannel, if (reversed) null else host, port)

        logger.info("VSmartCard running: $vSmartCard")
        while(true){
            try {
                Thread.sleep(1)
            } catch (e: InterruptedException){
                break;
            }
        }

        logger.info("Terminating")
    }

    private fun resolveRemoteCard(): Card {
        val cfg = RunConfig.getDefaultConfig().apply {
            testCardType = CardType.REMOTE
            remoteAddress = remoteEndpoint
            targetReaderIndex = readerIdx
            remoteCardType = if ("sim" == remoteType) CardType.JCARDSIMLOCAL else CardType.PHYSICAL
        }

        logger.info("Connecting to the remote card")
        val mgr = CardManager(true, null)
        mgr.connect(cfg)
        return mgr.channel.card
    }

    private fun resolveCard(): Card {
        if (!remoteEndpoint.isNullOrBlank()) {
            return resolveRemoteCard()
        }
        if (directPhysical){
            return resolveDirectCard()
        }

        val cfg = RunConfig.getDefaultConfig().apply {
            testCardType = CardType.PHYSICAL
            targetReaderIndex = readerIdx
        }

        logger.info("Connecting to the physical card")
        val mgr = CardManager(true, null)
        mgr.connect(cfg)
        return mgr.channel.card
    }

    private fun resolveDirectCard(): Card {
        val tf = TerminalManager.getTerminalFactory()
        val reader = System.getenv("GP_READER")
        var t = TerminalManager.getInstance(tf.terminals()).dwim(reader, System.getenv("GP_READER_IGNORE"), emptyList())
        if (!t.isPresent) {
            System.err.println("Specify reader with \$GP_READER")
            throw RuntimeException("Multiple readers found, Specify reader with \$GP_READER")
        }
        t = t.map { e: CardTerminal? -> e }  // LoggingCardTerminal.getInstance(e)
        val c = t.get().connect("*")
        return c
    }
}

fun main(args: Array<String>) = VSmartCardWrapper().main(args)
