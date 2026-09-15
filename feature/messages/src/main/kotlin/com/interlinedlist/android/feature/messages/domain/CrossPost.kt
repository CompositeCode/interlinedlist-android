package com.interlinedlist.android.feature.messages.domain

/**
 * A social network the current user has already linked on the web, returned by
 * `GET /api/user/identities`. These are the candidate cross-post destinations the
 * composer offers; linking new accounts (OAuth) happens on the web and is out of
 * scope for the app.
 *
 * [provider] is the raw wire value (e.g. `mastodon:techhub.social`, `linkedin`);
 * [networkProvider] normalises it to a known [NetworkProvider] for icon/label and,
 * critically, to decide how the selection is encoded on the create request (a
 * per-instance mastodon id vs. a single boolean flag for the others).
 */
data class LinkedNetwork(
    /** The identity's stable id (used as a `mastodonProviderIds` entry). */
    val id: String,
    /** Raw provider string from the API, e.g. `mastodon:techhub.social`. */
    val provider: String,
    /** The handle/display name on that network, for the chip label. */
    val providerUsername: String,
    val profileUrl: String? = null,
    val avatarUrl: String? = null,
    val connectedAt: String? = null,
) {
    /** The normalised provider family, derived from [provider]'s prefix. */
    val networkProvider: NetworkProvider get() = NetworkProvider.fromWire(provider)

    /**
     * A short label for the destination chip. Mastodon shows its instance host
     * (e.g. `techhub.social`) since a user may link more than one; the others
     * show the single network's display name.
     */
    val chipLabel: String
        get() = when (networkProvider) {
            NetworkProvider.MASTODON -> provider.substringAfter(':', missingDelimiterValue = "Mastodon")
            else -> networkProvider.displayName
        }
}

/** The cross-post networks the create endpoint understands. */
enum class NetworkProvider(val displayName: String) {
    MASTODON("Mastodon"),
    LINKEDIN("LinkedIn"),
    TWITTER("X"),
    BLUESKY("Bluesky"),
    /** Any linked provider the app doesn't yet know how to cross-post to. */
    OTHER("Other");

    companion object {
        /**
         * Maps a raw `provider` value to a [NetworkProvider]. Mastodon arrives as
         * `mastodon:<host>` (per-instance), so match on the prefix; the rest are
         * plain tokens.
         */
        fun fromWire(provider: String): NetworkProvider = when {
            provider.startsWith("mastodon", ignoreCase = true) -> MASTODON
            provider.equals("linkedin", ignoreCase = true) -> LINKEDIN
            provider.equals("twitter", ignoreCase = true) ||
                provider.equals("x", ignoreCase = true) -> TWITTER
            provider.equals("bluesky", ignoreCase = true) -> BLUESKY
            else -> OTHER
        }
    }
}

/**
 * The set of already-linked networks the composer has selected as cross-post
 * targets for a post. Built from the toggled [LinkedNetwork]s; the repository
 * translates it into the discrete create-request fields (a mastodon id list plus
 * the per-network boolean flags). An empty selection means InterlinedList-only.
 */
data class CrossPostSelection(
    /** Selected mastodon identity ids (a user may link several instances). */
    val mastodonProviderIds: List<String> = emptyList(),
    val bluesky: Boolean = false,
    val linkedIn: Boolean = false,
    val twitter: Boolean = false,
) {
    /** True when at least one external network is targeted. */
    val hasTargets: Boolean
        get() = mastodonProviderIds.isNotEmpty() || bluesky || linkedIn || twitter

    companion object {
        val NONE = CrossPostSelection()

        /** Folds a set of selected [networks] into a [CrossPostSelection]. */
        fun from(networks: Collection<LinkedNetwork>): CrossPostSelection = CrossPostSelection(
            mastodonProviderIds = networks
                .filter { it.networkProvider == NetworkProvider.MASTODON }
                .map { it.id },
            bluesky = networks.any { it.networkProvider == NetworkProvider.BLUESKY },
            linkedIn = networks.any { it.networkProvider == NetworkProvider.LINKEDIN },
            twitter = networks.any { it.networkProvider == NetworkProvider.TWITTER },
        )
    }
}

/**
 * Per-network delivery status for a cross-posted message, parsed from the create
 * response's `crossPosts` array. The response shape is not modelled in the OpenAPI
 * spec, so all fields are best-effort: [provider] identifies the target, [status]
 * is a free-text state (e.g. `success`, `pending`, `failed`), and [url]/[error]
 * are surfaced when present.
 */
data class CrossPostStatus(
    val provider: String,
    val status: String,
    val url: String? = null,
    val error: String? = null,
) {
    val networkProvider: NetworkProvider get() = NetworkProvider.fromWire(provider)

    /** A human label for the network the post was sent to. */
    val label: String
        get() = when (networkProvider) {
            NetworkProvider.MASTODON -> provider.substringAfter(':', missingDelimiterValue = "Mastodon")
            NetworkProvider.OTHER -> provider
            else -> networkProvider.displayName
        }

    val isSuccess: Boolean get() = status.equals("success", ignoreCase = true) || url != null && error == null
    val isFailed: Boolean get() = status.equals("failed", ignoreCase = true) || error != null
}

/**
 * The outcome of creating a message: the created [message] plus any per-network
 * [crossPosts] delivery statuses the create endpoint reported. [crossPosts] is
 * empty for a plain InterlinedList-only post.
 */
data class CreatedMessage(
    val message: Message,
    val crossPosts: List<CrossPostStatus> = emptyList(),
)
