package com.azatmukha.tg.gallery

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.GetFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.channels.Channels

private const val DIRECTORY = "DIRECTORY"

private val logger = KotlinLogging.logger {}

private val bot = TelegramBot("BOT_TOKEN")

fun getFileUrl(fileId: String): String? {
    val request = GetFile(fileId)
    val getFileResponse = bot.execute(request)

    val file = getFileResponse.file()

    return file
        ?.let {
            bot.getFullFilePath(it)
        }
}

fun downloadFile(fileUri: URI, filepath: String) {
    try {
        val readableByteChannel = Channels.newChannel(fileUri.toURL().openStream())
        val fileOutputStream = FileOutputStream(filepath)
        val fileChannel = fileOutputStream.getChannel()
        fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE)
        logger.info { "File $fileUri is successfully downloaded to $filepath" }
    } catch (ex: IOException) {
        logger.error { "Error occurred while downloading $fileUri.file to $filepath. Reason: ${ex.message}" }
    } catch (ex: FileNotFoundException) {
        logger.error { "Could not create file $filepath. Reason: ${ex.message}" }
    }
}

fun main(args: Array<String>) {

    logger.info { "Info message at the beginning of the run" }
    bot.setUpdatesListener(
        { updates ->
            if (updates == null)
            {
                UpdatesListener.CONFIRMED_UPDATES_NONE
            }
            updates
                .mapNotNull { it.message()}
                .forEach{ message ->
                    println(message.text())

                    message.document()
                        ?.also { document ->
                            getFileUrl(document.fileId())
                                ?.let {
                                    downloadFile(URI(it), "$DIRECTORY/${document.fileName()}")
                                }
                        }

                    message.photo()
                        ?.filterNotNull()
                        // Only take the file with the highest definition
                        ?.fold(message.photo().first()) { result, current->
                            if (result.fileSize() < current.fileSize()) {
                                current
                            } else {
                                result
                            }
                        }
                        // Download the file
                        ?.also { photo ->
                            getFileUrl(photo.fileId())
                                ?.let {
                                    downloadFile(URI(it), "$DIRECTORY/image_${message.messageId()}.jpg")
                                }
                        }

                }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        },
        { exception ->
            exception.response()?.let { response ->
                // Bad response from Telegram
                val errorCode = response.errorCode()
                val description = response.description()
                logger.error { "Telegram API error $errorCode: $description" }
            } ?: run {
                // Probably network error
                logger.error { "Unexpected error occurred: ${exception.message}. Stack trace: ${exception.stackTraceToString()}" }
            }
        }
    )
}