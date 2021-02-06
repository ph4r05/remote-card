/*
 * Copyright 2018 Joyent, Inc
 * Copyright 2020 The University of Queensland
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

import com.licel.jcardsim.remote.VSmartCardTCPProtocol;
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
 * VSmartCard Card Implementation.
 *
 * @author alex@cooperi.net
 * @author ph4r05@gmail.com
 */
public class VSmartCard {
    private final static Logger LOG = LoggerFactory.getLogger(VSmartCard.class);

    public VSmartCard(CardChannel channel, String host, int port) throws IOException {
        VSmartCardTCPProtocol driverProtocol = new VSmartCardTCPProtocol();
        driverProtocol.connect(host, port);
        startThread(channel, driverProtocol);
    }

    static public void main(String args[]) throws Exception {
        if (args.length !=1) {
            System.out.println("Usage: java com.licel.jcardsim.remote.VSmartCard <jcardsim.cfg>");
            System.exit(-1);
        }

        Properties cfg = Util.loadProperties(args[0]);

        final Enumeration<?> keys = cfg.propertyNames();
        while (keys.hasMoreElements()) {
            String propertyName = (String) keys.nextElement();
            System.setProperty(propertyName, cfg.getProperty(propertyName));
        }

        String propKey = "com.licel.jcardsim.vsmartcard.host";
        String host = System.getProperty(propKey);
        if (host == null) {
            throw new InvalidParameterException("Missing value for property: " + propKey);
        }

        propKey = "com.licel.jcardsim.vsmartcard.port";
        String port = System.getProperty(propKey);
        if (port == null) {
            throw new InvalidParameterException("Missing value for property: " + propKey);
        }

        new VSmartCard(null, host, Integer.parseInt(port));
    }

    private void startThread(CardChannel channel, VSmartCardTCPProtocol driverProtocol) throws IOException {
        final IOThread ioThread = new IOThread(channel, driverProtocol);
        final ShutDownHook hook = new ShutDownHook(ioThread);
        Runtime.getRuntime().addShutdownHook(hook);
        ioThread.start();
    }

    static class ShutDownHook extends Thread {
        IOThread ioThread;

        public ShutDownHook(IOThread ioThread) {
            this.ioThread = ioThread;
        }

        public void run() {
            ioThread.isRunning = false;
            System.out.println("Shutdown connections");
            ioThread.driverProtocol.disconnect();
        }
    }

    static class IOThread extends Thread {
        VSmartCardTCPProtocol driverProtocol;
        CardChannel channel;
        boolean isRunning;

        public IOThread(CardChannel channel, VSmartCardTCPProtocol driverProtocol) {
            this.channel = channel;
            this.driverProtocol = driverProtocol;
            isRunning = true;
        }

        @Override
        public void run() {
            while (isRunning) {
                try {
                    int cmd = driverProtocol.readCommand();
                    switch (cmd) {
                        case VSmartCardTCPProtocol.POWER_ON:
                        case VSmartCardTCPProtocol.RESET:
                            // TODO: how to reset properly
                            channel.getCard().getATR();
                            break;
                        case VSmartCardTCPProtocol.GET_ATR:
                            driverProtocol.writeData(channel.getCard().getATR().getBytes());
                            break;
                        case VSmartCardTCPProtocol.APDU:
                            final byte[] apdu = driverProtocol.readData();
                            final byte[] reply = channel.transmit(new CommandAPDU(apdu)).getBytes();
                            driverProtocol.writeData(reply);
                            break;
                    }
                } catch (Exception e) {
                    LOG.error("Exception in VSmartCardIO", e);
                }
            }
        }
    }

}
