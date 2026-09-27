package com.miguenduval.mimicdj2.di

import android.content.Context
import com.miguenduval.mimicdj2.network.NetworkDiagnostics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNetworkDiagnostics(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): NetworkDiagnostics {
        return NetworkDiagnostics(context)
    }
}