/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package app.roomtorent.figmatocompose

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty, port = 8080, host = "0.0.0.0",
        watchPaths = listOf("classes"), module = Application::main
    ).start(true)
}
