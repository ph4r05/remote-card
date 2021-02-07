package cz.muni.fi.crocs.rcard.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.nio.ByteBuffer;

/**
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public class WrappingCardChannel extends CardChannel {
  private final static Logger LOG = LoggerFactory.getLogger(WrappingCardChannel.class);

  protected CardChannel wrapped;
  private WrappingCard wrappedCard;

  protected boolean bDebug = false;

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

  protected Long lastTransmitTime = (long) 0;
  protected CommandAPDU lastCommand = null;

  public WrappingCardChannel(CardChannel wrapped) {
    if (wrapped instanceof WrappingCardChannel){
      throw new RuntimeException("Double wrapping channel");
    }
    this.wrapped = wrapped;
  }

  @Override
  public Card getCard() {
    if (wrappedCard == null){
      wrappedCard = new WrappingCard(wrapped.getCard());
    }
    return wrappedCard;
  }

  @Override
  public int getChannelNumber() {
    return wrapped.getChannelNumber();
  }

  @Override
  public ResponseAPDU transmit(CommandAPDU cmd)
      throws CardException {

    if (fixLc){
      cmd = fixApduLc(cmd);
    }

    lastCommand = cmd;
    if (bDebug) {
      log(cmd);
    }

    ResponseAPDU response = null;
    long elapsed = -System.currentTimeMillis();
    try {
      response = wrapped.transmit(cmd);
    } finally {
      elapsed += System.currentTimeMillis();
      lastTransmitTime = elapsed;
    }

    if (bDebug) {
      log(response, lastTransmitTime);
    }

    return response;
  }

  @Override
  public int transmit(ByteBuffer command, ByteBuffer response) throws CardException {
    // Suboptimal wrapping, can be optimized to be in-memory in the buffers
    final byte[] req = new byte[command.position()];
    command.get(req);
    final ResponseAPDU resp = transmit(new CommandAPDU(req));
    final byte[] respBytes = resp.getBytes();
    response.put(respBytes);
    return respBytes.length;
    //wrapped.transmit(command, response);
  }

  @Override
  public void close() throws CardException {
    wrapped.close();
  }

  public void log(CommandAPDU cmd) {
    Util.log(LOG, cmd);
  }

  public void log(ResponseAPDU response, long time) {
    Util.log(LOG, response, time);
  }

  public CommandAPDU fixApduLc(CommandAPDU cmd){
    if (cmd.getNc() != 0){
      return fixApduNe(cmd);
    }

    byte[] apdu = new byte[] {
        (byte)cmd.getCLA(),
        (byte)cmd.getINS(),
        (byte)cmd.getP1(),
        (byte)cmd.getP2(),
        (byte)0
    };
    return new CommandAPDU(apdu);
  }

  private CommandAPDU fixApduNe(CommandAPDU cmd) {
    Boolean doFix = fixNe;
    if (doFix == null) {
      doFix = System.getProperty("cz.muni.fi.crocs.rcard.fixNe", "false").equalsIgnoreCase("true");
    }
    if (!doFix) {
      return cmd;
    }

    Integer ne = defaultNe;
    if (ne == null) {
      ne = Integer.valueOf(System.getProperty("cz.muni.fi.crocs.rcard.defaultNe", "255"));
    }

    LOG.debug("Fixed NE for the APDU to: " + ne);
    return new CommandAPDU(cmd.getCLA(), cmd.getINS(), cmd.getP1(), cmd.getP2(), cmd.getData(), ne);
  }

  @Override
  public String toString() {
    return "WrappingCardChannel{" +
        "wrapped=" + wrapped +
        ", wrappedCard=" + wrappedCard +
        ", bDebug=" + bDebug +
        ", fixLc=" + fixLc +
        ", fixNe=" + fixNe +
        ", defaultNe=" + defaultNe +
        ", lastTransmitTime=" + lastTransmitTime +
        '}';
  }

  class WrappingCard extends Card {
    private final Logger LOG = LoggerFactory.getLogger(WrappingCard.class);
    private final Card wrappedCard;

    public WrappingCard(Card wrappedCard) {
      this.wrappedCard = wrappedCard;
    }

    @Override
    public ATR getATR() {
      return wrappedCard.getATR();
    }

    @Override
    public String getProtocol() {
      return wrappedCard.getProtocol();
    }

    @Override
    public CardChannel getBasicChannel() {
      return WrappingCardChannel.this;
    }

    @Override
    public CardChannel openLogicalChannel() throws CardException {
      return WrappingCardChannel.this;
    }

    @Override
    public void beginExclusive() throws CardException {
      wrappedCard.beginExclusive();
    }

    @Override
    public void endExclusive() throws CardException {
      wrappedCard.endExclusive();
    }

    @Override
    public byte[] transmitControlCommand(int controlCode, byte[] command) throws CardException {
      return wrappedCard.transmitControlCommand(controlCode, command);
    }

    @Override
    public void disconnect(boolean reset) throws CardException {
      wrappedCard.disconnect(reset);
    }

    @Override
    public String toString() {
      return "WrappingCard{" +
          "wrappedCard=" + wrappedCard +
          '}';
    }
  }
}
