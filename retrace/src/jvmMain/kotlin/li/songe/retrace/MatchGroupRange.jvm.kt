package li.songe.retrace

import kotlin.text.MatchGroup

internal actual val MatchGroup.matchRange: IntRange
    get() = range
