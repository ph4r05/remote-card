package cz.muni.fi.crocs.rcard.server

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.logging.LoggerFactory
import java.util.*

open class WebsocketServer(vertx: Vertx, app: App): BaseVerticle(vertx, app) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var server: HttpServer? = null

    override suspend fun start() {
        startServer()
    }

    open fun startServer(){
        val opts = getServerOptions()
        server = vertx.createHttpServer(opts).webSocketHandler { webSocket: ServerWebSocket ->
            onClientConnected(webSocket)
        }.listen(app.port) {
            logger.info("WS Server listening @ port ${app.port}")
        }
    }

    open fun onClientConnected(webSocket: ServerWebSocket) {
        val client = WebsocketHandler(this, webSocket)
        client.initHooks()
    }

    open fun getServerOptions(): HttpServerOptions {
        return HttpServerOptions()
            .setIdleTimeout(20)
            .setTcpKeepAlive(true)
    }

    open fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    override suspend fun stop() {

    }

    open fun getHandler(): Handler {
        return app.getHandler()
    }


}
