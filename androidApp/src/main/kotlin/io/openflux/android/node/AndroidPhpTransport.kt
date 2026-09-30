package io.openflux.android.node

import io.openflux.bridge.mobile.Mobile
import io.openflux.desktop.model.PhpHostingException
import io.openflux.desktop.model.PhpProgress
import io.openflux.desktop.service.PhpCallTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The hosting wizard's steps through the core's PhpCall (the same steps the
 * desktop runs through `--node-wizard`). The call blocks, so it runs off the
 * main thread, one at a time, while a poller hands the upload's progress
 * (PhpProgress) to [onProgress].
 */
class AndroidPhpTransport : PhpCallTransport {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    override suspend fun call(method: String, params: JsonObject, onProgress: (PhpProgress) -> Unit): JsonObject =
        withContext(Dispatchers.IO) {
            lock.withLock {
                coroutineScope {
                    fun drain() {
                        Mobile.phpProgress().orEmpty().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            runCatching { json.decodeFromString(PhpProgress.serializer(), line) }.getOrNull()?.let(onProgress)
                        }
                    }
                    val poller = launch { while (isActive) { delay(200); drain() } }
                    try {
                        val answer = Mobile.phpCall(method, params.toString())
                        runCatching { json.parseToJsonElement(answer).jsonObject }
                            .getOrElse { throw PhpHostingException("bad_params", method, "the core's answer was not JSON") }
                    } finally {
                        poller.cancel()
                        drain()
                    }
                }
            }
        }

    override fun close() = Mobile.phpCancel()
}
