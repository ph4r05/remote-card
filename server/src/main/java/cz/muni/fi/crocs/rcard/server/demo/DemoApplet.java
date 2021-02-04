package cz.muni.fi.crocs.rcard.server.demo;

import javacard.framework.*;
import javacard.security.RandomData;

public class DemoApplet extends Applet implements MultiSelectable
{
	public static final byte[] APPLET_AID_BYTE = {
		(byte)0x01, (byte)0xff, (byte)0xff, (byte)0x04,
		(byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08,
		(byte)0x09, (byte)0x01, (byte)0x02};

	private static final short BUFFER_SIZE = 32;
	private byte[] tmpBuffer = JCSystem.makeTransientByteArray(BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
	private RandomData random;

	public static void install(byte[] bArray, short bOffset, byte bLength)
	{
		new DemoApplet(bArray, bOffset, bLength);
	}

	public DemoApplet(byte[] buffer, short offset, byte length)
	{
		random = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
		register();
	}

	public void process(APDU apdu)
	{
		byte[] apduBuffer = apdu.getBuffer();
		byte cla = apduBuffer[ISO7816.OFFSET_CLA];
		byte ins = apduBuffer[ISO7816.OFFSET_INS];
		short lc = (short)apduBuffer[ISO7816.OFFSET_LC];
		short p1 = (short)apduBuffer[ISO7816.OFFSET_P1];
		short p2 = (short)apduBuffer[ISO7816.OFFSET_P2];

		random.generateData(tmpBuffer, (short) 0, BUFFER_SIZE);

		Util.arrayCopyNonAtomic(tmpBuffer, (short)0, apduBuffer, (short)0, BUFFER_SIZE);
		apdu.setOutgoingAndSend((short)0, BUFFER_SIZE);
	}

	public boolean select(boolean b) {
		return true;
	}

	public void deselect(boolean b) {

	}
}
