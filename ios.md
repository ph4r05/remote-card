## iOS version, limitations

Unfortunately, an iOS version of a remote-reader does not currently exist.
There are few limitations on this platform:

### AID selection
iOS App [has to specify AIDs](https://developer.apple.com/documentation/corenfc/nfctagreadersession) of supported applets.
NFC stack then performs SELECT command over all provided AIDs specified by the app. When SELECT succeeds,
app callback is called and control is passed to the app.

Thus if the card is empty, contains applets with unknown AIDs, AIDs are not specified in `Info.plist` or the AID is blacklisted,
iOS app is not able to communicate with the card.

Cards have CardManagers installed (applet management, installation). Unfortunately, some AIDs are [blacklisted](https://developer.apple.com/forums/thread/122314?answerId=381879022#381879022),
such as `A000000003000000`. Thus empty card with CardManager with this AID is not selectable by iOS app.

You may want to add CardManagers from known [AID list](https://www.eftlab.com/knowledge-base/211-emv-aid-rid-pix/)
to maximize possibility of supporting the card.

Possible workarounds, all require card setup with a card reader:
- Change CardManager AID, e.g., via `gp --rename-isd`
- Install minimalistic placeholder applet with known AID. If this AID is present, it can be picked by the iOS app.

### Session duration
NFC session duration is caped to 20 seconds since NFC dialog popup. If you need card session to last longer,
it is not possible to use iOS NFC stack.

Also, there is timeout 2 seconds for a card response. If card takes longer to respond to a single APDU, iOS app
cannot support the use-case.

### Possible implementation

- iOS NFC app starts listening on TCP socket.
- On reception of a command, it opens NFC session and prompts user to tap the card (timeout 20s starts counting)
- After card is tapped, the app starts communication, relays APDUs in the same way as Android remote-reader does
- If NFC session is invalidated by iOS, send back error SW code or data packet with negative length.
- If disconnect command is received, close NFC session gracefully.

### Useful sources:
- https://github.com/ph4r05/ios-14-nfc-bug-poc
- https://github.com/AndyQ/NFCPassportReader
- https://github.com/Tangem/tangem-sdk-ios
