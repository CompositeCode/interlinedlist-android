package com.interlinedlist.android.feature.integrations.data.mapper

import com.interlinedlist.android.feature.integrations.data.remote.dto.ConnectionStatusDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.LinkedIdentityDto
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount

/**
 * Rows to show for one provider.
 *
 * Two sources are combined. `/api/auth/<provider>/status` answers "is this connected"
 * for every provider the screen lists, including the ones with nothing linked;
 * `/api/user/identities` adds the identity key that unlink/verify need plus the
 * `connectedAt`/`lastVerifiedAt` that drive connection health.
 *
 * A provider can own more than one identity — Mastodon returns one per instance — so
 * each identity becomes its own actionable row, and a provider with none falls back
 * to a single status-only row.
 */
internal fun ConnectedAccount.Provider.toAccounts(
    status: ConnectionStatusDto?,
    identities: List<LinkedIdentityDto>,
): List<ConnectedAccount> {
    val mine = identities.filter { ConnectedAccount.Provider.fromIdentityProvider(it.provider) == this }
    if (mine.isEmpty()) {
        return listOf(
            ConnectedAccount(
                provider = this,
                isConnected = status?.isConnected ?: false,
                handle = status?.bestHandle,
            ),
        )
    }
    return mine.map { identity ->
        ConnectedAccount(
            // An identity record exists, so the account is linked whatever the status
            // endpoint says — a lapsed authorization is reported as health, not absence.
            provider = this,
            isConnected = true,
            handle = identity.providerUsername?.takeIf { it.isNotBlank() } ?: status?.bestHandle,
            identityProvider = identity.provider.takeIf { it.isNotBlank() },
            connectedAt = identity.connectedAt,
            lastVerifiedAt = identity.lastVerifiedAt,
        )
    }
}
