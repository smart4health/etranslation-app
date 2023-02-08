package de.hpi.etranslation.feature.dashboard

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {

    suspend fun write(localId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        pdfDir().div(localId.pdf).writeBytes(bytes)
    }

    suspend fun exists(localId: String): Boolean = withContext(Dispatchers.IO) {
        pdfDir().div(localId.pdf).exists()
    }

    fun getRenderer(localId: String): PdfRenderer? =
        pdfDir().div(localId.pdf)
            .orNull()
            ?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            ?.let(::PdfRenderer)

    suspend fun clear() = withContext(Dispatchers.IO) {
        pdfDir().deleteRecursively()
    }

    /**
     * Ensures the pdf for [localId] is deleted
     */
    suspend fun deleteByLocalId(localId: String) = withContext(Dispatchers.IO) {
        val localFile = pdfDir().div(localId.pdf)
        if (localFile.exists())
            localFile.delete()
    }

    private fun pdfDir(): File = File(context.filesDir, "pdfs").also(File::mkdirs)

    private operator fun File.div(name: String) = File(this, name)

    private val String.pdf: String
        get() = "$this.pdf"

    private fun File.orNull(): File? = if (exists()) this else null
}
