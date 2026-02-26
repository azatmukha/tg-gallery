package com.azatmukha.tg.gallery.updates_processor.menu_state

import com.pengrad.telegrambot.model.Message

interface MenuState {
    fun doState(userId: Long)
    fun processMessage(message: Message)
}