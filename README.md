## JavaCard remote access

### WebSocket API

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
