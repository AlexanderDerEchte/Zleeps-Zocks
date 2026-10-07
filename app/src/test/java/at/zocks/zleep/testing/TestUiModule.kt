package at.zocks.zleep.testing

import at.zocks.zleep.di.UiModule
import at.zocks.zleep.ui.onboarding.OnboardingPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Schalter für UI-Tests: Standardmäßig ohne Einrichtung, der Einrichtungstest schaltet sie ein. */
object TestOnboarding {
    @Volatile
    var enabled: Boolean = false
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [UiModule::class])
object TestUiModule {
    @Provides
    fun onboardingPolicy(): OnboardingPolicy = OnboardingPolicy { TestOnboarding.enabled }
}
