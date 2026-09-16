package com.interlinedlist.android.blog.di

import com.interlinedlist.android.blog.data.BlogApi
import com.interlinedlist.android.blog.data.BlogSubscriptionRepository
import com.interlinedlist.android.blog.data.DefaultBlogSubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Bindings for the blog's email list.
 *
 * [BlogApi] gets its own client because the confirm/unsubscribe endpoints answer `307`
 * and the redirect target's `subscription` parameter is the *entire* result: letting
 * OkHttp follow the redirect would fetch the blog's HTML and discard the answer. The
 * client is derived from the shared one with `newBuilder()`, so the interceptors,
 * timeouts and connection pool are all still the app's.
 */
@Module
@InstallIn(SingletonComponent::class)
object BlogNetworkModule {

    @Provides
    @Singleton
    fun provideBlogApi(retrofit: Retrofit, client: OkHttpClient): BlogApi = retrofit
        .newBuilder()
        .client(client.newBuilder().followRedirects(false).followSslRedirects(false).build())
        .build()
        .create(BlogApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BlogRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlogSubscriptionRepository(
        impl: DefaultBlogSubscriptionRepository,
    ): BlogSubscriptionRepository
}
