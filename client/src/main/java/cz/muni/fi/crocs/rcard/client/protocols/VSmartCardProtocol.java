package cz.muni.fi.crocs.rcard.client.protocols;

import java.io.IOException;

/**
 * Communication protocol for VSmartCard - card side.
 *
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public interface VSmartCardProtocol {
    void disconnect();
    int readCommand() throws IOException;
    byte[] readData() throws IOException;
    void writeData(byte[] data) throws IOException;
}
