package com.telezen.app.core

import android.content.Context

/** The offline build has no network code. The sync build has its own LaptopSync with the same shape. */
object LaptopSync {
    const val available = false

    @Suppress("UNUSED_PARAMETER")
    fun schedule(ctx: Context) {}
}
