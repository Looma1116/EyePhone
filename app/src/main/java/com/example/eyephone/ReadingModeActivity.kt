package com.example.eyephone

import android.Manifest
import android.R
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.coroutines.CoroutineContext


class ReadingModeActivity: AppCompatActivity() ,CoroutineScope {
    private lateinit var camera: android.hardware.camera2.CameraDevice
    private lateinit var surfaceView: SurfaceView
    private lateinit var streamButton: Button
    private lateinit var outputStream: DataOutputStream
    private lateinit var inputStream: DataInputStream
    private lateinit var socket: Socket
    private var isStreaming = false
    private var streamingConfirm = false
    private lateinit var imageReader: ImageReader
    private val imageProcessingSemaphore = Semaphore(2)
    private lateinit var tts:TextToSpeech

    @Volatile
    private var isProcessingImage = false
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.eyephone.R.layout.activity_reading_mode)
        job = Job()

        //streaming 실행 코드
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                WalkingModeActivity.CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            //화면 켜짐 유지 코드
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val serverUrl = "112.187.163.193"//"10.0.2.2" //localhost
            val port = 9999

            val socket = Socket(serverUrl, port)
            outputStream = DataOutputStream(socket.getOutputStream())
            inputStream = DataInputStream(socket.getInputStream())
            var mode = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0)
                .array()
            outputStream.write(mode)
            outputStream.flush()

            startStreaming()
            streamButton.text = "Stop Streaming"
        }

        var backBtn: ImageView = findViewById(com.example.eyephone.R.id.reading_mode_backBtn)

        backBtn.setOnClickListener {
            stopStreaming()
            streamButton.text = "Start Streaming"
            val mainIntent = Intent(this, MainActivity::class.java)
            startActivity(mainIntent)
            finish()
        }
        // 권한 설정 같은 거 (소켓 쓸 때 오류 났었음)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        surfaceView = findViewById(com.example.eyephone.R.id.reading_surfaceView)
        streamButton = findViewById(com.example.eyephone.R.id.reading_streamButton)


      /*  streamButton.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
                //화면 켜짐 유지 해제 코드
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                streamButton.text = "Start Streaming"
            } else {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        WalkingModeActivity.CAMERA_PERMISSION_REQUEST_CODE
                    )
                } else {
                    //화면 켜짐 유지 코드
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                    val serverUrl = "112.187.163.193"//"10.0.2.2" //localhost
                    val port = 9999

                    val socket = Socket(serverUrl, port)
                    outputStream = DataOutputStream(socket.getOutputStream())
                    inputStream = DataInputStream(socket.getInputStream())
                    var mode = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0)
                        .array()
                    outputStream.write(mode)
                    outputStream.flush()

                    startStreaming()
                    streamButton.text = "Stop Streaming"
                }
            }
            isStreaming = !isStreaming
        }
*/

    }

    private fun startStreaming() {
        val serverUrl = "112.187.163.193"//"10.0.2.2" //localhost
        val port = 9999


        val imageChannel = Channel<ByteArray>()
        val cameraJob = launch(Dispatchers.IO) {
            startCamera(serverUrl, port, imageChannel)
        }

        val inputJob = launch(Dispatchers.IO) {
            while (true) {
                val lengthBytes = ByteArray(4)
                inputStream.read(lengthBytes)
                val len = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).int

                val dataBytes = ByteArray(len)
                inputStream.read(dataBytes)
                val receivedString = dataBytes.toString(Charsets.UTF_8)
                println("Received string: $receivedString")
                //서버에서 받은 문자열 스피커로 출력
                setTTS(receivedString)
//                playTTS(receivedString)
            }
        }
