package com.dronegcs.app.di

import android.content.Context
import com.dronegcs.app.data.camera.CameraXPreviewRepository
import com.dronegcs.app.data.video.RtspVideoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    @Singleton
    fun provideCameraXPreviewRepository(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): CameraXPreviewRepository {
        return CameraXPreviewRepository(context, scope)
    }

    @Provides
    @Singleton
    fun provideRtspVideoRepository(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): RtspVideoRepository {
        return RtspVideoRepository(context, scope)
    }
}