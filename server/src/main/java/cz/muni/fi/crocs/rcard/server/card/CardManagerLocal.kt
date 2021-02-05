package cz.muni.fi.crocs.rcard.server.card

import apdu4j.TerminalManager
import com.licel.jcardsim.io.CAD
import com.licel.jcardsim.io.JavaxSmartCardInterface
import com.licel.jcardsim.smartcardio.CardSimulator
import com.licel.jcardsim.smartcardio.CardTerminalSimulator
import cz.muni.fi.crocs.rcard.client.*
import javacard.framework.AID
import javacard.framework.Applet
import org.bouncycastle.util.encoders.Hex
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.smartcardio.*

class CardManagerLocal(bDebug: Boolean, appletAID: ByteArray?) {
    var bDebug = false

    val isConnected = AtomicBoolean(false)
    var appletId: ByteArray? = null
        private set
    var lastTransmitTime = 0L
        private set
    var lastCommand: CommandAPDU? = null
        private set
    var channel: CardChannel? = null
        private set
    var selectResponse: ResponseAPDU? = null
        private set
    var lastChannelType: CardType? = null
        private set

    /**
     * Add LC=0 byte to the APDU.
     */
    var isFixLc = true

    /**
     * Enables to fix NE=255, required for some JCOP3 cards.
     */
    var fixNe: Boolean? = null

    /**
     * Default NE to add to the command
     */
    var defaultNe: Int? = null

    /**
     * Perform automated select
     */
    var doSelect = true

    companion object {
        private val logger = LoggerFactory.getLogger(CardManagerLocal::class.java)
    }

    init {
        this.bDebug = bDebug
        appletId = appletAID
    }

    /**
     * Card connect
     * @param runCfg run configuration
     * @return true if connected
     * @throws Exception exceptions from underlying connects
     */
    fun connect(runCfg: RunConfig): Boolean {
        var bConnected = false
        if (appletId == null && runCfg.aid != null){
            appletId = runCfg.aid
        }

        when (runCfg.testCardType) {
            CardType.PHYSICAL -> {
                channel = connectPhysicalCard(runCfg.targetReaderIndex)
            }
            CardType.PHYSICAL_JAVAX -> {
                channel = connectPhysicalCardJavax(runCfg.targetReaderIndex)
            }
            CardType.JCOPSIM -> {
                channel = connectJCOPSimulator(runCfg.targetReaderIndex)
            }
            CardType.JCARDSIMLOCAL -> {
                channel = runCfg.simulator?.let {
                    connectJCardSimLocalSimulator(it)
                } ?: connectJCardSimLocalSimulator(runCfg.appletToSimulate, runCfg.installData)
            }
            CardType.JCARDSIMREMOTE -> {
                channel = null // Not implemented yet
            }
            CardType.REMOTE -> {
                channel = RemoteCardChannel(runCfg)
                maybeSelect()
            }
            else -> {
                throw RuntimeException("Null Card type")
            }
        }
        if (channel != null) {
            bConnected = true
        }
        lastChannelType = runCfg.testCardType
        isConnected.set(bConnected)
        return bConnected
    }

    fun connectChannel(ch: CardChannel?){
        channel = ch
        isConnected.set(ch != null)
    }

    fun connectSimulator(sim: CardSimulator){
        channel = connectJCardSimLocalSimulator(sim)
        lastChannelType = CardType.JCARDSIMLOCAL
        isConnected.set(true)
    }

    @Throws(CardException::class)
    fun disconnect(bReset: Boolean) {
        try {
            channel?.card?.disconnect(bReset) // Disconnect from the card
            channel?.card?.atr
        } finally {
            isConnected.set(false)
        }
    }

    fun atr(): ATR? {
        return channel?.card?.atr
    }

    fun protocol(): String? {
        return channel?.card?.protocol
    }

    @Throws(Exception::class)
    fun connectPhysicalCardJavax(targetReaderIndex: Int): CardChannel? {
        logger.debug("Looking for physical cards (javax)... ")
        return connectTerminalAndSelect(findCardTerminalSmartcardIO(targetReaderIndex))
    }

