package at.zocks.zleep.data.export

import android.content.Context
import androidx.core.net.toUri
import at.zocks.zleep.di.IoDispatcher
import at.zocks.zleep.domain.export.ExportSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Schreibt einen Export als UTF-8 in eine über die Dateiauswahl des Systems gewählte Datei. */
class DocumentExportSink @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ExportSink {

    override suspend fun <T> write(target: String, block: suspend (Appendable) -> T): T = withContext(io) {
        val stream = context.contentResolver.openOutputStream(target.toUri(), "wt") ?: throw IOException("Datei nicht beschreibbar")
        stream.bufferedWriter(Charsets.UTF_8).use { writer -> block(writer) }
    }
}
