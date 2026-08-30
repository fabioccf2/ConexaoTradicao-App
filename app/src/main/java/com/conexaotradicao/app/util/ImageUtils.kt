package com.conexaotradicao.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * RF10 — fotos da carneação finalizada são guardadas direto no documento do evento no
 * Firestore (sem Firebase Storage — decisão da Parte 3.16, pra não exigir upgrade pro plano
 * Blaze), então precisam ser bem pequenas: redimensiona pro lado maior caber em [maxDimension]
 * e comprime em JPEG com [quality] antes de virar Base64.
 */
object ImageUtils {

    private const val MAX_DIMENSION = 800
    private const val JPEG_QUALITY = 60

    /** Lê uma imagem escolhida na galeria (Uri) e devolve já redimensionada, comprimida e
     * codificada em Base64 — pronta pra entrar numa lista `List<String>` do `Event`. Devolve
     * `null` se não conseguir ler/decodificar (arquivo corrompido, sem permissão etc.). */
    fun uriToCompressedBase64(context: Context, uri: Uri): String? {
        val bitmap = decodeSampledBitmap(context, uri) ?: return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        bitmap.recycle()
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /** Decodifica o Base64 de volta pra Bitmap, pra exibir a foto num ImageView. */
    fun base64ToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        // Primeiro só lê as dimensões (inJustDecodeBounds), sem carregar a imagem inteira na
        // memória, pra calcular o fator de redução (inSampleSize) antes da decodificação real.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }

        var sampleSize = 1
        var width = boundsOptions.outWidth
        var height = boundsOptions.outHeight
        while (width / 2 >= MAX_DIMENSION || height / 2 >= MAX_DIMENSION) {
            sampleSize *= 2
            width /= 2
            height /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }.getOrNull()
}