//        val socketJob = launch(Dispatchers.IO) {
//            startSocket(serverUrl, port, imageChannel)
//        }
        launch {
            cameraJob.join()
            inputJob.join()
//            socketJob.join()
        }
    }

    private suspend fun startCamera(serverUrl: String,
                                    port: Int,
                                    imageChannel: Channel<ByteArray>
    ) {

        try {


            val cameraManager =
                getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]



            if (ActivityCompat.checkSelfPermission(
                    this@ReadingModeActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            streamingConfirm = true
            val handlerThread = HandlerThread("CameraThread")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
//            val socket = Socket(serverUrl, port)
//            outputStream = DataOutputStream(socket.getOutputStream())

            val processingJob = Job()
            val processingScope = CoroutineScope(Dispatchers.IO + processingJob)

//            val inputData = launch(Dispatchers.IO) {
//                getData(serverUrl ,port)
//            }


            cameraManager.openCamera(
                cameraId,
                object : android.hardware.camera2.CameraDevice.StateCallback() {
                    override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                        this@ReadingModeActivity.camera = camera
                        val surface = surfaceView.holder.surface
//                        val preview_image_format = ImageFormat.YUV_420_888
                        val preview_image_format = ImageFormat.JPEG
                        val imageReader = ImageReader.newInstance(
                            surfaceView.width/3, surfaceView.height/3, preview_image_format, 32
                        )

                        val ImageAvailableListener: (ImageReader) -> Unit =
                            { reader ->
                                processingScope.launch {
                                    if (streamingConfirm) {
                                        if (isProcessingImage) {
                                            return@launch
                                        }
                                        isProcessingImage = true
                                        var image: Image? = null
                                        try {
                                            imageProcessingSemaphore.acquire()
                                            val image = reader.acquireNextImage()
                                            if (image != null) {
                                                processImage(image)
                                                delay(44)
//                                                getData(serverUrl ,port)
                                            } else {
                                                isProcessingImage = false
                                            }
                                        } catch (e: Exception) {
                                            Log.e(WalkingModeActivity.TAG, "Failed to outputStream Write", e)
                                            isProcessingImage = false
                                        } finally {
                                            imageProcessingSemaphore.release()
                                            isProcessingImage = false
                                        }
                                    }
                                }
                            }

                        imageReader.setOnImageAvailableListener(ImageAvailableListener, null)
                        // SurfaceLayout에 프리뷰 띄우기
                        val surfaces = listOf(surface, imageReader.surface)
                        val captureRequestBuilder =
                            camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW)
                        captureRequestBuilder.addTarget(surface)
                        captureRequestBuilder.addTarget(imageReader.surface)

                        camera.createCaptureSession(
                            surfaces,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.setRepeatingRequest(
                                            captureRequestBuilder.build(),
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: CaptureRequest,
                                                    result: TotalCaptureResult
                                                ) {
                                                }
                                            },
                                            null
                                        )
                                    } catch (e: Exception) {
                                        Log.e(WalkingModeActivity.TAG, "Failed to set up capture request", e)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(WalkingModeActivity.TAG, "Failed to configure camera capture session")
                                }
                            },
                            null
                        )
                    }

                    override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                        Log.e(WalkingModeActivity.TAG, "Camera disconnected")
                    }

                    override fun onError(
                        camera: android.hardware.camera2.CameraDevice,
                        error: Int
                    ) {
                        Log.e(WalkingModeActivity.TAG, "Camera error: $error")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(WalkingModeActivity.TAG, "Failed to start streaming", e)
            e.printStackTrace()
        }
    }

    suspend fun processImage(image: Image){
        try {
            if (image.planes != null && streamingConfirm) {
                var buffer = image.planes[0].buffer

                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                // Convert YUV_420_888 (Camera2 default format) to JPEG
//                val yuvImage = YuvImage(
//                    bytes,
//                    ImageFormat.NV21,
//                    image.width,
//                    image.height,
//                    null
//                )

                val byteArrayOutputStream = ByteArrayOutputStream()
//                yuvImage.compressToJpeg(
//                    Rect(0, 0, image.width, image.height),
//                    50,
//                    byteArrayOutputStream
//                )
//                val jpegBytes = byteArrayOutputStream.toByteArray()
                launch {
                    withContext(Dispatchers.IO) {// Send the JPEG byte array to the imageChannel
//                        val imageSize = jpegBytes.size
                        val imageSize = bytes.size
                        val imageSizeBytes =
                            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(imageSize)
                                .array()
//                        outputStream.writeUTF("read")
                        outputStream.write(imageSizeBytes)


                        // Send the actual image byte array
//                        outputStream.write(jpegBytes)
                        outputStream.write(bytes)
                        image.close()
                        outputStream.flush()


//                                                    imageChannel.send(jpegBytes)

                    }
                }
            }
        }catch (e:Exception){
            Log.e(WalkingModeActivity.TAG, "Error processing image", e)
        } finally {
            isProcessingImage = false
        }

    }
