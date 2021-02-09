## VSmartCard

## Installing vpcd on OSX Catalina / BigSur

[Vpcd](https://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html) is a local virtual card reader
that registers to PCSC as a card reader driver. It enables you to virtualize cards that act as a physical cards
to the PCSC system / smart card application.

Build steps are based on [official ones](https://frankmorgner.github.io/vsmartcard/virtualsmartcard/README.html#building-and-installing-vpcd-on-mac-os-x)
with few extra steps.

```bash
git clone --recursive https://github.com/frankmorgner/vsmartcard.git
cd vsmartcard/virtualsmartcard

brew install help2man gengetopt 
autoreconf --verbose --install
./configure --enable-infoplist
make osx
```

If build fails, try applying patch from the [pull request](https://github.com/frankmorgner/vsmartcard/issues/186).

Sometimes, `make osx` fails, just re-run the command.

- Open `virtualsmartcard-0.8_osx.dmg` and install OpenPACE
- Install VirtualSmartCard, if it fails, do the following.

After installation do not forget to configure Info.plist (below)

```bash
./configure --enable-infoplist
make
make install
```

### Troubleshooting - driver installation

Since El Capitan it is not possible to add drivers to the System volume (snapshot):
```
/usr/libexec/SmartCardServices/drivers/
```

as the following command shows, 

```bash
man SmartCardServices
```

the driver directory should be
```
/usr/local/libexec/SmartCardServices/drivers/
```

Then 
```bash
vim /usr/local/libexec/SmartCardServices/drivers/ifd-vpcd.bundle/Contents/vpcd
```

and make sure that `LIBPATH` points to the correct directory, i.e., `/usr/local/libexec/`

Also make sure that `Info.plist` contains USB device product ID and vendor ID that triggers vpcd load.
List USB devices with:

```bash
system_profiler SPUSBDataType
```

and update `ifdVendorID`, `ifdProductID` values in `Info.plist` accordingly.

Then restart card stack

```bash
sudo killall -9 -m '.*ifdreader'
```

Reconnect USB device you specified in the `Info.plist`

Check if the vpcd is installed correctly
```bash
system_profiler SPSmartCardsDataType
```

On my system the output starts:
```
SmartCards:

    Readers:

      #01: /dev/null:0x8C7B(1) (no card present)
      #02: /dev/null:0x8C7B(2) (no card present)

    Reader Drivers:

      #01: org.debian.alioth.pcsclite.smartcardccid:1.4.32 (/usr/libexec/SmartCardServices/drivers/ifd-ccid.bundle)
      #02: (null):(null) (/usr/local/libexec/SmartCardServices/drivers/ifd-vpcd.bundle)
```
which means the card reader driver is recognized and card reader is present. When you unplug the USB device, the reader disappears.
VPCD is listening on a port 0x8C7B = 35963 by default. 

To test if port is open, try:
```bash
netstat -an -ptcp | grep LISTEN
```

In order to test it, you can use [remote-reader](https://frankmorgner.github.io/vsmartcard/remote-reader/README.html)
project. Set IP address of your host running the VPCD in the Android app and tap the card.

### Troubleshooting - log inspection

Inspect card-related logs:


```bash
log show --predicate '(subsystem == "com.apple.CryptoTokenKit")' --info --debug
```

or 

```bash
log show --process com.apple.ifdreader --info --debug
```

In my case I had error:

```
2021-02-09 14:13:33.654825+0100 0x6e9c95   Error       0x0                  51031  0    com.apple.ifdreader: [com.apple.CryptoTokenKit:smartcard] Failed to parse Info.plist of /usr/local/libexec/SmartCardServices/drivers/ifd-vpcd.bundle, ignoring
```

However, plist file seemed fine, also:

```bash
plutil /usr/local/libexec/SmartCardServices/drivers/ifd-vpcd.bundle/Contents/Info.plist
```

Returns
```
/usr/local/libexec/SmartCardServices/drivers/ifd-vpcd.bundle/Contents/Info.plist: OK
```

The problem was fixed when I have only one element in arrays `ifdVendorID` and `ifdProductID`. 

After each `Info.plist` edit, restart card stack and reconnect USB device.
