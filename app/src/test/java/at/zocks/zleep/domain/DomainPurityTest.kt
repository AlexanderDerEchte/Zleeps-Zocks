package at.zocks.zleep.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** Die Domain-Schicht bleibt reines Kotlin: keine Android-, UI- oder Datenbank-Abhängigkeiten. */
class DomainPurityTest {

    private val forbidden = listOf("import android.", "import androidx.", "import at.zocks.zleep.ui.", "import at.zocks.zleep.data.", "import at.zocks.zleep.device.")

    @Test
    fun `domain layer has no framework or outer layer imports`() {
        val domainDir = File("src/main/java/at/zocks/zleep/domain")
        assertThat(domainDir.isDirectory).isTrue()

        val violations = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbidden.any { line.trim().startsWith(it) } }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        assertThat(violations).isEmpty()
    }
}
