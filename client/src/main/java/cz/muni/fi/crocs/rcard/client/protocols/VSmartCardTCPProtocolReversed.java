/*
 * Copyright 2018 Joyent, Inc
 * Copyright 2020 The University of Queensland
 * Copyright 2017 Licel Corporation.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * VSmartCard TCP protocol implementation. Used by VSmartCard.
 * VICC client side, waiting for VPCD connection.
 *
 * @author alex@cooperi.net
 * @author ph4r05@gmail.com
 * Source: CRoCS Card project
 */
public class VSmartCardTCPProtocolReversed implements VSmartCardProtocol {
    private final static Logger LOG = LoggerFactory.getLogger(VSmartCardTCPProtocolReversed.class);
    private ServerSocket listenSocket;
    private Socket socket;
    private VSmartCardCommProto protocol;

    public void listen(int port) throws IOException {
        listenSocket = new ServerSocket(port);
        listenSocket.setSoTimeout(0);

        LOG.info("Server is listening on " + port);
        socket = listenSocket.accept();
        LOG.info("Client connected, " + socket.getInetAddress());

        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException ignore) {}

        final InputStream dataInput = socket.getInputStream();
        final OutputStream dataOutput = socket.getOutputStream();
        protocol = new VSmartCardCommProto(dataInput, dataOutput);
    }

    public void disconnect() {
        closeSocket(socket);
        try {
            listenSocket.close();
        } catch (IOException e) {
            LOG.warn("Exception closing listening socket", e);
        }
    }

    public int readCommand() throws IOException {
        return protocol.readCommand();
    }

    public byte[] readData() throws IOException {
        return protocol.readData();
    }

    public void writeData(byte[] data) throws IOException {
        protocol.writeData(data);
    }

    private void closeSocket(Socket sock) {
        try {
            sock.close();
        } catch (IOException ignored) {}
    }
}
