package cz.muni.fi.crocs.rcard.client;

import com.licel.jcardsim.io.JavaxSmartCardInterface;

import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;

/**
 * @author Petr Svenda
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public class SimulatedCard extends Card {

    JavaxSmartCardInterface sim;
    SimulatedCardChannelLocal channel;

    public SimulatedCard(SimulatedCardChannelLocal channel, JavaxSmartCardInterface sim) {
        this.sim = sim;
        this.channel = channel;
    }

    @Override
    public ATR getATR() {
        return new ATR(sim.getATR());
    }

    @Override
    public String getProtocol() {
        return sim.getProtocol();
    }

    @Override
    public CardChannel getBasicChannel() {
        return channel;
    }

    @Override
    public CardChannel openLogicalChannel() throws CardException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void beginExclusive() throws CardException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void endExclusive() throws CardException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte[] transmitControlCommand(int i, byte[] bytes) throws CardException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void disconnect(boolean bln) throws CardException {
        channel.reset();
    }
}
