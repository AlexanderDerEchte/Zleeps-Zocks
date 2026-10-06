package at.zocks.zleep.domain.model

/** Eine Socke eines Paares. Bedienung wirkt standardmäßig auf [BOTH]. */
enum class SockSide {
    LEFT,
    RIGHT,
    BOTH,
    ;

    /** Die einzelnen Socken, die dieses Ziel umfasst. */
    val feet: List<SockSide>
        get() = when (this) {
            LEFT -> listOf(LEFT)
            RIGHT -> listOf(RIGHT)
            BOTH -> listOf(LEFT, RIGHT)
        }
}
