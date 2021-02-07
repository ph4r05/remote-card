package cz.muni.fi.crocs.rcard.client;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;

public class NoClosingCard extends Card {
  protected Card card;
  protected boolean shouldClose = false;

  public NoClosingCard(Card card) {
    this.card = card;
  }

  @Override
  public ATR getATR() {
    return card.getATR();
  }

  @Override
  public String getProtocol() {
    return card.getProtocol();
  }

  @Override
  public CardChannel getBasicChannel() {
    return card.getBasicChannel();
  }

  @Override
  public CardChannel openLogicalChannel() throws CardException {
    return card.openLogicalChannel();
  }

  @Override
  public void beginExclusive() throws CardException {
    card.beginExclusive();
  }

  @Override
  public void endExclusive() throws CardException {
    card.endExclusive();
  }

  @Override
  public byte[] transmitControlCommand(int controlCode, byte[] command) throws CardException {
    return card.transmitControlCommand(controlCode, command);
  }

  @Override
  public void disconnect(boolean reset) throws CardException {
    if (shouldClose) {
      card.disconnect(reset);
    }
  }

  public Card getCard() {
    return card;
  }

  public boolean isShouldClose() {
    return shouldClose;
  }

  public void setShouldClose(boolean shouldClose) {
    this.shouldClose = shouldClose;
  }
}
