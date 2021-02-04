package cz.muni.fi.crocs.rcard.server.card

import com.licel.jcardsim.smartcardio.CardSimulator
import javacard.framework.Applet

enum class CardType {
    PHYSICAL, JCOPSIM, JCARDSIMLOCAL, JCARDSIMREMOTE, PHYSICAL_JAVAX
}

class RunConfig {
    var targetReaderIndex = 0
    var numRepeats = 1
    var appletToSimulate: Class<out Applet>? = null
    var bReuploadApplet = false
    var installData: ByteArray? = null
    var aid: ByteArray? = null
    var simulator: CardSimulator? = null
    var testCardType = CardType.PHYSICAL

    companion object {
        val defaultConfig: RunConfig
            get() {
                val runCfg = RunConfig()
                runCfg.targetReaderIndex = 0
                runCfg.testCardType = CardType.PHYSICAL
                runCfg.appletToSimulate = null
                return runCfg
            }
    }
}
