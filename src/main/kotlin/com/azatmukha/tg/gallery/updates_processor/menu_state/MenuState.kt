package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.azatmukha.tg.gallery.updates_processor.UpdatesProcessor
import com.pengrad.telegrambot.model.Message

abstract class MenuState(
    val state: MenuStateEnum,
    protected val updatesProcessor: UpdatesProcessor
) {

    abstract fun doState(userId: Long)
    abstract fun processMessage(message: Message)
}
