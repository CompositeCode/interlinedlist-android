package com.interlinedlist.android.feature.directmessages.ui.newmessage

/**
 * Copy for the "who you can message" rule enforced by `GET /api/dm/recipients`.
 *
 * Wording is taken from the public help centre
 * (https://interlinedlist.com/help/direct-messages → *Who you can message*):
 * "You can only message people you follow each other: you follow them and they
 * follow you back (both follow requests approved)… Once you both follow each
 * other, they'll appear in your recipient list."
 *
 * Kept here — rather than inline in the composable — so the wording is asserted
 * by tests and never drifts from the rule the server actually enforces.
 */
object RecipientRuleCopy {
    /** Headline for the loaded-but-empty picker. Deliberately not an error. */
    const val TITLE = "No one to message yet"

    /** The rule itself, phrased so it holds whatever the reason the set is empty. */
    const val EXPLANATION =
        "You can only message people you follow each other with: you follow them, " +
            "and they follow you back. Once you both follow each other, they'll appear here."

    /** Route out of the dead end, into the existing people search. */
    const val FIND_PEOPLE = "Find people to follow"

    /** Shown when the picker has recipients but the query matches none of them. */
    const val NO_MATCHES = "No one matches that search."
}
