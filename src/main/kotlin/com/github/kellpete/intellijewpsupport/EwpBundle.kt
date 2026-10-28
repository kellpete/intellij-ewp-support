package com.github.kellpete.intellijewpsupport

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.EwpBundle"

object EwpBundle {
    private val INSTANCE = DynamicBundle(EwpBundle::class.java, BUNDLE)

    operator fun get(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        INSTANCE.getMessage(key, *params)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        INSTANCE.getMessage(key, *params)

    @Suppress("unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        INSTANCE.getLazyMessage(key, *params)
}
