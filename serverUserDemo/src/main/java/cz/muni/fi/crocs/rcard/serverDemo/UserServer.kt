package cz.muni.fi.crocs.rcard.serverDemo

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.licel.jcardsim.smartcardio.CardSimulator
import com.licel.jcardsim.utils.AIDUtil
import cz.muni.fi.crocs.rcard.client.CardManager
import cz.muni.fi.crocs.rcard.client.CardType
import cz.muni.fi.crocs.rcard.client.RunConfig
import cz.muni.fi.crocs.rcard.server.CardConnectorIdx
import cz.muni.fi.crocs.rcard.serverDemo.demo.DemoApplet3
import cz.muni.fi.crocs.rcard.serverDemo.demo.DemoApplet4
import io.vertx.core.logging.LoggerFactory
import org.apache.commons.codec.binary.Hex

open class UserServer : cz.muni.fi.crocs.rcard.server.Server() {
    private val logger = LoggerFactory.getLogger(javaClass)

    // https://ajalt.github.io/clikt
    val anotherOption: Boolean by option("--another",
        help="Another testing option")
        .flag(default=false)

    /**
     * Change configuration of the card before connecting.
     * Can initialize simulator here
     */
    override fun configureCard(key: CardConnectorIdx, mrg: CardManager, cfg: RunConfig){
        if (key.ctype != CardType.JCARDSIMLOCAL){
            return
        }

        val simulator = CardSimulator()

        // Install first demo applet
        val appletAID = AIDUtil.create(DemoApplet3.APPLET_AID_BYTE)
        simulator.installApplet(appletAID, DemoApplet3::class.java)

        // Installing applet 2 - demo that we can have more, select between those
        val appletAID2 = AIDUtil.create(DemoApplet4.APPLET_AID_BYTE)
        simulator.installApplet(appletAID2, DemoApplet4::class.java)

        cfg.simulator = simulator

        // If AID is provided to the config, we can have applet selected on connection
        //cfg.aid = DemoApplet.APPLET_AID_BYTE

        logger.info("Created sim for AID ${Hex.encodeHexString(DemoApplet3.APPLET_AID_BYTE)} for DemoApplet3")
    }

    companion object {
        init {
            System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory") //::javaClass.name)
        }
    }
}

fun main(args: Array<String>) = UserServer().main(args)
