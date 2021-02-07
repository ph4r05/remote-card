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

import com.licel.jcardsim.remote.VSmartCardTCPProtocol;
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
 * Waiting for VPCD connection.
 *
 * @author alex@cooperi.net
 * @author ph4r05@gmail.com
 * Source: CRoCS Card project
 */
public class VSmartCardTCPProtocolClient implements VSmartCardProtocol {
    VSmartCardTCPProtocol client;

    public VSmartCardTCPProtocolClient() {
        this.client = new VSmartCardTCPProtocol();
    }

    public VSmartCardTCPProtocolClient(VSmartCardTCPProtocol client) {
        this.client = client;
    }

    public void connect(String host, int port) throws IOException {
        client.connect(host, port);
    }

    @Override
    public void disconnect() {
        client.disconnect();
    }

    @Override
    public int readCommand() throws IOException {
        return client.readCommand();
    }

    @Override
    public byte[] readData() throws IOException {
        return client.readData();
    }

    @Override
    public void writeData(byte[] data) throws IOException {
        client.writeData(data);
    }
}
