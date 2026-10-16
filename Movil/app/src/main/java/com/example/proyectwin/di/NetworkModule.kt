package com.example.proyectwin.di

import com.example.proyectwin.BuildConfig
import com.example.proyectwin.data.api.AuthInterceptor
import com.example.proyectwin.data.api.service.ApprenticesApi
import com.example.proyectwin.data.api.service.AppVersionApi
import com.example.proyectwin.data.api.service.AuditApi
import com.example.proyectwin.data.api.service.AuthApi
import com.example.proyectwin.data.api.service.BugReportsApi
import com.example.proyectwin.data.api.service.CatalogsApi
import com.example.proyectwin.data.api.service.ClassGroupsApi
import com.example.proyectwin.data.api.service.CommentsApi
import com.example.proyectwin.data.api.service.InstructorsApi
import com.example.proyectwin.data.api.service.MotorApi
import com.example.proyectwin.data.api.service.NotificationsApi
import com.example.proyectwin.data.api.service.ProjectsApi
import com.example.proyectwin.data.api.service.SimilaritiesApi
import com.example.proyectwin.data.api.service.UsersApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Configuración de red: Retrofit + OkHttp con interceptor de autenticación
 * (Bearer Sanctum) y logging sólo en builds de depuración.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BASIC: método/URL/estado, sin cuerpos ni cabeceras; evita
                    // que la contraseña del login o el token queden en Logcat.
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides
    @Singleton
    fun provideProjectsApi(retrofit: Retrofit): ProjectsApi = retrofit.create(ProjectsApi::class.java)

    @Provides
    @Singleton
    fun provideClassGroupsApi(retrofit: Retrofit): ClassGroupsApi = retrofit.create(ClassGroupsApi::class.java)

    @Provides
    @Singleton
    fun provideInstructorsApi(retrofit: Retrofit): InstructorsApi = retrofit.create(InstructorsApi::class.java)

    @Provides
    @Singleton
    fun provideApprenticesApi(retrofit: Retrofit): ApprenticesApi = retrofit.create(ApprenticesApi::class.java)

    @Provides
    @Singleton
    fun provideAppVersionApi(retrofit: Retrofit): AppVersionApi = retrofit.create(AppVersionApi::class.java)

    @Provides
    @Singleton
    fun provideSimilaritiesApi(retrofit: Retrofit): SimilaritiesApi = retrofit.create(SimilaritiesApi::class.java)

    @Provides
    @Singleton
    fun provideMotorApi(retrofit: Retrofit): MotorApi = retrofit.create(MotorApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideBugReportsApi(retrofit: Retrofit): BugReportsApi = retrofit.create(BugReportsApi::class.java)

    @Provides
    @Singleton
    fun provideCommentsApi(retrofit: Retrofit): CommentsApi = retrofit.create(CommentsApi::class.java)

    @Provides
    @Singleton
    fun provideCatalogsApi(retrofit: Retrofit): CatalogsApi = retrofit.create(CatalogsApi::class.java)

    @Provides
    @Singleton
    fun provideAuditApi(retrofit: Retrofit): AuditApi = retrofit.create(AuditApi::class.java)
}
