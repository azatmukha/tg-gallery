package com.azatmukha.tg.gallery

import java.io.FileInputStream
import java.util.Properties

private var TOKEN: String? = null
private var DIRECTORY: String? = null
private var WHITELIST: Set<Long>? = null

private val fileProperties: Properties by lazy {
    val props = Properties()
    val filePath = System.getenv("CONFIGURATION_FILEPATH")
    if (!filePath.isNullOrBlank()) {
        FileInputStream(filePath).use { props.load(it) }
    }
    props
}

private fun getProperty(key: String): String {
    val envValue = System.getenv(key)
    if (!envValue.isNullOrBlank()) return envValue

    val result = requireNotNull(fileProperties.getProperty(key)) {
        "Could not find $key in environment variables or configuration file"
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
