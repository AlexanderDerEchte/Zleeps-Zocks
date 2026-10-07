package at.zocks.zleep.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.data.export.DocumentExportSink
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentExportSinkTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `writes utf-8 text into the chosen file`() = runTest {
        val file = folder.newFile("export.csv")
        val sink = DocumentExportSink(ApplicationProvider.getApplicationContext(), StandardTestDispatcher(testScheduler))

        val result = sink.write(Uri.fromFile(file).toString()) { out ->
            out.append("night_date,note\r\n2026-10-05,müde\r\n")
            2
        }

        assertThat(result).isEqualTo(2)
        assertThat(file.readText(Charsets.UTF_8)).isEqualTo("night_date,note\r\n2026-10-05,müde\r\n")
    }
}
