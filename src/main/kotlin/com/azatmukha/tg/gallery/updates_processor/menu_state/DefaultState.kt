package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.SendMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val NEW_COLLECTION_MESSAGE = "Create new collection"
private const val ADD_IMAGE_MESSAGE = "Add images to existing collection"

const val DEFAULT_STATE_NAME = "DEFAULT"

class DefaultState(
    updatesProcessor: UpdatesProcessor
): MenuState(DEFAULT_STATE_NAME, updatesProcessor) {
    override fun doState(userId: Long) {
        val newCollectionButton = KeyboardButton(NEW_COLLECTION_MESSAGE)
        val addImagesButton = KeyboardButton(ADD_IMAGE_MESSAGE)

        val keyboard = ReplyKeyboardMarkup(
            arrayOf(newCollectionButton), arrayOf(addImagesButton)
        ).resizeKeyboard(true)


        val message = SendMessage(userId, "Use the keyboard to navigate.")
            .replyMarkup(keyboard)

        updatesProcessor.bot.execute(message)
    }

    override fun processMessage(message: Message) {
        val userId = message.from().id()
        if (message.text() == NEW_COLLECTION_MESSAGE) {
            updatesProcessor.changeState(userId,
                updatesProcessor.getState(COLLECTION_CREATING_STATE_NAME)
            )
            return
        }
        if (message.text() == ADD_IMAGE_MESSAGE) {
            updatesProcessor.changeState(userId,
                updatesProcessor.getState(IMAGE_ADDING_STATE_NAME)
            )
            return
        }
        logger.warn { "Unexpected message is received. Id: ${message.messageId()}, text: ${message.text()}" }

        // Reset the UI state to avoid user's confusion
        doState(userId)
    }
}
