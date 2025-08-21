package com.marrosublimacion.photosms

import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.viewmodel.compose.viewModel


class MainActivity : ComponentActivity() {

    private lateinit var dataStoreManager: DataStoreManager

    private val cameraVM: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        dataStoreManager = DataStoreManager(this)

        enableEdgeToEdge()
        setContent {
            var hasCameraPermission by remember { mutableStateOf(false) }
            var savedUri by remember { mutableStateOf<Uri?>(null) }
            var storedPhotoNumber by remember { mutableIntStateOf(0) }
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


                    CameraViewFramedRect (
                        viewModel = cameraVM
                    ) { orientedBitmap, pa ->

                        // 1) Obtener el recorte tal como se ve en el preview
                        val recorte = cropToFrameRectAsPreview(
                            context = context,
                            photo = orientedBitmap,                 // viene del callback de la cámara
                            frameRes = R.drawable.marco_final,
                            rectPerc = RECT_MARCO
                        )

                        // 2) Compón dentro del PNG del marco con FONDO TRANSPARENTE
                        val compuestoTransp = composeCroppedIntoFrame(
                            context = context,
                            cropped = recorte,
                            frameRes = R.drawable.marco_final,
                            rectPerc = RECT_MARCO,
                            backgroundColor = null
                        )

                        // 3) Recorta orillas por transparencia (ajusta alphaThreshold/padPx si quieres)
                        val compacto = trimTransparentBorders(
                            src = compuestoTransp,
                            alphaThreshold = 10,   // 5–15 normalmente bien
                            padPx = 0              // deja 2–4 si quieres un mini margen
                        )

                        // 4) (Opcional) Fondo blanco final
                        val finalBitmap = overBackground(compacto, android.graphics.Color.WHITE)
                        val finalWithCode = drawCodeTopRightText(finalBitmap, storedPhotoNumber + 1)

                        // 3) Guardar
                        savedUri = saveImageToGallery(
                            context,
                            finalWithCode,
                            storedPhotoNumber,
                            dataStoreManager,
                            scope
                        )
                        if (savedUri == null) {
                            Toast.makeText(
                                context,
                                "No se pudo guardar la imagen",
                                Toast.LENGTH_SHORT
                            ).show()
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
    nextPhoto: () -> Unit
) {

    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        coil.request.ImageRequest.Builder(context)
            .data(photoUri)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
            .build()
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Image(
                painter = painter,
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

data class RectPerc(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Composable
fun CameraViewFramedRect(
    @DrawableRes frameRes: Int = R.drawable.marco_final,
    viewModel: CameraViewModel = viewModel(),// tu PNG
    onImageCaptured: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val flashMode = viewModel.flashMode

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val preview = remember { Preview.Builder().build() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Relación de aspecto del PNG para no deformarlo
    val frameBitmap = ImageBitmap.imageResource(context.resources, frameRes)
    val frameAspect = frameBitmap.width.toFloat() / frameBitmap.height

    // Rectángulo medido en tu marco (ajústalo si cambias de PNG)
    val rectPerc = remember { RECT_MARCO }

    // Shape que recorta SOLO el rectángulo interior
    val maskShape = remember(rectPerc) {
        GenericShape { size, _ ->
            val l = size.width * rectPerc.left
            val t = size.height * rectPerc.top
            val r = size.width * rectPerc.right
            val b = size.height * rectPerc.bottom
            addRect(Rect(l, t, r, b))
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashMode) {
        // Asegura que la linterna esté apagada cuando uses FLASH_MODE_*
        camera?.cameraControl?.enableTorch(false)
        imageCapture?.flashMode = flashMode
    }


    Box(Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .padding(5.dp)
                .height(40.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {
                val next = when (flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON   -> ImageCapture.FLASH_MODE_OFF
                    else                         -> ImageCapture.FLASH_MODE_AUTO
                }
                viewModel.setFlash(next)
            }) { Text("Flash: " + when(flashMode){
                ImageCapture.FLASH_MODE_ON -> "ON"
                ImageCapture.FLASH_MODE_OFF -> "OFF"
                else -> "AUTO"
            }) }
        }

        // Contenedor con el mismo aspect ratio que el PNG
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp, bottom = 50.dp)
                .aspectRatio(frameAspect)
                .align(Alignment.TopCenter)
        ) {
            // 1) Cámara — recortada al rectángulo
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // IMPORTANTE: esto hace el clip al rectángulo
                        clip = true
                        shape = maskShape
                    },
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        // Usa TextureView para que el clip funcione en todos lados
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        // Llenar manteniendo centro (como center-crop)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        provider.unbindAll()

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(flashMode) // modo inicial
                            .build()

                        // bind y guarda la referencia
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )

                        preview.setSurfaceProvider(previewView.surfaceProvider)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // 2) Marco por encima (no se deforma)
            Image(
                painter = painterResource(frameRes),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(1f)
            )
        }

        // Disparador
        Button(
            onClick = {
                val file = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture?.takePicture(
                    opts,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            val oriented = adjustBitmapOrientation(bmp, file.absolutePath)
                            onImageCaptured(oriented, file.absolutePath)
                        }

                        override fun onError(ex: ImageCaptureException) {
                            Log.e("CameraView", "Photo capture failed: ${ex.message}", ex)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) { Text("TOMAR FOTO") }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermission(
    permission: String,
    rationale: String,
    onPermissionResult: (Boolean) -> Unit
) {
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
    val uri =
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
        outputStream?.use { outputStream ->
            // Guardar el Bitmap en la galería sin compresión con pérdida
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
    }

    return uri // Retornar la URI de la imagen guardada o null en caso de error
}


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
    val orientation =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(
            originalBitmap,
            horizontal = true,
            vertical = false
        )

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(
            originalBitmap,
            horizontal = false,
            vertical = true
        )

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


private fun centerCropDestOnCanvas(
    canvasW: Int,
    canvasH: Int,
    src: Bitmap,
    overscan: Float = 1.0f
): RectF {
    var scale = maxOf(canvasW / src.width.toFloat(), canvasH / src.height.toFloat())
    scale *= overscan // por si quieres un micro “zoom” adicional (ej. 1.01f)
    val drawW = src.width * scale
    val drawH = src.height * scale
    val dx = (canvasW - drawW) / 2f
    val dy = (canvasH - drawH) / 2f
    return RectF(dx, dy, dx + drawW, dy + drawH)
}

/**
 * Recorta la foto EXACTAMENTE al rectángulo del marco tal como se ve en el preview.
 * Devuelve un bitmap del tamaño del rectángulo interno.
 */
fun cropToFrameRectAsPreview(
    context: Context,
    photo: Bitmap,
    @DrawableRes frameRes: Int = R.drawable.marco_final,
    rectPerc: RectPerc = RECT_MARCO,
    overscan: Float = 1.0f
): Bitmap {
    val frame = BitmapFactory.decodeResource(context.resources, frameRes)
    val width = frame.width
    val height = frame.height

    // Dónde se dibuja la foto para llenar TODO el marco (igual que el Preview FILL_CENTER)
    val destOnFrame = centerCropDestOnCanvas(width, height, photo, overscan)

    // Rectángulo interno del marco (en píxeles)
    val rectOnFrame =
        RectF(width * rectPerc.left, height * rectPerc.top, width * rectPerc.right, height* rectPerc.bottom)

    // Salida: exactamente el tamaño del rectángulo
    val outW = rectOnFrame.width().toInt()
    val outH = rectOnFrame.height().toInt()
    val output = createBitmap(outW, outH)
    val c = Canvas(output)

    // Para que el recorte coincida con lo visto, dibujamos con el mismo
    // escalado y desplazamiento del preview, pero re-referenciado al origen del rectángulo.
    val destOnOutput = RectF(
        destOnFrame.left - rectOnFrame.left,
        destOnFrame.top - rectOnFrame.top,
        destOnFrame.right - rectOnFrame.left,
        destOnFrame.bottom - rectOnFrame.top
    )

    c.drawBitmap(photo, null, destOnOutput, null)
    return output
}

private val RECT_MARCO = RectPerc(
    left   = 0.237f,   // ~col 420 px
    top    = 0.076f,   // ~fila 134 px
    right  = 0.832f,   // ~col 1474 px
    bottom = 0.925f
)

/**
 * Toma un bitmap YA RECORTADO al rectángulo y lo coloca dentro del PNG del marco,
 * luego dibuja el marco encima. Devuelve el bitmap final (mismo tamaño que el PNG).
 */
fun composeCroppedIntoFrame(
    context: Context,
    cropped: Bitmap,                     // ← el recorte que ya hiciste
    @DrawableRes frameRes: Int = R.drawable.marco_final,
    rectPerc: RectPerc = RECT_MARCO,
    backgroundColor: Int? = null         // ej. android.graphics.Color.BLACK o null para transparente
): Bitmap {
    val frame = BitmapFactory.decodeResource(context.resources, frameRes)
    val outW = frame.width
    val outH = frame.height

    val l = (outW * rectPerc.left)
    val t = (outH * rectPerc.top)
    val r = (outW * rectPerc.right)
    val b = (outH * rectPerc.bottom)
    val areaW = (r - l).toInt()
    val areaH = (b - t).toInt()

    val result = createBitmap(outW, outH)
    val c = Canvas(result)

    // Fondo opcional (si tu PNG tiene zonas transparentes y quieres color detrás)
    backgroundColor?.let { c.drawColor(it) }

    // Asegurar que el recorte encaja al 100% dentro del rectángulo del marco
    val toDraw = if (cropped.width != areaW || cropped.height != areaH) {
        cropped.scale(areaW, areaH)
    } else cropped

    // 1) Pegar el recorte en la zona del rectángulo
    c.drawBitmap(toDraw, null, RectF(l, t, r, b), null)

    // 2) Dibujar el marco encima
    c.drawBitmap(frame, 0f, 0f, null)

    return result
}

fun trimTransparentBorders(
    src: Bitmap,
    alphaThreshold: Int = 10,   // 5–20: mayor = más agresivo con semitransparencias
    padPx: Int = 0              // margen interno opcional
): Bitmap {
    val w = src.width
    val h = src.height

    fun rowHasContent(y: Int, x0: Int, x1: Int): Boolean {
        val row = IntArray(x1 - x0 + 1)
        src.getPixels(row, 0, row.size, x0, y, row.size, 1)
        return row.any { android.graphics.Color.alpha(it) > alphaThreshold }
    }

    fun colHasContent(x: Int, y0: Int, y1: Int): Boolean {
        val col = IntArray(y1 - y0 + 1)
        src.getPixels(col, 0, 1, x, y0, 1, col.size)
        return col.any { android.graphics.Color.alpha(it) > alphaThreshold }
    }

    var top = 0
    while (top < h && !rowHasContent(top, 0, w - 1)) top++
    var bottom = h - 1
    while (bottom >= top && !rowHasContent(bottom, 0, w - 1)) bottom--

    var left = 0
    while (left < w && !colHasContent(left, top, bottom)) left++
    var right = w - 1
    while (right >= left && !colHasContent(right, top, bottom)) right--

    // padding interno opcional
    left = (left - padPx).coerceAtLeast(0)
    top = (top - padPx).coerceAtLeast(0)
    right = (right + padPx).coerceAtMost(w - 1)
    bottom = (bottom + padPx).coerceAtMost(h - 1)

    val newW = (right - left + 1).coerceAtLeast(1)
    val newH = (bottom - top + 1).coerceAtLeast(1)
    return Bitmap.createBitmap(src, left, top, newW, newH)
}

fun overBackground(src: Bitmap, color: Int = 0xFFFFFFFF.toInt()): Bitmap {
    val out = createBitmap(src.width, src.height)
    val c = Canvas(out)
    c.drawColor(color)
    c.drawBitmap(src, 0f, 0f, null)
    return out
}


fun drawCodeTopRightText(base: Bitmap, code: Int): Bitmap {
    val bmp = base.copy(base.config, true)
    val c = Canvas(bmp)

    val text = "$code"

    // Tamaños proporcionales al alto del bitmap
    val margin = (bmp.height * 0.03f)
    val textSize = (bmp.height * 0.05f)
    val strokeW = (bmp.height * 0.006f)

    // Pintura de relleno (blanco, negrita)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        this.textSize = textSize
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    // Pintura de contorno (negro)
    val stroke = android.graphics.Paint(fill).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeW
        color = android.graphics.Color.BLACK
    }

    // Cálculo de baseline: alinear la “parte superior” a margin
    val fm = fill.fontMetrics
    val topToBaseline = -fm.ascent            // distancia desde top del texto a baseline
    val x = bmp.width - margin
    val y = margin + topToBaseline

    // Dibuja contorno y luego relleno
    c.drawText(text, x, y, stroke)
    c.drawText(text, x, y, fill)

    return bmp
}


//FOTO EN CIRCULO
private val CIRCLE_DEFAULT = RectPerc(0.12f, 0.12f, 0.88f, 0.88f)

private fun centerCropDest(canvasW: Int, canvasH: Int, src: Bitmap): RectF {
    val scale = maxOf(canvasW / src.width.toFloat(), canvasH / src.height.toFloat())
    val dw = src.width * scale
    val dh = src.height * scale
    val dx = (canvasW - dw) / 2f
    val dy = (canvasH - dh) / 2f
    return RectF(dx, dy, dx + dw, dy + dh)
}

/** compone foto dentro de un círculo y dibuja el aro; WYSIWYG con el preview */
fun composeCircleWysiwyg(
    photo: Bitmap,
    circle: RectPerc = CIRCLE_DEFAULT,
    outSizePx: Int = minOf(photo.width, photo.height), // salida cuadrada
    ringColorInt: Int = android.graphics.Color.rgb(42, 167, 240),
    ringWidthPx: Int = 18,
    backgroundColorInt: Int? = android.graphics.Color.WHITE // null => transparente fuera del círculo
): Bitmap {
    val outW = outSizePx
    val outH = outSizePx
    val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val c = Canvas(result)

    // fondo (blanco o transparente)
    backgroundColorInt?.let { c.drawColor(it) }

    // rectángulo del círculo en la salida
    val l = outW * circle.left
    val t = outH * circle.top
    val r = outW * circle.right
    val b = outH * circle.bottom
    val circleRect = android.graphics.RectF(l, t, r, b)

    // dibuja la foto SOLO dentro del círculo (clip)
    val save = c.save()
    val path = android.graphics.Path().apply { addOval(circleRect, android.graphics.Path.Direction.CW) }
    c.clipPath(path)
    val dest = centerCropDest(outW, outH, photo) // mismo FILL_CENTER que el preview
    c.drawBitmap(photo, null, dest, null)
    c.restoreToCount(save)

    // aro
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColorInt
        style = Paint.Style.STROKE
        strokeWidth = ringWidthPx.toFloat()
    }
    c.drawOval(circleRect, ringPaint)
    return result
}

