package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.azatmukha.tg.gallery.getStorageDirectory
import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Document
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.PhotoSize
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageText
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
import kotlin.text.RegexOption

private val logger = KotlinLogging.logger {}

private const val INITIAL_MESSAGE =
"""
Please send a collection name that you want to add images into.
The list of collections:
<LIST_OF_COLLECTIONS>
<PAGE_NUMBER>
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
private const val FINISH_KEYBOARD_PROMPT = "Use keyboard to finish media uploading."

private const val PAGE_SIZE = 10

private val PREVIOUS_BUTTON_DATA = MenuStateEnum.IMAGE_ADDING.name + "::prev"
private val NEXT_BUTTON_DATA = MenuStateEnum.IMAGE_ADDING.name + "::next"

private const val FINISH_MESSAGE = "Finish"

class ImageAddingState(
    updatesProcessor: UpdatesProcessor
): MenuState(MenuStateEnum.IMAGE_ADDING, updatesProcessor)  {

    val userToCollection: MutableMap<Long, String> = mutableMapOf()

    override fun doState(userId: Long) {
        val textToSend = prepareInitialMessageText(0)

        val initialMessage = SendMessage(userId, textToSend)
            .replyMarkup(getCollectionsNavigationMarkup())
        updatesProcessor.bot.execute(initialMessage)

        val finishKeyboardMessage = SendMessage(userId, FINISH_KEYBOARD_PROMPT)
            .replyMarkup(getKeyboard())
        updatesProcessor.bot.execute(finishKeyboardMessage)
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

    override fun processCallbackQuery(query: CallbackQuery) {
        val message = query.message()
        val (currentPage, totalPages) = extractPageIndices(message.text())
            ?: run {
                logger.warn { "Callback message ${message.messageId()} is missing a Page header" }
                return
            }

        logger.info { "Received callback for page $currentPage/$totalPages" }
        val chatId = requireNotNull(message?.chat()?.id()) {
            "Callback query ${query.id()} does not have chat id"
        }

        val currentPageIndex = currentPage - 1
        val newPageIndex =
            if (query.data().contains("prev") && currentPageIndex != 0) {
                currentPageIndex - 1 % totalPages
            } else if (query.data().contains("next") && currentPage != totalPages){
                currentPageIndex + 1 % totalPages
            } else {
                currentPageIndex
            }
        val updatedText = prepareInitialMessageText(newPageIndex)

        val editRequest = EditMessageText(chatId, message.messageId(), updatedText)
            .replyMarkup(getCollectionsNavigationMarkup())
        updatesProcessor.bot.execute(editRequest)
    }

    // args: pageIndex starting from 0
    private fun prepareInitialMessageText(pageIndex: Int): String {
        val pageAmount = getCollectionList().size.let { size ->
            size/PAGE_SIZE + if (size % PAGE_SIZE == 0)  0 else 1
        }

        return INITIAL_MESSAGE
            .replace("<LIST_OF_COLLECTIONS>",
                getCollectionsPage(pageIndex).joinToString("\n")
            )
            .let { text ->
                if (pageAmount > 1) {
                    text.replace("<PAGE_NUMBER>", "\nPage: ${pageIndex + 1}/$pageAmount")
                } else {
                    text.replace("<PAGE_NUMBER>", "")
                }
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
        val text = message.text()
        if (text == FINISH_MESSAGE) {
            userToCollection.remove(userId)

            updatesProcessor.changeState(userId,
                updatesProcessor.getState(MenuStateEnum.DEFAULT)
            )
            return
        }

        val currentCollection = userToCollection[userId]
        if (getCollectionList().contains(text) &&
            currentCollection != null) {

            val textToSend = UNEXPECTED_COLLECTION_NAME
                .replace("<COLLECTION_NAME>",
                    currentCollection
                )
            val messageToSend = SendMessage(userId, textToSend)
                .replyMarkup(getKeyboard())

            updatesProcessor.bot.execute(messageToSend)
        }

        if (getCollectionList().contains(text) &&
            currentCollection == null) {

            userToCollection[userId] = text

            val textToSend = COLLECTION_SELECTED
                .replace(
                    "<COLLECTION_NAME>",
                    text
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

    private fun getCollectionsNavigationMarkup(): InlineKeyboardMarkup {
        val previousButton = InlineKeyboardButton("Previous")
            .callbackData(PREVIOUS_BUTTON_DATA)
        val nextButton = InlineKeyboardButton("Next")
            .callbackData(NEXT_BUTTON_DATA)

        return InlineKeyboardMarkup(
            arrayOf(previousButton, nextButton)
        )
    }

    private fun getTargetPath(userId: Long, collectionName: String, filename: String): String =
        "${getStorageDirectory()}/$collectionName/$filename"

    private fun getCollectionList(): List<String> =
        Path(getStorageDirectory())
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.pathString }
            .map { it.split("/").last() }
    
    private fun getCollectionsPage(pageIndex: Int): List<String> =
        getCollectionList()
            .reversed()
            .chunked(PAGE_SIZE)[pageIndex]

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

    private fun extractPageIndices(text: String?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) {
            return null
        }

        val match = PAGE_LINE_REGEX.find(text)
            ?: return null

        val (current, total) = match.destructured
        return current.toIntOrNull()?.let { currentValue ->
            total.toIntOrNull()?.let { totalValue ->
                currentValue to totalValue
            }
        }
    }

    // TODO: it may be a good idea to move constants to companion objects
    companion object {
        private val PAGE_LINE_REGEX = Regex("^Page: \\s*(\\d+)\\s*/\\s*(\\d+)$", RegexOption.MULTILINE)
    }
}
