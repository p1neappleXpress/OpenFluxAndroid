package io.openflux.android.web

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebPageSizingTest {
    @Test fun remoteDocumentGetsAContainingBlockForPercentageHeights() {
        val sizing = assertNotNull(webPageSizing(remote = true, setupPage = false))
        // Chromium uses WRAP_CONTENT to enable forceZeroLayoutHeight, even
        // when the native view has already received an exact measured size.
        assertNotEquals(WRAP_CONTENT, sizing.height)
        assertEquals(MATCH_PARENT, sizing.height)
        assertEquals(MATCH_PARENT, sizing.width)
    }

    @Test fun localBrowserRetainsItsExistingSizingPolicy() {
        assertNull(webPageSizing(remote = false, setupPage = false))
    }

    @Test fun scriptSetupPagesRetainTheirExistingSizingPolicy() {
        assertNull(webPageSizing(remote = false, setupPage = true))
        assertNull(webPageSizing(remote = true, setupPage = true))
    }
}
