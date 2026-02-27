package com.azatmukha.tg.gallery

import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Info message at the beginning of the run" }

    val updatesProcessor = UpdatesProcessor()
    updatesProcessor.run()
}
