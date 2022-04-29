# MultiProtocol

Let Minecraft use multiple protocols, such as WebSocket

## Getting started

1. Download the MultiProtocol plugin and put it in the plugins folder
2. Start the server
3. Find the MultiProtocol folder in the plugins and modify the config.yml file
```yaml
websocket:
  enabled: true
  # Custom path such as path: /minecraft to ws://mc.ixnah.com/minecraft
  path: /
  # Get client real IP through Http request before establishing WebSocket connection
  # For example, if the CDN does not provide IP header when establishing WebSocket connection, enable this function
  # After this function is enabled, Query must be added after the path: realAddress such as ws://mc.ixnah.com/?realAddress
  # With custom path ws://mc.ixnah.com/minecraft?realAddress
  ipFromHttp: false
  # Get the real IP address of the client through the Header
  realIpHeaders: [ 'X-Forwarded-For', 'X-Real-Ip' ]
  # Maximum length of Http content
  maxContentLength: 8192
  # Enable normal Http request fallback
  # For example, a normal HTTP request needs to be redirected to https://www.google.com/
  fallback:
    enabled: false
    # Type: redirect, proxy
    type: redirect
    # Add {uri} if you need to pass paths and parameters
    # For example https://www.google.com/${uri}
    url: https://www.google.com/
  # Enable Https
  ssl:
    enabled: false
    # The relative path is plugins/MultiProtocol/server.keystore
    keyStore: server.keystore
    keyStorePassword: 123456
    keyStoreType: JKS
  # Enable the WebSocket whitelist
  whitelist:
    enabled: false
    # CIDR can be used
    addresses: [ '127.0.0.0/8', '10.0.0.0/8', '192.168.0.0/16' ]
```
4. Restart the server
5. Download the MultiProtocol client mod and put it in the mods folder
6. Connect to the server as required by the protocol such as `ws://mc.ixnah.com/minecraft?realAddress`

## Supported protocol

1. WebSocket

### WebSocket

Let Minecraft use WebSocket protocol to prevent DDOS attacks or optimize the network

Client(mod required) -> CDN(such as cloudflare) -> GameServer