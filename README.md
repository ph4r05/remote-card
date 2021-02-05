# JavaCard remote access

Wraps physically connected JavaCards and virtual JCardSim cards behind REST and WebSocket interface.
Enables to connect multiple JavaCards on one host and access them remotely on a different host.

The project contains 3 main parts:
- Server part connects to the cards and provides API via REST and WebSocket interface.
- Client part is a library for connecting to the various card sources, e.g., physical, jcardsim, remote server.
- GP is a GlobalPlatformPro wrapper with remote card channel added, so you can manipulate with the cards, e.g., install applets remotely. 

Project currently does not integrate wit PCSC natively, so the remote card is not transparently installed as a card reader.
This may be added in future releases.

# GP wrapper

GP wrapper takes own parameters like

- `--remote-card` contains remote address endpoint
- `--remote-reader-idx` defines reader index on the server to use, defaults to 0
- `--remote-type` defines card type to use on the remote server, defaults to `card`. Note that JCardSim does not support GP commands now

Always specify there arguments when using the wrapper first, then other arguments to GP follow.
If you have difficulty specifying GP arguments, separate wrapper and GP arguments by `--`.

If `--remote-card` is not specified, locally connected card is used instead, obeying `GP_READER` env var. 

Usage listing installed applets on a remote card:
```bash
./gradlew :gp:run --args="--remote-card=http://127.0.0.1:9901 --list -d"
```

# Server
Server part below.

## Server Usage

```bash
./gradlew :server:run --args='--help'
```

```bash
./gradlew :server:run --args='--reader-idx=0 --allow-pick-reader'
```

## Future work

Not currently supported, may be added later:
- Configuring from JSON configuration file.
- Add authentication to the clients, via access tokens.
- Add reader index allocation and locking policy via reservation tokens.  
- Enable TLS on the HTTP server. Vertx has already support for it, it is just needed to configure it.

### REST API

- `/v1/ping` is a simple ping endpoint, just to test if server responds
- `/v1/card` is endpoint responsible for all card operations

API can be called in two ways:
- GET request with `req` parameter containing JSON request
- POST request with `application/json` type, body contains serialized JSON request

Response is JSON encoded, contains `result` integer field. If there was no error, it contains `0`. 
Other values indicate some problem with processing. Field `error` can contain additional information about the error
(e.g., exception message).

Convenience API endpoints are available according to the scheme:
```
/v1/card/:card_type/reader_index/action
```

Example:
- `/v1/card/card/0/is_connected`
- `/v1/card/card/0/connect`
- `/v1/card/card/0/disconnect`
- `/v1/card/card/0/atr`
- `/v1/card/card/0/select/001020303040506`
- `/v1/card/card/0/cmd/0001000000`
- `/v1/card/sim/0/is_connected`

### API logic - physical cards

All clients connected to the server share connected physical readers. So if multiple clients use the same reader index, 
probably the exception is thrown due to race condition. Simultaneous access by multiple clients to shared readers 
is not yet supported. So clients has to cooperatively decide, which card reader to use. 

Alternatively, you can start multiple server instances, each running on custom ports, each having different `--reader-idx=0`
default card reader index specified. You can thus specify different REST endpoints for different clients.

When server is started, no reader probing is performed. 

Before sending any commands, client has to ask for card connections.
Request for connecting to the physical card, with card index 0 (if `--allow-pick-reader` is specified, otherwise
`--reader-idx` is used). 

#### Connect

```json
{"action": "connect", "target": "card", "idx": 0}
```

#### Is connected

User can test if the connection to the card already exists:
```json
{"action":"is_connected", "target": "card", "idx": 0}
```

Response:
```json
{"result":0, "num_connections":1, "connected":true, "ctype":"card"}
```

In this case, field `connected` indicates the card is already connected and APDU requests can be sent.

#### Select applet

Before sending APDUs to your applet, you have to select the applet
```json
{"action":"select", "target": "card", "idx": 0, "aid":"02ffff0405060708090103"}
```

Response:
```json
{"result":0,"num_connections":1,"response":"000000000000000000006C5544797A91115DC3330EBD003851D239A706FF2AA29000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}
```

#### APDU command

And finally, you can send APDU commands:

```json
{"action":"send","target":"sim","apdu":"0001000000"}
```

Response:
```json
{"result":0,"num_connections":1,"response":"0000000000000000000051373E8B6FDEC284DB569204CA13D2CAA23BD1D85DCA9000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}
```

### API logic - JCardSim

In order to support JCardSim over REST (virtual remote card), one has to add applet code to the project so the simulator can pick it up.

This project adds DemoApplet and DemoApplet2 applets to the `{"target":"sim"}` simulator. 
In order to specify your applets for a simulator, override `App.configureCard` method. 

### WebSocket API

Request and response payloads are the same.
WebSocket is stateful, but you still have to add `target` and `index` to all your requests.

Websocat enables CLI interaction with the WebSocket API
```bash
websocat ws://127.0.0.1:9901
```

If request contains `rid` field, WebSocket API returns `rid` in the response, so the client can pair requests to the responses.

```
{"action":"is_connected"}
{"session":"7812ed35-8017-4431-85e7-637ca96634a0","result":0,"num_connections":1,"connected":false,"ctype":"?"}

{"action":"connect","target":"sim"}
{"session":"7812ed35-8017-4431-85e7-637ca96634a0","result":0,"num_connections":1}

{"action":"select","target":"sim","aid":"01ffff0405060708090102"}
{"session":"7812ed35-8017-4431-85e7-637ca96634a0","result":0,"num_connections":1,"response":"B2F3A2D4FBB002D9F0B51258AF43E98A5423FB145257AE460342361C2199D3809000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}

{"action":"send","target":"sim","apdu":"0001000000"}
{"session":"7812ed35-8017-4431-85e7-637ca96634a0","result":0,"num_connections":1,"response":"9D4169CC1427F2A407191B84AB7ABAACE66A95CA26AB0915803106315080F3319000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}

{"action":"is_connected","target":"sim"}
{"session":"7812ed35-8017-4431-85e7-637ca96634a0","result":0,"num_connections":1,"connected":true,"ctype":"sim"}

{"action":"select","target":"sim","aid":"02ffff0405060708090103"}
{"session":"5ae71460-44b1-44ab-b8f7-ad6987c7faae","result":0,"num_connections":1,"response":"000000000000000000006C5544797A91115DC3330EBD003851D239A706FF2AA29000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}

{"action":"send","target":"sim","apdu":"0001000000"}
{"session":"5ae71460-44b1-44ab-b8f7-ad6987c7faae","result":0,"num_connections":1,"response":"0000000000000000000051373E8B6FDEC284DB569204CA13D2CAA23BD1D85DCA9000","sw":36864,"sw_hex":"9000","sw1":144,"sw2":0}
```
