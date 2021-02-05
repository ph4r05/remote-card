package cz.muni.fi.crocs.rcard.server

import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.CoroutineVerticle

open class BaseVerticle(val vertx_: Vertx, val app: Server): CoroutineVerticle()  {
}
