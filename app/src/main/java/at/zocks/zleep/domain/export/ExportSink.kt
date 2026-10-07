package at.zocks.zleep.domain.export

/** Ziel eines Exports, z. B. eine vom Nutzer gewählte Datei ([target] ist ihre Adresse). */
interface ExportSink {
    suspend fun <T> write(target: String, block: suspend (Appendable) -> T): T
}