    @Throws(Exception::class)
    fun connectPhysicalCard(targetReaderIndex: Int): CardChannel? {
        logger.debug("Looking for physical cards... ")
        return connectTerminalAndSelect(findCardTerminal(targetReaderIndex))
    }

    @Throws(Exception::class)
    fun connectJCOPSimulator(targetReaderIndex: Int): CardChannel? {
        logger.debug("Looking for JCOP simulators...")
        val ports = intArrayOf(8050)
        return connectToCardByTerminalFactory(TerminalFactory.getInstance("JcopEmulator", ports), targetReaderIndex)
    }

    @Throws(CardException::class)
    fun findCardTerminal(targetReaderIndex: Int): CardTerminal {
        val tf = TerminalManager.getTerminalFactory()
        val reader = System.getenv("GP_READER")
        if (reader != null) {
            val t =
                TerminalManager.getInstance(tf.terminals()).dwim(reader, System.getenv("GP_READER_IGNORE"), emptyList())
            if (!t.isPresent) {
                throw RuntimeException("Reader could not be found")
            }
            return t.get()
        }
        return findTerminalIdx(tf.terminals().list(), targetReaderIndex)
    }

    @Throws(CardException::class)
    fun findCardTerminalSmartcardIO(targetReaderIndex: Int): CardTerminal {
        val tf = TerminalFactory.getDefault()
        return findTerminalIdx(tf.terminals().list(), targetReaderIndex)
    }

    @Throws(CardException::class)
    fun findTerminalIdx(terminals: List<CardTerminal>, targetReaderIndex: Int): CardTerminal {
        var currIdx = -1
        for (t in terminals) {
            currIdx += 1
            if (currIdx != targetReaderIndex) {
                continue
            }
            if (t.isCardPresent) {
                return t
            }
        }
        throw RuntimeException("No card terminal found")
    }

    fun connectJCardSimLocalSimulator(sim: CardSimulator): CardChannel? {
        val cardTerminal = CardTerminalSimulator.terminal(sim)
        logger.info("JcardSim terminal: $cardTerminal")
        return connectTerminalAndSelect(cardTerminal)
        //return SimulatedCardChannelLocal(sim)
    }

    fun connectJCardSimLocalSimulator(appletClass: Class<out Applet>?, installData: ByteArray?): CardChannel {
        var installData = installData
        System.setProperty("com.licel.jcardsim.terminal.type", "2")
        val cad = CAD(System.getProperties())
        val simulator = cad.cardInterface as JavaxSmartCardInterface
        if (installData == null) {
            installData = ByteArray(0)
        }

        appletId ?: throw RuntimeException("Applet ID cannot be null at this point")
        val appletAID = AID(appletId, 0.toShort(), appletId!!.size.toByte())
        simulator.installApplet(appletAID, appletClass, installData, 0.toShort(), installData.size.toByte())

        selectSimApplet(simulator, appletAID)
        return SimulatedCardChannelLocal(simulator)
    }

    private fun selectSimApplet(sim: JavaxSmartCardInterface, aid: AID? = null){
        val appletId = appletId
        if (!doSelect || (appletId == null && aid == null)){
            return
        }

        val appletAID = aid ?: appletId?.let { AID(it, 0.toShort(), it.size.toByte()) } ?: throw RuntimeException("Null applet ID")
        val selResp = sim.selectAppletWithResult(appletAID)
        selectResponse = ResponseAPDU(selResp)
        if (selectResponse!!.sw != -28672) {
            throw RuntimeException("Select error")
        }
    }

    @Throws(CardException::class)
    fun connectTerminalAndSelect(terminal: CardTerminal?): CardChannel? {
        val ch = connectTerminal(terminal)

        maybeSelect()
        return ch
    }

    fun maybeSelect() {
        if (doSelect && appletId != null) {
            logger.debug("Smartcard: Selecting applet...")
            selectResponse = selectApplet()
        }
    }

