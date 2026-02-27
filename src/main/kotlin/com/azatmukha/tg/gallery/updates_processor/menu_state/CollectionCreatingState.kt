package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.azatmukha.tg.gallery.getStorageDirectory
import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.SendMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

private val logger = KotlinLogging.logger {}

const val COLLECTION_CREATING_STATE_NAME = "COLLECTION_CREATING"

private const val INITIAL_MESSAGE =
"""
Enter a collection name to be created. 
We recommend using YYYY-MM-DD_YYYY-MM-DD_event format for easier navigation, where the first date is the start of the event and the second one is the end.

To finish collection creation, use the keyboard.
"""
private const val COLLECTION_CREATED_SUCCESSFULLY_MESSAGE =
"""
The collection <COLLECTION_NAME> has been created successfully. You can add photos to it or list your collections after you finish creating.

To finish collection creation, use the keyboard.
"""

private const val FINISH_MESSAGE = "Finish"

class CollectionCreatingState(
    updatesProcessor: UpdatesProcessor
): MenuState(COLLECTION_CREATING_STATE_NAME, updatesProcessor) {
    override fun doState(userId: Long) {
        val message = SendMessage(userId, INITIAL_MESSAGE)
            .replyMarkup(getKeyboard())

        updatesProcessor.bot.execute(message)
    }

    override fun processMessage(message: Message) {
        val userId = message.from().id()
        if (message.text() == FINISH_MESSAGE) {
            updatesProcessor.changeState(userId,
                updatesProcessor.getState(DEFAULT_STATE_NAME)
            )
            return
        }
        val collectionCreatingError = createCollection(message.text())
        val messageToSend =
            if (collectionCreatingError == null) {
                val textToSend = COLLECTION_CREATED_SUCCESSFULLY_MESSAGE
                    .replace("<COLLECTION_NAME>", message.text())
                SendMessage(userId, textToSend)
                    .replyMarkup(getKeyboard())
            } else {
                SendMessage(userId, collectionCreatingError)
                    .replyMarkup(getKeyboard())
            }

        updatesProcessor.bot.execute(messageToSend)
    }

    // Tries to create a new directory inside the storage directory.
    // Returns null if the directory was created successfully.
    //         error description if any.
    private fun createCollection(name: String): String? {
        val fullDirectoryName = "${getStorageDirectory()}/$name"
        val newDirectory = Path(fullDirectoryName)
        if (newDirectory.exists()) {
            return "The collection already exists."
        }

        try {
            newDirectory.createDirectory()
        } catch (ex: Exception) {
            logger.error { "Unexpected exception occurred while creating directory $fullDirectoryName. Reason: ${ex.message}. Backtrace: ${ex.stackTraceToString()}" }
            return "Unknown error occurred. Please, contact the developer."
        }

        return null
    }

    private fun getKeyboard(): ReplyKeyboardMarkup {
        val finishButton = KeyboardButton(FINISH_MESSAGE)

        return ReplyKeyboardMarkup(
            arrayOf(finishButton)
        ).resizeKeyboard(true)
    }
}
