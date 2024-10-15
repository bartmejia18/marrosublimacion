package com.marrosublimacion.photosms.ui

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import com.marrosublimacion.photosms.BuildConfig
import com.marrosublimacion.photosms.R
import com.marrosublimacion.photosms.utils.DataStoreManager
import com.marrosublimacion.photosms.utils.createImageFile
import com.marrosublimacion.photosms.utils.createImageFileUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Objects

@Composable
fun CameraWithOverlay() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val preview = Preview.Builder().build()
    val imageCaptureBuilder = ImageCapture.Builder().build()

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCaptureBuilder
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    preview.setSurfaceProvider(surfaceProvider)
                }
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Rectangle overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp, 100.dp)
                    .background(Color.Transparent)
                    .border(2.dp, Color.Red)
            )
        }

        Button(
            onClick = {
                val photoFile = File(
                    context.cacheDir,
                    "${System.currentTimeMillis()}.jpg"
                )

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCaptureBuilder.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: photoFile.toUri()
                            capturedImageUri = savedUri
                            coroutineScope.launch {
                                val superImage = superimposeImage(context, savedUri)
                                saveImageToGallery(context, superImage)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // Handle the error
                        }
                    }
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Text("Take Photo")
        }

        capturedImageUri?.let { uri ->
            val bitmap = BitmapFactory.decodeFile(uri.path)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}

suspend fun superimposeImage(context: Context, photoUri: Uri): Uri {
    return withContext(Dispatchers.IO) {
        val photoBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(photoUri))
        val overlayBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.buho)

        val finalBitmap = Bitmap.createBitmap(photoBitmap.width, photoBitmap.height, photoBitmap.config)
        val canvas = Canvas(finalBitmap)
        canvas.drawBitmap(photoBitmap, 0f, 0f, null)
        canvas.drawBitmap(overlayBitmap, 0f, 0f, Paint())

        val file = File(photoUri.path!!)
        val outputStream = FileOutputStream(file)
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        file.toUri()
    }
}


fun saveImageToGallery(context: Context, photoUri: Uri) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    imageUri?.let {
        val outputStream: OutputStream? = resolver.openOutputStream(it)
        val inputStream = context.contentResolver.openInputStream(photoUri)
        inputStream?.use { input ->
            outputStream?.use { output ->
                input.copyTo(output)
            }
        }
    }
}


/*
Codigo completo de cortar y guardar
 */

fun cropCircleAndSaveImageAndReturnUri(context: Context, imageUri: Uri): Uri? {
    try {
        // Obtener el InputStream desde la URI
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)

        // Cargar el Bitmap desde el InputStream
        val originalBitmap = BitmapFactory.decodeStream(inputStream)

        inputStream?.close()

        // Asegurarse de que el Bitmap se haya cargado correctamente
        originalBitmap?.let { bitmap ->
            // Crear un Bitmap cuadrado centrado, para asegurar que el recorte circular sea uniforme
            val size = minOf(bitmap.width, bitmap.height)
            val startX = (bitmap.width - size) / 2
            val startY = (bitmap.height - size) / 2
            val squareBitmap = Bitmap.createBitmap(bitmap, startX, startY, size, size)

            // Crear un nuevo Bitmap con un círculo recortado
            val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Definir el área circular a recortar
            val rect = android.graphics.Rect(0, 0, size, size)
            val rectF = RectF(rect)

            // Dibujar un círculo en el canvas
            canvas.drawARGB(0, 0, 0, 0) // Fondo transparente
            canvas.drawOval(rectF, paint)

            // Configurar el modo de transferencia para aplicar el recorte circular
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            // Dibujar la imagen original dentro del círculo
            canvas.drawBitmap(squareBitmap, null, rect, paint)

            // Preparar los valores para insertar en la galería
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "imagen_recortada_circulo_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            // Insertar la imagen recortada en la galería
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
                outputStream?.use {
                    // Guardar el Bitmap recortado en la galería sin compresión con pérdida
                    outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                return uri // Retornar la URI de la imagen guardada
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null // En caso de error, retornar null
}
