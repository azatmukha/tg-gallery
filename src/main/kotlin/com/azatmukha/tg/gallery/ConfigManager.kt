package com.azatmukha.tg.gallery

import java.util.Properties

private var TOKEN: String? = null
private var DIRECTORY: String? = null
private var WHITELIST: Set<Long>? = null

private fun getProperty(key: String): String {
    val props = Properties()
    val input = object {}.javaClass
        .getResourceAsStream("/.env")
    props.load(input)

    val result = requireNotNull(props.getProperty(key)) {
        "Could not find $key value in .env configuration"
    }
    return result
}

fun getToken(): String {
    TOKEN?.let { return it }

    val tokenProp = getProperty("TOKEN")

    TOKEN = tokenProp
    return tokenProp
}

fun getStorageDirectory(): String {
    DIRECTORY?.let { return it }

    val directoryProp = getProperty("DIRECTORY")

    DIRECTORY = directoryProp
    return directoryProp
}

fun getWhiteList(): Set<Long> {
    WHITELIST?.let { return it }

    val whitelistProp = getProperty("WHITELIST")

    val result = whitelistProp
        .split(",")
        .map{ idStr ->
            idStr.toLong()
        }
        .toSet()

    WHITELIST = result
    return result
}
