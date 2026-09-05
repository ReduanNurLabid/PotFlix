package com.potflix.di

import com.potflix.data.remote.PotFlixApi
import com.potflix.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(serverPreferences: com.potflix.data.local.preferences.ServerPreferences): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val originalHttpUrl = original.url
                if (originalHttpUrl.host.contains("themoviedb.org")) {
                    val key = serverPreferences.getEffectiveTmdbApiKey()
                    val requestBuilder = original.newBuilder()
                    if (key.startsWith("eyJ") || key.length > 50) {
                        requestBuilder.addHeader("Authorization", "Bearer $key")
                        chain.proceed(requestBuilder.build())
                    } else {
                        val url = originalHttpUrl.newBuilder()
                            .addQueryParameter("api_key", key)
                            .build()
                        chain.proceed(requestBuilder.url(url).build())
                    }
                } else {
                    chain.proceed(original)
                }
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/")
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePotFlixApi(retrofit: Retrofit): PotFlixApi {
        return retrofit.create(PotFlixApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(retrofit: Retrofit): com.potflix.data.remote.TmdbApi {
        return retrofit.create(com.potflix.data.remote.TmdbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAListScraper(okHttpClient: OkHttpClient): com.potflix.data.remote.AListScraper {
        return com.potflix.data.remote.AListScraper(okHttpClient, "https://cdn.nagordola.com.bd/")
    }
}
