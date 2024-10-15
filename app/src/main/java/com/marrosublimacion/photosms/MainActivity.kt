package com.marrosublimacion.photosms

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.marrosublimacion.photosms.utils.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class MainActivity : ComponentActivity() {

    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        dataStoreManager = DataStoreManager(this)

        enableEdgeToEdge()
        setContent {
            var hasCameraPermission by remember { mutableStateOf(false) }
            var savedUri by remember { mutableStateOf<Uri?>(null) }
            var storedPhotoNumber by remember { mutableStateOf(0) }
            var selectedOption by remember { mutableStateOf(Option.Option1) }
            val context = LocalContext.current

            val scope = rememberCoroutineScope()

            RequestPermission(
                permission = android.Manifest.permission.CAMERA,
                rationale = "The app needs access to the camera to take photos.",
                onPermissionResult = { granted ->
                    hasCameraPermission = granted
                }
            )

            LaunchedEffect(Unit) {
                dataStoreManager.photoNumber.collect { value ->
                    storedPhotoNumber = value
                }
            }

            if (hasCameraPermission && savedUri == null) {

                Column(modifier = Modifier.padding(top = 48.dp)) {

                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .height(40.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            ToggleOption(
                                text = "Medalla",
                                isSelected = selectedOption == Option.Option1,
                                onClick = { selectedOption = Option.Option1 }
                            )
                            ToggleOption(
                                text = "Banda",
                                isSelected = selectedOption == Option.Option2,
                                onClick = { selectedOption = Option.Option2 }
                            )
                        }


                    CameraView(selectedOption) { originalBitmap ->


                        //val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        //val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        //inputStream?.close()

                        originalBitmap.let{bitmap ->
                            val croppedBitmap = if (selectedOption == Option.Option1) {
                                cropCircleImage(bitmap)
                            } else {
                                cropRectangleImage(bitmap, 500, 800)
                                //cropBitmapWithBitmapSize(originalBitmap, 0.5f, 0.62f)
                            }

                            savedUri = saveImageToGallery(context, croppedBitmap, storedPhotoNumber, dataStoreManager, scope)
                            if (savedUri == null) {
                                Toast.makeText(context, "No se pudo guardar la imagen", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            if (savedUri != null) {
                ShowDataPhoto(
                    savedUri!!,
                    storedPhotoNumber
                ) {
                    savedUri = null
                }
            }
        }
    }
}


@Composable
fun ShowDataPhoto(
    photoUri: Uri,
    numberPhoto: Int,
    nextPhoto: ()->Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Image(
                painter = rememberAsyncImagePainter(photoUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "CÓDIGO: $numberPhoto",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(Color.White)
            )

            Button(
                onClick = { nextPhoto() },
            ) {
                Text(text = "GUARDAR")
            }
        }
    }
}

@Composable
fun CameraView(optionImage: Option, onImageCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraProvider = cameraProviderFuture.get()
    val coroutineScope = rememberCoroutineScope()

    val preview = Preview.Builder().build()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 30.dp,
                    bottom = 50.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 30.dp,
                        bottom = 50.dp
                    )
                    .aspectRatio(1f)  // Maintain a square aspect ratio for the image
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        cameraProviderFuture.addListener({
                            cameraProvider.unbindAll()
                            imageCapture = ImageCapture.Builder().build()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier
                        .fillMaxSize()

                )

                if (optionImage == Option.Option1) {
                    Box(
                        modifier = Modifier
                            .size(380.dp)  // Define the size of the inner Box
                            .clip(CircleShape)  // Clip to Circle shape
                            .background(Color.Transparent)  // Transparent background
                            .border(2.dp, Color.Red, CircleShape)  // Red border with Circle shape
                            .align(Alignment.Center)  // Center the inner Box
                            .offset(y = (-80).dp)  // Move the Box upwards
                            .padding(8.dp),  // Padding inside the circle
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(200.dp, 300.dp)
                            .border(2.dp, Color.Red)
                            .background(Color.Transparent)
                    )

                }
            }

        }

        Button(
            onClick = {
                val photoFile = File(
                    context.cacheDir,
                    "${System.currentTimeMillis()}.jpg"
                )

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture?.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = Uri.fromFile(photoFile)
                            onImageCaptured(adjustBitmapOrientation(BitmapFactory.decodeFile(photoFile.absolutePath), photoFile.absolutePath))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(
                                "CameraView",
                                "Photo capture failed: ${exception.message}",
                                exception
                            )
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text("TOMAR FOTO")
        }
    }
}



suspend fun superimposeImage(context: Context, photoUri: Uri) {
    withContext(Dispatchers.IO) {
        val photoBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(photoUri))
        val overlayBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.marro_logo)

        val finalBitmap = Bitmap.createBitmap(photoBitmap.width, photoBitmap.height, photoBitmap.config)
        val canvas = Canvas(finalBitmap)
        canvas.drawBitmap(photoBitmap, 0f, 0f, null)
        canvas.drawBitmap(overlayBitmap, 0f, 0f, Paint())

        val file = File(photoUri.path!!)
        val outputStream = FileOutputStream(file)
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermission(
    permission: String,
    rationale: String,
    onPermissionResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(permission)

    LaunchedEffect(permissionState) {
        permissionState.launchPermissionRequest()
    }

    when (permissionState.status) {
        is PermissionStatus.Granted -> onPermissionResult(true)
        is PermissionStatus.Denied -> {
            if ((permissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Permission required") },
                    text = { Text(rationale) },
                    confirmButton = {
                        Button(onClick = { permissionState.launchPermissionRequest() }) {
                            Text("OK")
                        }
                    }
                )
            } else {
                onPermissionResult(false)
            }
        }
    }
}

fun cropRectangleImage(originalBitmap: Bitmap, cropWidth: Int, cropHeight: Int): Bitmap {

    val startX = (originalBitmap.width - cropWidth) / 2
    val startY = (originalBitmap.height - cropHeight) / 2

    // Asegúrate de que las coordenadas no sean negativas
    val x = startX.coerceAtLeast(0)
    val y = startY.coerceAtLeast(0)

    // Asegúrate de que el ancho y la altura no sean mayores que las dimensiones originales
    val cropWidth = cropWidth.coerceAtMost(originalBitmap.width - x)
    val cropHeight = cropHeight.coerceAtMost(originalBitmap.height - y)

    // Crear el bitmap recortado
    return Bitmap.createBitmap(originalBitmap, x, y, cropWidth, cropHeight)
}

fun cropCircleImage(bitmap: Bitmap): Bitmap {
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

    return outputBitmap
}

fun saveImageToGallery(
    context: Context,
    bitmap: Bitmap,
    number: Int,
    dataStoreManager: DataStoreManager,
    scope: CoroutineScope
): Uri? {

    val newNumber = number + 1
    scope.launch {
        dataStoreManager.setPhotoNumber(newNumber)
    }

    // Preparar los valores para insertar en la galería
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "MS_${System.currentTimeMillis()}_$newNumber.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MarroSubliFotos")
    }

    // Insertar la imagen en la galería
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
        outputStream?.use {
            // Guardar el Bitmap en la galería sin compresión con pérdida
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    return uri // Retornar la URI de la imagen guardada o null en caso de error
}

// ----------------------------


@Composable
fun ToggleOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color.Blue else Color.LightGray
    val textColor = if (isSelected) Color.White else Color.Black

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

enum class Option {
    Option1,
    Option2
}


fun adjustBitmapOrientation(originalBitmap: Bitmap, filePath: String): Bitmap {
    val exif = ExifInterface(filePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(originalBitmap, horizontal = true, vertical = false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(originalBitmap, horizontal = false, vertical = true)
        else -> originalBitmap
    }
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
    val matrix = Matrix().apply {
        preScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}


//------

fun cropBitmapWithBitmapSize(originalBitmap: Bitmap, widthFactor: Float, heightFactor: Float): Bitmap {
    // Calcula el tamaño del recorte en función del tamaño del bitmap original
    val cropWidth = (originalBitmap.width * widthFactor).toInt()
    val cropHeight = (originalBitmap.height * heightFactor).toInt()

    // Ajusta las coordenadas del recorte
    val startX = (originalBitmap.width - cropWidth) / 2
    val startY = (originalBitmap.height - cropHeight) / 2

    // Crear el bitmap recortado
    return Bitmap.createBitmap(originalBitmap, startX, startY, cropWidth, cropHeight)
}