//    private suspend fun getData(serverUrl: String,
//                                port: Int){
//        val socket = Socket(serverUrl, port)
//        inputStream = DataInputStream(socket.getInputStream())
//        val reader = BufferedReader(InputStreamReader(inputStream))
//        val receivedString = reader.readLine()
//        if (receivedString != null && receivedString.isNotEmpty()) {
//            // String received successfully
//            Log.d("Tag", "Received string: $receivedString")
//            //서버에서 받은 문자열 스피커로 출력
//            // setTTS()
//            //playTTS(receivedString)
//        } else {
//            // String not received
//            Log.d("Tag", "String not received or is empty")
//            // Handle the absence of the string, perform appropriate actions or show an error message
//        }
//    }

    //    private suspend fun startSocket(
//        serverUrl: String,
//        port: Int,
//        imageChannel: Channel<ByteArray>
//    ) {
//        try {
//            val socket = Socket(serverUrl, port)
//            outputStream = DataOutputStream(socket.getOutputStream())
//
//            for (jpegBytes in imageChannel) {
//                // Send the size of the byte array
//                val imageSize = jpegBytes.size
//                val imageSizeBytes =
//                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(imageSize).array()
//                outputStream.write(imageSizeBytes)
//
//                // Send the actual image byte array
//                outputStream.write(jpegBytes)
//                outputStream.flush()
//                delay(330)
//
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to start streaming", e)
//            e.printStackTrace()
//        }
//    }
    fun playTTS(text: String) {
        // Set the Utterance ID to identify the speech
        val utteranceId = UUID.randomUUID().toString()

        // Set the language based on the detected language of the text
        val language = detectLanguage(text)
        if (language == "ko") {
            tts.language = Locale.KOREAN
        } else {
            tts.language = Locale.ENGLISH
        }

        // Speak the text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun detectLanguage(text: String): String {
        // Use a language detection library or API to detect the language of the text
        // Return the language code (e.g., "ko" for Korean, "en" for English)
        // If the language detection library is not available, you can consider using a rule-based approach or fallback to a default language
        // Here's an example using ML Kit Language Identification API:
        // val languageIdentifier = LanguageIdentification.getClient()
        // val task = languageIdentifier.identifyLanguage(text)
        // val result = Tasks.await(task)
        // return result.languageTag

        // For demonstration purposes, let's assume we have a simple rule-based language detector
        return if (text.contains(Regex("[가-힣]"))) {
            "ko" // Korean
        } else {
            "en" // English
        }
    }

    fun shutdownTTS() {
        // Shutdown the TextToSpeech engine when it's no longer needed
        tts.stop()
        tts.shutdown()
    }

    private fun setTTS(text: String) {
        // Initialize the TextToSpeech engine
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val language = detectLanguage(text)

                if (language == "ko") {
                    val result = tts.setLanguage(Locale.KOREAN)
                } else {
                    val result = tts.setLanguage(Locale.ENGLISH)
                }
                // Set the language to both Korean and English
//                val result = tts.setLanguage(Locale.KOREAN)
//                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                    // Failed to set Korean language, handle the error
//                    // You can fallback to English or display an error message
//                } else {
//                    // Language set successfully, you can start using the TTS engine
//                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                // TextToSpeech initialization failed, handle the error
            }
        }
    }



    private fun stopStreaming() {
        try {
            streamingConfirm = false
            outputStream?.close()
            inputStream?.close()
            socket?.close()
            shutdownTTS()

            camera?.close()
            if (::imageReader.isInitialized) {
                imageReader.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop streaming", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WalkingModeActivity.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startStreaming()
                streamButton.text = "Stop Streaming"
            } else {
                //showToast("Camera permission is required.")
            }
        }
    }
    override fun onPause() {
        super.onPause()
        if (isStreaming) {
            stopStreaming()
            streamButton.text = "Start Streaming"
            isStreaming = false
        }
    } override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}