/*
 * Copyright 2013 Licel LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.muni.fi.crocs.rcard.client.protocols;

import com.licel.jcardsim.remote.BixVReaderIPCProtocol;
import com.licel.jcardsim.remote.BixVReaderProtocol;
import com.licel.jcardsim.remote.BixVReaderTCPProtocol;
import cz.muni.fi.crocs.rcard.client.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CommandAPDU;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * BixVReader Card Implementation.
 * 
 * @author LICEL LLC
 * @author ph4r05@gmail.com
 * Source: CRoCS Card project
 */
public class BixVReaderCard {
    private final static Logger LOG = LoggerFactory.getLogger(BixVReaderCard.class);

    public BixVReaderCard(CardChannel ch, int idx) throws IOException {
        BixVReaderIPCProtocol driverProtocol = new BixVReaderIPCProtocol();
        driverProtocol.connect(idx);
        startThread(ch, driverProtocol);
    }
    
    public BixVReaderCard(CardChannel ch, String host, int port, int event_port) throws IOException {
        BixVReaderTCPProtocol driverProtocol = new BixVReaderTCPProtocol();
        driverProtocol.connect(host, port, event_port);
        startThread(ch, driverProtocol);
    }
    
    static public void main(String args[]) throws Exception {
        if (args.length !=1) {
            System.out.println("Usage: java com.licel.jcardsim.remote.BixVReaderCard <jcardsim.cfg>");
            System.exit(-1);
        }

        Properties cfg = Util.loadProperties(args[0]);
        Enumeration keys = cfg.propertyNames();
        while(keys.hasMoreElements()) {
            String propertyName = (String) keys.nextElement();
            System.setProperty(propertyName, cfg.getProperty(propertyName));
        }
        
        String host = System.getProperty("com.licel.jcardsim.bixvreader.host");
        
        if (host != null) {
            String propKey = "com.licel.jcardsim.bixvreader.port";
            String port = System.getProperty(propKey);
            
            if(port == null) {
                throw new InvalidParameterException("Missing value for property: " + propKey);
            }
            
            propKey = "com.licel.jcardsim.bixvreader.eport";
            String eventPort = System.getProperty(propKey);
            
            if(eventPort == null) {
                throw new InvalidParameterException("Missing value for property: " + propKey);
            }

            BixVReaderCard server = new BixVReaderCard(null, host, Integer.parseInt(port), Integer.parseInt(eventPort));
        } else {
            int readerIdx = Integer.parseInt(System.getProperty("com.licel.jcardsim.bixvreader.idx", "0"));
            BixVReaderCard server = new BixVReaderCard(null, readerIdx);
        }
    }

    private void startThread(CardChannel ch, BixVReaderProtocol driverProtocol) throws IOException {
        final IOThread ioThread = new IOThread(ch, driverProtocol);
        ShutDownHook hook = new ShutDownHook(ioThread);
        Runtime.getRuntime().addShutdownHook(hook);
        ioThread.start();
        driverProtocol.writeEventCommand(BixVReaderProtocol.CARD_INSERTED);
    }
    
     static class ShutDownHook extends Thread {

        IOThread ioThread;

        public ShutDownHook(IOThread ioThread) {
            this.ioThread = ioThread;
        }

         public void run() {
             ioThread.isRunning = false;
             System.out.println("Shutdown connections");
             try {
                 ioThread.driverProtocol.writeEventCommand(BixVReaderIPCProtocol.CARD_REMOVED);
             } catch (Exception ignored) {
             }
             ioThread.driverProtocol.disconnect();
         }
    }
    
    static class IOThread extends Thread {

        BixVReaderProtocol driverProtocol;
        CardChannel ch;
        boolean isRunning;

        public IOThread(CardChannel ch, BixVReaderProtocol driverProtocol) {
            this.ch = ch;
            this.driverProtocol = driverProtocol;
            isRunning = true;
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    int cmd = driverProtocol.readCommand();
                    switch (cmd) {
                        case 0:
                        case 1:
                            // TODO: mgr.reset();
                            driverProtocol.writeData(ch.getCard().getATR().getBytes());
                            break;
                        case 2:
                            byte[] apdu = driverProtocol.readData();
                            driverProtocol.writeData(ch.transmit(new CommandAPDU(apdu)).getBytes());
                            break;
                    }
                } catch (Exception e) {
                    LOG.error("Exception in BixVReaderCardIO", e);
                }
            }
        }
    }

}
