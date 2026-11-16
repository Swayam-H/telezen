package com.telezen.app

import com.telezen.app.core.LaptopSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LaptopSyncTest {
    @Test
    fun parsesTheCodeTheHelperPrints() {
        val p = LaptopSync.parse("  192.168.1.5:4747/AbC_d-EfGhIjKlMnOpQrStUv\n")
        assertEquals("192.168.1.5:4747", p?.hostPort)
        assertEquals("AbC_d-EfGhIjKlMnOpQrStUv", p?.token)
        assertNotNull(LaptopSync.parse("my-laptop.local:4747/AbC_d-EfGhIjKlMnOpQrStUv"))
    }

    @Test
    fun rejectsAnythingElse() {
        val bad = listOf(
            "",
            "192.168.1.5/AbC_d-EfGhIjKlMnOpQrStUv", // no port
            "192.168.1.5:4747", // no token
            "192.168.1.5:4747/short", // token too short
            "http://192.168.1.5:4747/AbC_d-EfGhIjKlMnOpQrStUv", // scheme isn't part of the code
            "192.168.1.5:4747/AbC_d-EfGhIjKlMnOpQr StUv", // space in the token
            "evil.com/x:4747/AbC_d-EfGhIjKlMnOpQrStUv", // path before the port
        )
        for (code in bad) assertNull(code, LaptopSync.parse(code))
    }
}
