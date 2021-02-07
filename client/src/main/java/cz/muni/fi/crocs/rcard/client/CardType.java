package cz.muni.fi.crocs.rcard.client;

/**
 * @author Petr Svenda
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public enum CardType {
  /**
   * Physically connected card, APDU4J reader
   */
  PHYSICAL,

  /**
   * JCOP card simulator, deprecated
   */
  JCOPSIM,

  /**
   * Locla JCardSim, configured in the RunConfig
   */
  JCARDSIMLOCAL,

  /**
   * Remote JCardSim, deprecated
   */
  JCARDSIMREMOTE,

  /**
   * Physically connected card, uses javax.smartcardio for connection
   */
  PHYSICAL_JAVAX,

  /**
   * Remote card over REST interface
   */
  REMOTE,

  /**
   * VSmartCard card connection protocol (connects to vicc)
   */
  VSMARTCARD
}