package at.zocks.zleep.di

import at.zocks.zleep.ui.onboarding.OnboardingPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UiModule {
    @Provides
    fun onboardingPolicy(): OnboardingPolicy = OnboardingPolicy { true }
}
