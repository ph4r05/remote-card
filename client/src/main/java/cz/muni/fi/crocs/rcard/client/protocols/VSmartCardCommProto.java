package cz.muni.fi.crocs.rcard.client.protocols;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * VSmartCard communication protocol helper.
 * Implements both vpcd and card side.
 *
 * @author Dusan Klinec ph4r05@gmail.com
 * Source: CRoCS Card project, https://github.com/ph4r05/remote-card
 */
public class VSmartCardCommProto {
  private final InputStream dataInput;
  private final OutputStream dataOutput;

  private int frameLen = -1;

  public static final int POWER_OFF = 0;
  public static final int POWER_ON = 1;
  public static final int RESET = 2;
  public static final int GET_ATR = 4;
  public static final int APDU = -1;

  public VSmartCardCommProto(InputStream dataInput, OutputStream dataOutput) {
    this.dataInput = dataInput;
    this.dataOutput = dataOutput;
  }

  public int readCommand() throws IOException {
    final byte[] cmdBuf = new byte[3];
    read(cmdBuf, 0, 2, dataInput);
    final int len = ((cmdBuf[0] << 8) & 0xFF00) | (cmdBuf[1] & 0xFF);
    if (len == 1) {
      read(cmdBuf, 2, 1, dataInput);
      final int cmd = cmdBuf[2];
      return (cmd);
    }
    frameLen = len;
    return (APDU);
  }

  public byte[] readResponse() throws IOException {
    final byte[] lenBuf = new byte[3];
    read(lenBuf, 0, 2, dataInput);
    final int len = ((lenBuf[0] << 8) & 0xFF00) | (lenBuf[1] & 0xFF);
    if (len > 1024*1024){
      throw new RuntimeException("Indicated length is too big");
    }

    final byte[] dataBuf = new byte[len];
    read(dataBuf, 0, len, dataInput);
    return dataBuf;
  }

  protected void writeCommand(int cmd, byte[] buf){
    writeCommand(cmd, buf, 1);
  }

  protected void writeCommand(int cmd, byte[] buf, int length){
    writeLength(buf, length);
    buf[2] = (byte) cmd;
  }

  protected void writeLength(byte[] buf, int length){
    buf[0] = (byte)(((length & 0xFF00) >> 8) & 0xFF);
    buf[1] = (byte)(length & 0xFF);
  }

  public int writeCommand(int cmd) throws IOException {
    final byte[] buf = new byte[3];
    writeCommand(cmd, buf);
    dataOutput.write(buf);
    return 3;
  }

  public int writeApdu(byte[] data) throws IOException {
    final byte[] buf = new byte[2+data.length];
    writeLength(buf, data.length);
    System.arraycopy(data, 0, buf, 2, data.length);
    dataOutput.write(buf);
    return buf.length;
  }

  public byte[] readData() throws IOException {
    if (frameLen == -1) {
      throw new IOException("No APDU command waiting");
    }
    final byte[] buf = new byte[frameLen];
    read(buf, dataInput);
    frameLen = -1;
    return buf;
  }

  public void writeData(byte[] data) throws IOException {
    final byte[] buf = new byte[2 + data.length];
    buf[0] = (byte)(((data.length & 0xFF00) >> 8) & 0xFF);
    buf[1] = (byte)(data.length & 0xFF);
    System.arraycopy(data, 0, buf, 2, data.length);
    dataOutput.write(buf);
  }

  private void read(byte[] buf, InputStream stream) throws IOException {
    read(buf, 0, buf.length, stream);
  }

  private void read(byte[] buf, int offset, int len, InputStream stream) throws IOException {
    while (len > 0) {
      final int retval = stream.read(buf, offset, len);

      if (retval < 0) {
        throw new IOException("Got negative number from socket");
      }

      len    -= retval;
      offset += retval;
    }
  }
}
