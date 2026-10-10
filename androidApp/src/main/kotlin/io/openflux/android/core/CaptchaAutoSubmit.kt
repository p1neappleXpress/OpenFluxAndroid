package io.openflux.android.core

import kotlinx.coroutines.delay

/** Only local checks may settle automatically; remote prompts stay available for manual submission. */
internal suspend fun monitorCaptchaAutoSubmit(
    allowAutoSubmit: Boolean,
    isOpen: () -> Boolean,
    isSettled: () -> Boolean,
    nowMillis: () -> Long = System::currentTimeMillis,
    submit: () -> Unit,
) {
    if (!allowAutoSubmit) return
    var settledSince = 0L
    while (isOpen()) {
        val settled = isSettled()
        val now = nowMillis()
        if (!settled) settledSince = 0L
        else if (settledSince == 0L) settledSince = now
        else if (now - settledSince >= 1500L) {
            submit()
            return
        }
        delay(300)
    }
}
