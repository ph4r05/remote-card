package cz.muni.fi.crocs.rcard.server

import com.beust.klaxon.Klaxon
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ResponseContentTypeHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.TimeoutHandler
import java.io.StringReader

open class RestServer(vertx_: Vertx, app: App): BaseVerticle(vertx_, app) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val klaxon = Klaxon()
    private lateinit var server: HttpServer
    private lateinit var router: Router

    override suspend fun start() {
        startServer()
    }

    private fun startServer(){
        logger.info("Starting REST server")
        val options = getServerOptions()
        server = vertx.createHttpServer(options)
        val port = app.webPort
        initHooks()
        server.listen(port) {
            logger.info("REST Server listening @ $port")
        }
    }

    open fun getServerOptions(): HttpServerOptions {
        return HttpServerOptions()
            .setIdleTimeout(20)
            .setTcpKeepAlive(true)
    }

    override suspend fun stop() {
        super.stop()
    }

    open fun getHandler(): Handler {
        return app.getHandler()
    }

    fun initHooks() {
        val handler = getHandler()
        router = Router.router(vertx)
        router.route("/static/*").handler(StaticHandler.create())
        router.route("/v1/*").handler(TimeoutHandler.create(5000))
        router.route("/v1/*").handler(ResponseContentTypeHandler.create())
        router
            .route("/v1/card")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                logger.info("Card action from ${ctx.request().remoteAddress()}")
                handler.onGlobalCtxAsync {
                    handleCard(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/is_connected")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleIsConnected(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/connect")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleConnect(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/disconnect")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleDisconnect(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/atr")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleAtr(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/select/:aid")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleSelect(ctx)
                }
            }

        router
            .route("/v1/card/:ctype/:cidx/cmd/:cmd")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                handler.onGlobalCtxAsync {
                    handleCmd(ctx)
                }
            }

        // ping handler
        router
            .route("/v1/ping")
            .produces("application/json")
            .handler(BodyHandler.create())
            .handler { ctx ->
                logger.info("ping access ${ctx.request().remoteAddress()}")
                handler.onGlobalCtxAsync { handlePing(ctx) }
            }

        server.requestHandler(router)
        logger.info("REST hooks initialized")
    }

    open suspend fun handleCard(ctx: RoutingContext) {
        val ctxResp = ctx.response()
        try {
            val reqBody = ctx.bodyAsJson
            val reqParam = ctx.queryParam("req")
            val req = if (reqBody?.isEmpty == false) reqBody else JsonObject(if (reqParam.isEmpty()) "{}" else reqParam.first())
            handleCore(req, ctxResp)
            return

        } catch (e: Exception){
            logger.info("Error: cmd failed $e", e)
            val resp = JsonObject()
            resp.put("success", 0)
            resp.put("error", "Exception: ${e.localizedMessage}")
            write(ctxResp, resp)
        }
    }

    open suspend fun handleCore(req: JsonObject, ctxResp: HttpServerResponse): JsonObject {
        var resp = JsonObject()
        val handler = getHandler()

        try {
            handler.onClientConnect()
            logger.info("Req: $req")

            val reqKlax = klaxon.parseJsonObject(StringReader(req.toString()))
            val respKlax = handler.actionHandler(reqKlax)
            resp = JsonObject(respKlax)

        } catch (e: Exception){
            logger.info("Error: ping failed $e", e)
            resp.put("result", -1)
            resp.put("error", "Exception: ${e.localizedMessage}")
        } finally {
            handler.onClientDisconnect()
        }
        write(ctxResp, resp)
        return resp
    }

    open fun handlePing(ctx: RoutingContext) {
        val ctxResp = ctx.response()
        val resp = JsonObject()

        try {
            resp.put("success", 1)

        } catch (e: Exception){
            logger.info("Error: ping failed $e", e)
            resp.put("success", 0)
            resp.put("error", "Exception: ${e.localizedMessage}")
        }

        write(ctxResp, resp)
    }

    private fun extractTarget(ctx: RoutingContext, req: JsonObject? = null): JsonObject {
        val r = req ?: JsonObject()
        val ctypeStr = ctx.request().getParam("ctype") ?: throw RuntimeException("ctype not specified")
        val cIdxStr = ctx.request().getParam("cidx") ?: throw RuntimeException("cidx not specified")
        r.put("target", ctypeStr)
        r.put("idx", cIdxStr.toInt())
        return r
    }

    private suspend fun handleIsConnected(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "is_connected")
        handleCore(r, ctx.response())
    }

    private suspend fun handleConnect(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "connect")
        handleCore(r, ctx.response())
    }

    private suspend fun handleDisconnect(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "disconnect")
        handleCore(r, ctx.response())
    }

    private suspend fun handleAtr(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "atr")
        handleCore(r, ctx.response())
    }

    private suspend fun handleSelect(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "select")
        r.put("aid", ctx.request().getParam("aid"))
        handleCore(r, ctx.response())
    }

    private suspend fun handleCmd(ctx: RoutingContext) {
        val r = extractTarget(ctx)
        r.put("action", "send")
        r.put("apdu", ctx.request().getParam("cmd"))
        handleCore(r, ctx.response())
    }

    private fun write(response: HttpServerResponse, jsResp: JsonObject){
        response.isChunked = true
        response.putHeader("content-type", "application/json")
        response.write(jsResp.toString()).end()
    }
}
