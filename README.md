# BungeeWebsocket

Let BungeeCord use WebSocket protocol to prevent DDOS attacks or optimize the network

Client(mod required) -> CDN(such as cloudflare) -> BungeeCord -> GameServer

1.16.5 Client MOD: https://github.com/IceSoulHanxi/lightfall-client/tree/websocket </br>
1.12.2 Client MOD: https://github.com/IceSoulHanxi/MinecraftWebsocket/ </br>
1.7.10 Client MOD: https://github.com/IceSoulHanxi/MinecraftWebsocket/tree/1.7.10

## Setup

Download the plugin jar into the plugin directory of the BungeeCord server </br>
Download the Mod jar and put it in the Forge Mod folder </br>
Modify the server address to http or ws and connect </br>
E.g `http://localhost:25577`

After ensuring that there is no exception, you can use Nginx or CDN to reverse proxy server port

For jdk9+, you must to add the JVM option -Djdk.attach.allowAttachSelf=true </br>
E.g `java -Djdk.attach.allowAttachSelf=true -jar BungeeCord.jar`