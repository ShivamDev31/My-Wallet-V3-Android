package piuk.blockchain.android.ui.send.strategy

import com.blockchain.logging.CustomEventBuilder

internal fun CustomEventBuilder.buildToMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    build { key, value -> map[key] = value }
    return map
}
