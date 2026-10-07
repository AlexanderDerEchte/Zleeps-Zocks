package at.zocks.zleep.testing

import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Einstellungen im Speicher. */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (UserSettings) -> UserSettings) = settings.update(transform)
}
