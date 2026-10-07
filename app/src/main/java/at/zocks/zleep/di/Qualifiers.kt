package at.zocks.zleep.di

import javax.inject.Qualifier

/** Scope, der so lange lebt wie die App (für Geräte, Simulator, Hintergrundarbeit). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Standard-Einstellungen, abhängig vom Build (Release: echte Socken, Debug: Simulator). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultSettings
