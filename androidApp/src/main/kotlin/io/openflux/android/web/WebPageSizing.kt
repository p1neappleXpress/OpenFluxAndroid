package io.openflux.android.web

import android.view.ViewGroup.LayoutParams.MATCH_PARENT

internal data class WebPageSizing(val width: Int, val height: Int)

/** A remote check occupies the fixed browser area supplied by its dialog. */
internal fun webPageSizing(remote: Boolean, setupPage: Boolean): WebPageSizing? =
    if (remote && !setupPage) WebPageSizing(MATCH_PARENT, MATCH_PARENT) else null
