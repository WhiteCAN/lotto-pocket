package io.github.lottopocket.ticket

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

@Composable
fun CaptureInput(
    onDraft: (round: Int?, gamesText: String, source: String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestDraft = rememberUpdatedState(onDraft)
    val latestError = rememberUpdatedState(onError)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    var active by remember { mutableStateOf(true) }
    var pendingQrPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun image(uri: Uri): InputImage? = runCatching {
        InputImage.fromFilePath(context, uri)
    }.getOrElse {
        latestError.value("사진을 읽을 수 없습니다.")
        null
    }

    fun scanQr(uri: Uri, capturedFile: File?) {
        val input = image(uri)
        if (input == null) {
            capturedFile?.delete()
            return
        }
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                if (!active) return@addOnSuccessListener
                val raw = barcodes.firstOrNull()?.rawValue
                when (val result = raw?.let(::parseQr)) {
                    is TicketParseResult.Success -> latestDraft.value(
                        result.draft.round,
                        result.draft.games.toGamesText(),
                        "QR",
                    )
                    is TicketParseResult.Error -> latestError.value(result.message)
                    null -> latestError.value("사진에서 QR 코드를 찾지 못했습니다.")
                }
            }
            .addOnFailureListener {
                if (active) latestError.value("QR 코드를 인식하지 못했습니다.")
            }
            .addOnCompleteListener { capturedFile?.delete() }
    }

    fun scanPhoto(uri: Uri, capturedFile: File?) {
        val input = image(uri)
        if (input == null) {
            capturedFile?.delete()
            return
        }
        recognizer.process(input)
            .addOnSuccessListener { text ->
                if (!active) return@addOnSuccessListener
                val games = extractOcrGames(text.text)
                if (games.isEmpty()) {
                    latestError.value("사진에서 완전한 번호 6개 행을 찾지 못했습니다.")
                } else {
                    latestDraft.value(null, games.toGamesText(), "PHOTO")
                }
            }
            .addOnFailureListener {
                if (active) latestError.value("사진의 번호를 인식하지 못했습니다.")
            }
            .addOnCompleteListener { capturedFile?.delete() }
    }

    val qrCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingQrPath?.let(::File).also { pendingQrPath = null }
        if (saved && file != null) scanQr(FileProvider.getUriForFile(context, FILE_AUTHORITY, file), file)
        else file?.delete()
    }
    val photoCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhotoPath?.let(::File).also { pendingPhotoPath = null }
        if (saved && file != null) scanPhoto(FileProvider.getUriForFile(context, FILE_AUTHORITY, file), file)
        else file?.delete()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanPhoto(uri, null)
    }

    fun newCaptureFile(): File? = runCatching {
        val directory = File(context.cacheDir, "capture").apply { mkdirs() }
        File.createTempFile("ticket-", ".jpg", directory)
    }.getOrElse {
        latestError.value("촬영 파일을 준비하지 못했습니다.")
        null
    }

    DisposableEffect(scanner, recognizer) {
        onDispose {
            active = false
            scanner.close()
            recognizer.close()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CaptureButton("QR 촬영") {
            newCaptureFile()?.let { file ->
                pendingQrPath = file.absolutePath
                runCatching {
                    qrCamera.launch(FileProvider.getUriForFile(context, FILE_AUTHORITY, file))
                }.onFailure {
                    pendingQrPath = null
                    file.delete()
                    latestError.value("카메라를 열 수 없습니다.")
                }
            }
        }
        CaptureButton("용지 촬영") {
            newCaptureFile()?.let { file ->
                pendingPhotoPath = file.absolutePath
                runCatching {
                    photoCamera.launch(FileProvider.getUriForFile(context, FILE_AUTHORITY, file))
                }.onFailure {
                    pendingPhotoPath = null
                    file.delete()
                    latestError.value("카메라를 열 수 없습니다.")
                }
            }
        }
        CaptureButton("사진에서 불러오기") {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

@Composable
private fun CaptureButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
    ) {
        Text(label)
    }
}

private fun List<List<Int>>.toGamesText(): String =
    joinToString("\n") { game -> game.joinToString(" ") }

private const val FILE_AUTHORITY = "io.github.lottopocket.fileprovider"
