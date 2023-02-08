package de.hpi.etranslation.feature.viewer.usecase

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.util.Base64
import care.data4life.fhir.r4.FhirR4Parser
import care.data4life.fhir.r4.model.DocumentReference
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import de.hpi.etranslation.feature.dashboard.PdfRepository
import de.hpi.etranslation.feature.dashboard.ResourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.github.michaelbull.result.runCatching as catch

@Singleton
class RenderPdfUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val pdfRepository: PdfRepository,
    private val fhirR4Parser: FhirR4Parser,
) {
    suspend operator fun invoke(localId: String, targetWidth: Int): Result<Bitmap, Throwable> =
        withContext(Dispatchers.IO) {
            binding {
                if (!pdfRepository.exists(localId)) {
                    resourceRepository.getByLocalId(localId)
                        .mapError { err ->
                            when (err) {
                                ResourceRepository.Error.NotFound -> Error("local id $localId cannot be found in CHDP")
                                is ResourceRepository.Error.Other -> err.t
                            }
                        }
                        .bind()
                        .let { catch { fhirR4Parser.toFhir(DocumentReference::class.java, it) } }
                        .bind()
                        .catch {
                            content
                                .last()
                                .attachment
                                .data
                                .let { Base64.decode(it, Base64.DEFAULT) }
                        }
                        .bind()
                        .let { pdfRepository.write(localId, it) }
                }

                val pdfRenderer = pdfRepository.getRenderer(localId)
                    .toResultOr { Error("$localId.pdf not found") }
                    .bind()

                val page = pdfRenderer.openPage(0)
                val scaleRatio = targetWidth.toFloat().div(page.width)

                val targetHeight = page.height.times(scaleRatio).toInt()
                val bitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888,
                )

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfRenderer.close()

                bitmap
            }
        }
}