    @Throws(CardException::class)
    fun connectTerminal(terminal: CardTerminal?): CardChannel? {
        logger.debug("Connecting...")
        val card = terminal!!.connect("*") ?: return null // Connect with the card
        logger.debug("Terminal connected")
        logger.debug("Establishing channel...")
        channel = card.basicChannel
        logger.debug("Channel established")
        return card.basicChannel
    }

    @Throws(CardException::class)
    fun selectApplet(): ResponseAPDU {
        val cmd = CommandAPDU(0x00, 0xa4, 0x04, 0x00, appletId)
        return transmit(cmd)
    }

    @Throws(CardException::class)
    fun connectToCardByTerminalFactory(factory: TerminalFactory, targetReaderIndex: Int): CardChannel? {
        val terminals: MutableList<CardTerminal> = ArrayList()
        var cardFound = false
        var terminal: CardTerminal?
        try {
            for (t in factory.terminals().list()) {
                terminals.add(t)
                if (t.isCardPresent) {
                    cardFound = true
                }
            }
        } catch (e: Exception) {
            logger.error("Terminal listing failed.", e)
        }
        if (!cardFound) {
            logger.warn("Failed to find physical card.")
            return null
        }
        logger.debug("Cards found: $terminals")
        terminal = terminals[targetReaderIndex] // Prioritize physical card over simulations
        return connectTerminalAndSelect(terminal)
    }

    @Throws(CardException::class)
    fun transmit(cmd: CommandAPDU): ResponseAPDU {
        val ch = channel ?: throw RuntimeException("Channel is not opened")
        var cmd = cmd
        if (isFixLc) {
            cmd = fixApduLc(cmd)
        }
        lastCommand = cmd
        if (bDebug) {
            log(cmd)
        }
        var elapsed = -System.currentTimeMillis()
        val response = ch.transmit(cmd)
        elapsed += System.currentTimeMillis()
        lastTransmitTime = elapsed
        if (bDebug) {
            log(response, lastTransmitTime)
        }
        return response
    }

    private fun log(cmd: CommandAPDU) {
        logger.debug(
            String.format(
                "--> %s (%d B)", Hex.toHexString(cmd.bytes),
                cmd.bytes.size
            )
        )
    }

    private fun log(response: ResponseAPDU, time: Long = 0) {
        val swStr = String.format("%02X", response.sw)
        val data = response.data
        if (data.size > 0) {
            logger.debug(
                String.format(
                    "<-- %s %s (%d) [%d ms]", Hex.toHexString(data), swStr,
                    data.size, time
                )
            )
        } else {
            logger.debug(String.format("<-- %s [%d ms]", swStr, time))
        }
    }

    private fun fixApduLc(cmd: CommandAPDU): CommandAPDU {
        if (cmd.nc != 0) {
            return fixApduNe(cmd)
        }
        val apdu = byteArrayOf(
            cmd.cla.toByte(),
            cmd.ins.toByte(),
            cmd.p1.toByte(),
            cmd.p2.toByte(),
            0.toByte()
        )
        return CommandAPDU(apdu)
    }

    private fun fixApduNe(cmd: CommandAPDU): CommandAPDU {
        var doFix = fixNe
        if (doFix == null) {
            doFix = System.getProperty("cz.muni.fi.crocs.rcard.fixNe", "false") == "true"
        }
        if (!doFix) {
            return cmd
        }
        var ne = defaultNe
        if (ne == null) {
            ne = Integer.valueOf(System.getProperty("cz.muni.fi.crocs.rcard.defaultNe", "255"))
        }
        logger.debug("Fixed NE for the APDU to: $ne")
        return CommandAPDU(cmd.cla, cmd.ins, cmd.p1, cmd.p2, cmd.data, ne!!)
    }

    @Throws(CardException::class)
    fun waitForCard(terminals: CardTerminals): Card {
        while (true) {
            for (ct in terminals
                .list(CardTerminals.State.CARD_INSERTION)) {
                return ct.connect("*")
            }
            terminals.waitForChange()
        }
    }

}