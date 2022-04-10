package cz.muni.fi.crocs.rcard.client;

import apdu4j.TerminalManager;
import com.licel.jcardsim.io.CAD;
import com.licel.jcardsim.io.JavaxSmartCardInterface;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.smartcardio.CardTerminalSimulator;
import javacard.framework.AID;
import javacard.framework.Applet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main connector to various card sources.
 *
 * @author Petr Svenda
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public class CardManager {
    private final static Logger LOG = LoggerFactory.getLogger(CardManager.class);
    protected boolean bDebug = false;
    protected AtomicBoolean isConnected = new AtomicBoolean(false);
    protected byte[] appletId = null;
    protected WrappingCardChannel channel = null;
    protected ResponseAPDU selectResponse = null;
    protected CardType lastChannelType = null;

    /**
     * Add LC=0 byte to the APDU.
     */
    protected boolean fixLc = true;

    /**
     * Enables to fix NE=255, required for some JCOP3 cards.
     */
    protected Boolean fixNe = null;

    /**
     * Default NE to add to the command
     */
    protected Integer defaultNe = null;

    /**
     * Perform automated select
     */
    protected boolean doSelect = true;

    public CardManager(boolean bDebug, byte[] appletAID) {
        this.bDebug = bDebug;
        this.appletId = appletAID;
    }

    /**
     * Connect to a card source specified in the configuration.
     *
     * @param runCfg run configuration
     * @return true if connected
     * @throws CardException exceptions from underlying connects
     */
    public boolean connect(RunConfig runCfg) throws CardException {
        boolean bConnected = false;
        if (appletId == null && runCfg.aid != null){
            appletId = runCfg.aid;
        }

        switch (runCfg.testCardType) {
            case PHYSICAL: {
                connectPhysicalCard(runCfg.targetReaderIndex);
                break;
            }
            case PHYSICAL_JAVAX: {
                connectPhysicalCardJavax(runCfg.targetReaderIndex);
                break;
            }
            case JCOPSIM: {
                connectJCOPSimulator(runCfg.targetReaderIndex);
                break;
            }
            case JCARDSIMLOCAL: {
                if (runCfg.simulator != null){
                    connectJCardSimLocalSimulator(runCfg.simulator);
                } else {
                    connectJCardSimLocalSimulator(runCfg.appletToSimulate, runCfg.installData);
                }
                break;
            }
            case JCARDSIMREMOTE: {
                throw new RuntimeException("JCARDSIMREMOTE not supported yet");
            }
            case REMOTE: {
                connectRemoteChannel(runCfg);
                break;
            }
            case VSMARTCARD: {
                connectVSmartCart(runCfg);
                break;
            }
            default:
                throw new RuntimeException("Unsupported card type: " + runCfg.testCardType);
        }
        if (channel != null) {
            bConnected = true;
        }
        lastChannelType = runCfg.testCardType;
        isConnected.set(bConnected);
        return bConnected;
    }

    /**
     * Use existing card channel directly
     * @param ch CardChannel
     */
    public void connectChannel(CardChannel ch){
        if (ch == null){
            channel = null;
        } else {
            setChannel(ch);
        }
        isConnected.set(ch != null);
    }

    /**
     * Use created simulator instance
     * @param sim setup JCardSim
     * @throws CardException exception
     */
    public void connectSimulator(CardSimulator sim) throws CardException {
        setChannel(connectJCardSimLocalSimulator(sim));
        lastChannelType = CardType.JCARDSIMLOCAL;
        isConnected.set(true);
    }

    /**
     * Card disconnect
     * @param bReset reset card
     * @throws CardException exception
     */
    public void disconnect(boolean bReset) throws CardException {
        try {
            channel.getCard().disconnect(bReset); // Disconnect from the card
        } finally {
            isConnected.set(false);
        }
    }

    public CardChannel connectPhysicalCardJavax(int targetReaderIndex) throws CardException {
        LOG.debug("Looking for physical cards... ");
        return connectTerminalAndSelect(findCardTerminalSmartcardIO(targetReaderIndex));
    }

    public CardChannel connectPhysicalCard(int targetReaderIndex) throws CardException {
        LOG.debug("Looking for physical cards... ");
        return connectTerminalAndSelect(findCardTerminal(targetReaderIndex));
    }

    public CardChannel connectJCOPSimulator(int targetReaderIndex) throws CardException {
        LOG.debug("Looking for JCOP simulators...");
        int[] ports = new int[]{8050};
        try {
            return connectToCardByTerminalFactory(TerminalFactory.getInstance("JcopEmulator", ports), targetReaderIndex);
        } catch (NoSuchAlgorithmException e) {
            throw new CardException(e);
        }
    }

    public CardTerminal findCardTerminal(int targetReaderIndex) throws CardException {
        TerminalFactory tf = TerminalManager.getTerminalFactory();
        String reader = System.getenv("GP_READER");
        if (reader != null) {
            Optional<CardTerminal> t = TerminalManager.getInstance(tf.terminals()).dwim(reader, System.getenv("GP_READER_IGNORE"), Collections.emptyList());
            if (!t.isPresent()) {
                throw new RuntimeException("Reader could not be found");
            }
            return t.get();
        }

        return findTerminalIdx(tf.terminals().list(), targetReaderIndex);
    }

    public CardTerminal findCardTerminalSmartcardIO(int targetReaderIndex) throws CardException {
        TerminalFactory tf = TerminalFactory.getDefault();
        return findTerminalIdx(tf.terminals().list(), targetReaderIndex);
    }

    public CardTerminal findTerminalIdx(List<CardTerminal> terminals, int targetReaderIndex) throws CardException {
        int currIdx = -1;
        TerminalFactory tf = TerminalFactory.getDefault();
        for (CardTerminal t : terminals) {
            currIdx += 1;
            if (currIdx != targetReaderIndex){
                continue;
            }
            if (t.isCardPresent()) {
                return t;
            }
        }
        throw new RuntimeException("No card terminal found");
    }

    public CardChannel connectJCardSimLocalSimulator(CardSimulator sim) throws CardException {
        System.setProperty("com.licel.jcardsim.terminal.type", "2");
        final CardTerminal cardTerminal = CardTerminalSimulator.terminal(sim);
        return connectTerminalAndSelect(cardTerminal);
    }

    public CardChannel connectJCardSimLocalSimulator(Class<? extends Applet> appletClass, byte[] installData) {
        System.setProperty("com.licel.jcardsim.terminal.type", "2");
        CAD cad = new CAD(System.getProperties());
        JavaxSmartCardInterface simulator = (JavaxSmartCardInterface) cad.getCardInterface();
        if (installData == null) {
            installData = new byte[0];
        }
        AID appletAID = new AID(appletId, (short) 0, (byte) appletId.length);

        simulator.installApplet(appletAID, appletClass, installData, (short) 0, (byte) installData.length);
        if (doSelect) {
            selectResponse = new ResponseAPDU(simulator.selectAppletWithResult(appletAID));
            // if (selectResponse.getSW() != -28672) {
            //     throw new RuntimeException("Select error");
            // }
        }

        setChannel(new SimulatedCardChannelLocal(simulator));
        return channel;
    }

    public CardChannel connectRemoteChannel(RunConfig cfg) throws CardException {
        setChannel(new RemoteCardChannel(cfg));
        maybeSelect();
        return channel;
    }

    public CardChannel connectVSmartCart(RunConfig cfg) throws CardException {
        setChannel(new VSmartCardCardChannel(cfg));
        maybeSelect();
        return channel;
    }

    public CardChannel connectTerminalAndSelect(CardTerminal terminal) throws CardException {
        CardChannel ch = connectTerminal(terminal);

        // Select applet (mpcapplet)
        maybeSelect();
        return ch;
    }

    public void maybeSelect() throws CardException {
        if (doSelect && appletId != null) {
            LOG.debug("Smartcard: Selecting applet...");
            selectResponse = selectApplet();
        }
    }

    public CardChannel connectTerminal(CardTerminal terminal) throws CardException {
        LOG.debug("Connecting...");
        Card card = terminal.connect("*"); // Connect with the card
        if (card == null){
            return null;
        }
        LOG.debug("Terminal connected");

        LOG.debug("Establishing channel...");
        setChannel(card.getBasicChannel());
        LOG.debug("Channel established");

        return channel;
    }

    public ResponseAPDU selectApplet() throws CardException {
        CommandAPDU cmd = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, appletId);
        return transmit(cmd);
    }

    public CardChannel connectToCardByTerminalFactory(TerminalFactory factory, int targetReaderIndex) throws CardException {
        List<CardTerminal> terminals = new ArrayList<>();

        boolean card_found = false;
        CardTerminal terminal = null;
        Card card = null;
        try {
            for (CardTerminal t : factory.terminals().list()) {
                terminals.add(t);
                if (t.isCardPresent()) {
                    card_found = true;
                }
            }
        } catch (Exception e) {
            LOG.error("Terminal listing failed.", e);
        }

        if (!card_found) {
            LOG.warn("Failed to find physical card.");
            return null;
        }

        LOG.debug("Cards found: " + terminals);
        terminal = terminals.get(targetReaderIndex); // Prioritize physical card over simulations
        return connectTerminalAndSelect(terminal);
    }

    /**
     * Main communication method with the card.
     * @param cmd APDU command to send to the card
     * @return APDU card response
     * @throws CardException exception
     */
    public ResponseAPDU transmit(CommandAPDU cmd) throws CardException {
        try {
            return channel.transmit(cmd);
        } catch(Exception e) {
            isConnected.set(false);
            throw e;
        }
    }

    /**
     * Reset the card
     */
    public void reset() {
        channel.getCard().getATR();
    }

    /**
     * Read card ATR
     * @return ATR
     */
    public ATR atr() {
        return channel.getCard().getATR();
    }

    /**
     * Retrieve card protocol
     * @return protocol
     */
    public String protocol() {
        return channel.getCard().getProtocol();
    }

    public Card waitForCard(CardTerminals terminals)
        throws CardException {
        while (true) {
            for (CardTerminal ct : terminals
                .list(CardTerminals.State.CARD_INSERTION)) {

                return ct.connect("*");
            }
            terminals.waitForChange();
        }
    }

    public boolean isbDebug() {
        return bDebug;
    }

    public byte[] getAppletId() {
        return appletId;
    }

    // it's safe to return the reference directly as all Duration methods
    // are either const or return a copy
    public Duration getLastTransmitTimeDuration() {
        return channel.lastTransmitTimeDuration;
    }

    public long getLastTransmitTime() {
        return channel.lastTransmitTimeDuration.toMillis();
    }

    public long getLastTransmitTimeNano() {
        return channel.lastTransmitTimeDuration.toNanos();
    }

    public CommandAPDU getLastCommand() {
        return channel.lastCommand;
    }

    public CardChannel getChannel() {
        return channel;
    }

    public CardManager setbDebug(boolean bDebug) {
        this.bDebug = bDebug;
        if (channel != null){
            channel.bDebug = bDebug;
        }
        return this;
    }

    public CardManager setAppletId(byte[] appletId) {
        this.appletId = appletId;
        return this;
    }

    public CardManager setChannel(CardChannel channel) {
        this.channel = new WrappingCardChannel(channel);
        this.channel.bDebug = bDebug;
        this.channel.fixLc = fixLc;
        this.channel.fixNe = fixNe;
        this.channel.defaultNe = defaultNe;
        return this;
    }

    public boolean isFixLc() {
        return fixLc;
    }

    public CardManager setFixLc(boolean fixLc) {
        this.fixLc = fixLc;
        if (channel != null){
            channel.fixLc = fixLc;
        }
        return this;
    }

    public ResponseAPDU getSelectResponse() {
        return selectResponse;
    }

    public boolean isDoSelect() {
        return doSelect;
    }

    public CardManager setDoSelect(boolean doSelect) {
        this.doSelect = doSelect;
        return this;
    }

    public Boolean getFixNe() {
        return fixNe;
    }

    public CardManager setFixNe(Boolean fixNe) {
        this.fixNe = fixNe;
        if (channel != null){
            channel.fixNe = fixNe;
        }
        return this;
    }

    public Integer getDefaultNe() {
        return defaultNe;
    }

    public CardManager setDefaultNe(Integer defaultNe) {
        this.defaultNe = defaultNe;
        if (channel != null){
            channel.defaultNe = defaultNe;
        }
        return this;
    }

    public boolean getIsConnected() {
        return isConnected.get();
    }

    public CardType getLastChannelType() {
        return lastChannelType;
    }
}
