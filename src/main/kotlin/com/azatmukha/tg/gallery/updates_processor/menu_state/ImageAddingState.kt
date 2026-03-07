package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.azatmukha.tg.gallery.getStorageDirectory
import com.azatmukha.tg.gallery.getToken
import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.Document
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.PhotoSize
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.GetFile
import com.pengrad.telegrambot.request.SendMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.channels.Channels
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

private const val INITIAL_MESSAGE =
"""
Please send a collection name that you want to add images into.
The list of collections:
<LIST_OF_COLLECTIONS>
"""
private const val COLLECTION_SELECTED =
"""
Collection <COLLECTION_NAME> has been selected. 
Send photos/files to add them to the collection.

Use keyboard to finish media uploading.
"""
private const val UNEXPECTED_COLLECTION_NAME =
"""
Seems like you've sent another collection name before finishing the upload to the selected one.
If you want to upload media to another collection, please, finish this upload first.
The selected collection remains <COLLECTION_NAME>.

Use keyboard to finish media uploading.
"""
private const val SELECT_COLLECTION_REMINDER =
"""
Please select a collection first. 
"""

private const val FINISH_MESSAGE = "Finish"

class ImageAddingState(
    updatesProcessor: UpdatesProcessor
): MenuState(MenuStateEnum.IMAGE_ADDING, updatesProcessor)  {

    val userToCollection: MutableMap<Long, String> = mutableMapOf()

    override fun doState(userId: Long) {
        val textToSend = INITIAL_MESSAGE
            .replace("<LIST_OF_COLLECTIONS>",
                getCollectionList().joinToString("\n")
            )

        val message = SendMessage(userId, textToSend)
            .replyMarkup(getKeyboard())

        updatesProcessor.bot.execute(message)
    }

    override fun processMessage(message: Message) {
        val userId = message.from().id()
        if ((message.text() == null || message.text().isEmpty()) &&
            userToCollection[userId] == null) {

            val message = SendMessage(userId, SELECT_COLLECTION_REMINDER)
                .replyMarkup(getKeyboard())

            updatesProcessor.bot.execute(message)
            return
        }

        if (message.text() != null) {
            processTextInMessage(message)
        }

        if (message.document() != null) {
            processDocumentInMessage(userId,
                message.messageId(),
                message.document()
            )
        }

        if (message.photo() != null) {
            processPhotoInMessage(userId,
                message.messageId(),
                message.photo().toList()
            )
        }
    }

    private fun processPhotoInMessage(userId: Long, messageId: Int, photos: List<PhotoSize?>) {
        val photosNullSafe = photos.filterNotNull()

        require(!photosNullSafe.isEmpty()) {
            "Photos list is empty after filtration. User id: $userId, message id: $messageId"
        }

        // Only take the file with the highest definition
        val photoToDownload = photosNullSafe
            .fold(photosNullSafe.first()) { result, current->
                if (result.fileSize() < current.fileSize()) {
                    current
                } else {
                    result
                }
            }

        // Download the file
        val fileUrl = requireNotNull(getFileUrl(photoToDownload.fileId())) {
            "Could not retrieve file url. User id: $userId, message id: $messageId"
        }
        downloadFile(URI(fileUrl),
            getTargetPath(userId,
                requireNotNull(userToCollection[userId]){
                    "User $userId does not have a collection assigned"
                },
                "image_$messageId.jpg")
        )
    }

    private fun processDocumentInMessage(userId: Long, messageId: Int, document: Document) {
        val fileUrl = requireNotNull(getFileUrl(document.fileId())) {
            "Could not retrieve file url. User id: $userId, message id: $messageId"
        }
        downloadFile(
            URI(fileUrl),
            getTargetPath(
                userId,
                requireNotNull(userToCollection[userId]),
                document.fileName()
            )
        )
    }

    private fun processTextInMessage(message: Message) {
        val userId = message.from().id()
        if (message.text() == FINISH_MESSAGE) {
            userToCollection.remove(userId)

            updatesProcessor.changeState(userId,
                updatesProcessor.getState(MenuStateEnum.DEFAULT)
            )
            return
        }

        val currentCollection = userToCollection[userId]
        if (getCollectionList().contains(message.text()) &&
            currentCollection != null) {

            val textToSend = UNEXPECTED_COLLECTION_NAME
                .replace("<COLLECTION_NAME>",
                    currentCollection
                )
            val messageToSend = SendMessage(userId, textToSend)
                .replyMarkup(getKeyboard())

            updatesProcessor.bot.execute(messageToSend)
        }

        if (getCollectionList().contains(message.text()) &&
            currentCollection == null) {

            val selectedCollection = message.text()
            userToCollection[userId] = selectedCollection

            val textToSend = COLLECTION_SELECTED
                .replace(
                    "<COLLECTION_NAME>",
                    selectedCollection
                )
            val messageToSend = SendMessage(userId, textToSend)
                .replyMarkup(getKeyboard())

            updatesProcessor.bot.execute(messageToSend)
        }
    }

    private fun getKeyboard(): ReplyKeyboardMarkup {
        val finishButton = KeyboardButton(FINISH_MESSAGE)

        return ReplyKeyboardMarkup(
            arrayOf(finishButton)
        ).resizeKeyboard(true)
    }

    private fun getTargetPath(userId: Long, collectionName: String, filename: String): String =
        "${getStorageDirectory()}/$collectionName/$filename"

    private fun getCollectionList(): List<String> =
        Path(getStorageDirectory())
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.pathString }
            .map { it.split("/").last() }

    private fun getFileUrl(fileId: String): String? {
        val request = GetFile(fileId)
        val getFileResponse = updatesProcessor.bot.execute(request)

        val file = getFileResponse.file()

        return file
            ?.let {
                updatesProcessor.bot.getFullFilePath(it)
            }
    }

    private fun downloadFile(fileUri: URI, filepath: String) {
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
}
