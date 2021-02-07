package cz.muni.fi.crocs.rcard.client;

import cz.muni.fi.crocs.rcard.client.protocols.VSmartCardCommProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Card channel that connects to the VICC.
 * http://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html#running-vpicc
 *
 * Endpoint behaves like VPCD
 * http://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html
 *
 * Waits for VICC connection or connects to the VICC, based on the address setting (null address: listen)
 *
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public class VSmartCardCardChannel extends CardChannel {
  private final static Logger LOG = LoggerFactory.getLogger(VSmartCardCardChannel.class);

  protected RemoteVSmartCard card;
  protected RunConfig cfg;
  protected boolean connected = false;

  protected ServerSocket listenSocket;
  protected Socket socket;
  protected VSmartCardCommProto protocol;

  public VSmartCardCardChannel(RunConfig runConfig) {
    card = new RemoteVSmartCard();
    cfg = runConfig;
  }

  public void listen(int port) throws IOException {
    listenSocket = new ServerSocket(port);
    listenSocket.setSoTimeout(0);

    LOG.info("VSmartCard VPCD emulator is listening on " + port);
    socket = listenSocket.accept();
    LOG.info("VICC connected, " + socket.getInetAddress());

    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException ignore) {}

    final InputStream dataInput = socket.getInputStream();
    final OutputStream dataOutput = socket.getOutputStream();
    protocol = new VSmartCardCommProto(dataInput, dataOutput);
  }

  public void connect(String host, int port) throws IOException {
    socket = new Socket(host, port);

    try {
      TimeUnit.SECONDS.sleep(3);
    } catch (InterruptedException ignore) {}

    final InputStream dataInput = socket.getInputStream();
    final OutputStream dataOutput = socket.getOutputStream();
    protocol = new VSmartCardCommProto(dataInput, dataOutput);
  }

  @Override
  public Card getCard() {
    return card;
  }

  @Override
  public int getChannelNumber() {
    return 0;
  }

  @Override
  public ResponseAPDU transmit(CommandAPDU apdu) throws CardException {
    try {
      connectIfNeeded();
      protocol.writeApdu(apdu.getBytes());
      final byte[] resp = protocol.readResponse();
      return new ResponseAPDU(resp);

    } catch (Exception ex) {
      LOG.warn("Transmit failed", ex);
      throw new CardException("Transmit failed - exception", ex);
    }
  }

  @Override
  public int transmit(ByteBuffer bb, ByteBuffer bb1) throws CardException {
    LOG.error("Accessing unimplemented transmit variant");
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void close() throws CardException {
    try {
      cardDisconnect(true);
    } catch (IOException e) {
      throw new CardException("Disconnect failed - exception", e);
    } finally {
      connected = false;
    }
  }

  private void cardDisconnect(boolean reset) throws IOException {
    if (socket != null){
      if (reset) {
        protocol.writeCommand(VSmartCardCommProto.RESET);
      }

      LOG.debug("Closing vicc socket");
      socket.close();
    }

    if (listenSocket != null) {
      LOG.debug("Closing server listening socket");
      listenSocket.close();
    }
  }

  protected void connectIfNeeded() throws IOException {
    if (connected){
      return;
    }

    final String host = cfg.getRemoteAddress();
    if (host == null || host.isEmpty()){
      LOG.debug("Host is empty, listening for VICC connection on port " + cfg.getRemoteViccPort());
      listen(cfg.getRemoteViccPort());
    } else {
      LOG.debug("Connecting to the VICC " + host + ":" + cfg.getRemoteViccPort());
      connect(host, cfg.getRemoteViccPort());
    }

    // if (cfg.aid != null) {
    //   cardSelect(cfg.aid);
    // }
    protocol.writeCommand(VSmartCardCommProto.POWER_ON);
    connected = true;
  }

  private String cardProtocol() throws IOException {
    return null;
  }

  private byte[] cardAtr() throws IOException {
    protocol.writeCommand(VSmartCardCommProto.GET_ATR);
    return protocol.readResponse();
  }

  private static void log(CommandAPDU cmd) {
    Util.log(LOG, cmd);
  }

  private static void log(ResponseAPDU response) {
    Util.log(LOG, response);
  }

  @Override
  public String toString() {
    return "VSmartCardCardChannel{" +
        "card=" + card +
        ", cfg=" + cfg +
        '}';
  }

  class RemoteVSmartCard extends Card {
    private final Logger LOG = LoggerFactory.getLogger(VSmartCardCardChannel.class);
    @Override
    public ATR getATR() {
      try {
        connectIfNeeded();
        return new ATR(cardAtr());
      } catch (Exception e) {
        LOG.error("ATR failed", e);
      }
      return null;
    }

    @Override
    public String getProtocol() {
      try {
        connectIfNeeded();
        return cardProtocol();
      } catch (Exception e) {
        LOG.error("ATR failed", e);
      }
      return null;
    }

    @Override
    public CardChannel getBasicChannel() {
      return VSmartCardCardChannel.this;
    }

    @Override
    public CardChannel openLogicalChannel() throws CardException {
      return VSmartCardCardChannel.this;
    }

    @Override
    public void beginExclusive() throws CardException {
      LOG.info("Asked to beginExclusive(), do nothing");
    }

    @Override
    public void endExclusive() throws CardException {
      LOG.info("Asked to endExclusive(), do nothing");
    }

    @Override
    public byte[] transmitControlCommand(int controlCode, byte[] command) throws CardException {
      LOG.error("Accessing unsupported transmitControlCommand");
      throw new CardException("Not supported");
    }

    @Override
    public void disconnect(boolean reset) throws CardException {
      close();
    }

    @Override
    public String toString() {
      return "RemoteVSmartCard{}";
    }
  }


}
