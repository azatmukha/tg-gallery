package com.azatmukha.tg.gallery.updates_processor

import com.azatmukha.tg.gallery.getToken
import com.azatmukha.tg.gallery.getWhiteList
import com.azatmukha.tg.gallery.updates_processor.menu_state.MenuState
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.MessageEntity.Type
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val NO_PERMISSION_MESSAGE =
    """        
        You do not have permission to use this bot.
        Please, contact @man0402 if you think you should have the access.
    """
private const val HELP_MESSAGE =
    """
        I can help you manage endless images that your friends share with \
        you in Telegram and save them automatically on a host machine. 

        Use keyboard to navigate.
    """

class UpdatesProcessor(
    private val bot: TelegramBot = TelegramBot(getToken()),
    private val userToMenuState: MutableMap<Long, MenuState> = mutableMapOf()
) {
    fun run() {
        bot.setUpdatesListener(
            { updates ->
                processUpdates(updates)
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

    fun processUpdates(updates: List<Update?>?): Int {
        if (updates == null)
        {
            return UpdatesListener.CONFIRMED_UPDATES_NONE
        }
        updates
            .filterNotNull()
            .forEach{ update ->
                processUpdate(update)
            }
        return UpdatesListener.CONFIRMED_UPDATES_ALL
    }

    private fun processUpdate(update: Update) {
        val message = update.message()
        val userId = message.from().id()

        if (!getWhiteList().contains(userId)) {
            val response = SendMessage(userId, NO_PERMISSION_MESSAGE)
            bot.execute(response)
            return
        }

        if (message.isCommand() && message.text() == "/start") {
//          userToMenuState[userId] = TODO
            requireNotNull(userToMenuState[userId]){
                "The state of user with id $userId was not found!"
            }.doState(userId)
        } else if (message.isCommand() && message.text() == "/help") {
            val response = SendMessage(userId, HELP_MESSAGE)
            bot.execute(response)
        } else {
            userToMenuState[userId]?.processMessage(message)
        }
    }

    internal fun changeState(userId: Long, state: MenuState) {
        userToMenuState[userId] = state
        userToMenuState[userId]!!.doState(userId)
    }

    private fun Message.isCommand(): Boolean {
        return entities().firstOrNull()?.type()
            ?.let { type -> type == Type.bot_command }
            ?: false
    }
}