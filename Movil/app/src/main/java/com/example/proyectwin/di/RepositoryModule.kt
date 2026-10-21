package com.example.proyectwin.di

import com.example.proyectwin.data.repository.AppUpdatesRepositoryImpl
import com.example.proyectwin.data.repository.AuditRepositoryImpl
import com.example.proyectwin.data.repository.AuthRepositoryImpl
import com.example.proyectwin.data.repository.BugReportsRepositoryImpl
import com.example.proyectwin.data.repository.CatalogsRepositoryImpl
import com.example.proyectwin.data.repository.CommentsRepositoryImpl
import com.example.proyectwin.data.repository.DashboardMetricsRepositoryImpl
import com.example.proyectwin.data.repository.FichasRepositoryImpl
import com.example.proyectwin.data.repository.MembersRepositoryImpl
import com.example.proyectwin.data.repository.MotorRepositoryImpl
import com.example.proyectwin.data.repository.NotificationsRepositoryImpl
import com.example.proyectwin.data.repository.ProjectsRepositoryImpl
import com.example.proyectwin.data.repository.SimilaritiesRepositoryImpl
import com.example.proyectwin.data.repository.UsersRepositoryImpl
import com.example.proyectwin.domain.repository.AppUpdatesRepository
import com.example.proyectwin.domain.repository.AuditRepository
import com.example.proyectwin.domain.repository.AuthRepository
import com.example.proyectwin.domain.repository.BugReportsRepository
import com.example.proyectwin.domain.repository.CatalogsRepository
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.DashboardMetricsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.MotorRepository
import com.example.proyectwin.domain.repository.NotificationsRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Une cada interfaz de repositorio con su implementación sobre la API. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindProjectsRepository(impl: ProjectsRepositoryImpl): ProjectsRepository

    @Binds
    abstract fun bindFichasRepository(impl: FichasRepositoryImpl): FichasRepository

    @Binds
    abstract fun bindUsersRepository(impl: UsersRepositoryImpl): UsersRepository

    @Binds
    abstract fun bindNotificationsRepository(impl: NotificationsRepositoryImpl): NotificationsRepository

    @Binds
    abstract fun bindBugReportsRepository(impl: BugReportsRepositoryImpl): BugReportsRepository

    @Binds
    abstract fun bindSimilaritiesRepository(impl: SimilaritiesRepositoryImpl): SimilaritiesRepository

    @Binds
    abstract fun bindCommentsRepository(impl: CommentsRepositoryImpl): CommentsRepository

    @Binds
    abstract fun bindCatalogsRepository(impl: CatalogsRepositoryImpl): CatalogsRepository

    @Binds
    abstract fun bindMotorRepository(impl: MotorRepositoryImpl): MotorRepository

    @Binds
    abstract fun bindDashboardMetricsRepository(
        impl: DashboardMetricsRepositoryImpl,
    ): DashboardMetricsRepository

    @Binds
    abstract fun bindAppUpdatesRepository(impl: AppUpdatesRepositoryImpl): AppUpdatesRepository

    @Binds
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds
    abstract fun bindMembersRepository(impl: MembersRepositoryImpl): MembersRepository
}
