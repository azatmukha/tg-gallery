package com.azatmukha.tg.gallery.updates_processor

import com.azatmukha.tg.gallery.getToken
import com.azatmukha.tg.gallery.getWhiteList
import com.azatmukha.tg.gallery.updates_processor.menu_state.CollectionCreatingState
import com.azatmukha.tg.gallery.updates_processor.menu_state.DefaultState
import com.azatmukha.tg.gallery.updates_processor.menu_state.ImageAddingState
import com.azatmukha.tg.gallery.updates_processor.menu_state.MenuState
import com.azatmukha.tg.gallery.updates_processor.menu_state.MenuStateEnum
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.MessageEntity.Type
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.SendMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.EnumMap

private val logger = KotlinLogging.logger {}

private const val WELCOME_MESSAGE =
"""
Welcome to Telegram Gallery bot!
"""
private const val NO_PERMISSION_MESSAGE =
"""
You do not have permission to use this bot.
Please, contact the developer if you think you should have the access.
"""
private const val HELP_MESSAGE =
"""
I can help you manage endless images that your friends share with you in Telegram and save them automatically on a host machine. 

Use keyboard to navigate.
"""

class UpdatesProcessor(
    val bot: TelegramBot = TelegramBot(getToken()),
    private val userToMenuState: MutableMap<Long, MenuState> = mutableMapOf(),
) {

    private val availableStates: EnumMap<MenuStateEnum, MenuState> =
        EnumMap(
            listOf(DefaultState(this),
                ImageAddingState(this),
                CollectionCreatingState(this)
            )
                .associateBy { it.state }
        )

    fun getState(state: MenuStateEnum) = requireNotNull(availableStates[state]) {
        "$state state is not available!"
    }

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
                try {
                    processUpdate(update)
                } catch (ex: Exception) {
                    val messageId = update.message()?.messageId()
                    logger.error { "Exception occurred while processing the message $messageId. Reason: ${ex.message}. Stack trace: ${ex.stackTraceToString()}" }
                } catch (ex: NotImplementedError) {
                    // TODO: remove this catch block
                    logger.error { "Error occurred while processing an update. Reason: ${ex.message}. Stack trace: ${ex.stackTraceToString()}" }
                }
            }
        return UpdatesListener.CONFIRMED_UPDATES_ALL
    }

    private fun processUpdate(update: Update) {
        val message = update.message()
        val callbackQuery = update.callbackQuery()
        if (message == null && callbackQuery == null) {
            return
        }

        val userId = callbackQuery?.from()?.id()
            ?: message?.from()?.id()
            ?: return

        if (!getWhiteList().contains(userId)) {
            handleNoPermission(userId, message, callbackQuery)
            return
        }

        if (callbackQuery != null) {
            handleCallbackQueryWrapper(callbackQuery)
            return
        }

        val nonNullMessage = requireNotNull(message)
        if (nonNullMessage.isCommand() && nonNullMessage.text() == "/start") {
            val response = SendMessage(userId, WELCOME_MESSAGE)
            bot.execute(response)
            val newUserState = getState(MenuStateEnum.DEFAULT)
            userToMenuState[userId] = newUserState
            newUserState.doState(userId)
        } else if (nonNullMessage.isCommand() && nonNullMessage.text() == "/help") {
            val response = SendMessage(userId, HELP_MESSAGE)
            bot.execute(response)
        } else {
            userToMenuState.computeIfAbsent(userId) {
                this.getState(MenuStateEnum.DEFAULT)
            }
                .processMessage(nonNullMessage)
        }
    }

    internal fun changeState(userId: Long, state: MenuState) {
        userToMenuState[userId] = state
        state.doState(userId)
    }

    private fun handleNoPermission(userId: Long, message: Message?, callbackQuery: CallbackQuery?) {
        val chatId = message?.chat()?.id() ?: callbackQuery?.message()?.chat()?.id()
        if (chatId != null) {
            val response = SendMessage(chatId, NO_PERMISSION_MESSAGE)
            bot.execute(response)
        }
        callbackQuery?.let { bot.execute(AnswerCallbackQuery(it.id())) }
    }

    private fun handleCallbackQueryWrapper(callbackQuery: CallbackQuery) {
        handleCallbackQuery(callbackQuery);
        bot.execute(AnswerCallbackQuery(callbackQuery.id()))
    }

    private fun handleCallbackQuery(callbackQuery: CallbackQuery) {
        val data = callbackQuery.data()
        if (data.isNullOrBlank()) {
            logger.warn { "Callback data is empty" }
            return
        }

        val separatorIndex = data.indexOf("::")
        if (separatorIndex == -1) {
            logger.warn { "Callback data '${data}' has no state prefix" }
            return
        }

        val statePrefix = data.take(separatorIndex)
        val menuStateEnum = runCatching { MenuStateEnum.valueOf(statePrefix) }
            .onFailure { logger.warn { "Unknown state '$statePrefix' in callback data '$data'" } }
            .getOrNull()

        if (menuStateEnum == null) {
            return
        }

        val targetState = getState(menuStateEnum)
        targetState.processCallbackQuery(callbackQuery)
    }

    private fun Message.isCommand(): Boolean {
        return entities()?.firstOrNull()?.type()
            ?.let { type -> type == Type.bot_command }
            ?: false
    }
}
