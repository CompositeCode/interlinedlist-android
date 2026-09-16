package com.interlinedlist.android.blog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlogRoutesTest {

    @Test
    fun `the Account hub opens the screen with no arguments`() {
        assertThat(BlogRoutes.subscription()).isEqualTo("blog/subscription")
    }

    @Test
    fun `a tapped confirmation link maps onto the in-app route`() {
        val route = BlogRoutes.routeForSubscriptionLink(
            "https://interlinedlist.com/api/blog/subscribe/confirm?token=abc123",
        )

        assertThat(route).isEqualTo("blog/subscription?action=CONFIRM&token=abc123")
    }

    @Test
    fun `a tapped unsubscribe link maps onto the in-app route`() {
        val route = BlogRoutes.routeForSubscriptionLink(
            "interlinedlist://blog-unsubscribe?token=abc123",
        )

        assertThat(route).isEqualTo("blog/subscription?action=UNSUBSCRIBE&token=abc123")
    }

    @Test
    fun `a token is percent-encoded so it cannot split the route apart`() {
        val route = BlogRoutes.subscription(BlogSubscriptionAction.CONFIRM, "a b&action=UNSUBSCRIBE")

        assertThat(route)
            .isEqualTo("blog/subscription?action=CONFIRM&token=a%20b%26action%3DUNSUBSCRIBE")
    }

    @Test
    fun `links the blog does not own are ignored`() {
        assertThat(BlogRoutes.routeForSubscriptionLink("https://interlinedlist.com/blog")).isNull()
        assertThat(BlogRoutes.routeForSubscriptionLink(null)).isNull()
    }
}
