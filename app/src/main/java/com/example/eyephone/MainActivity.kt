package com.example.eyephone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Semaphore
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope{

    private lateinit var camera: android.hardware.camera2.CameraDevice
    private lateinit var surfaceView: SurfaceView
    private lateinit var streamButton: Button
    private lateinit var outputStream: DataOutputStream
    private var isStreaming = false
    private var streamingConfirm = false
    private lateinit var imageReader: ImageReader
    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
    private val imageProcessingSemaphore = Semaphore(2)

    @Volatile
    private var isProcessingImage = false
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + job
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        job = Job()

        // 권한 설정 같은 거 (소켓 쓸 때 오류 났었음)
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        surfaceView = findViewById(R.id.surfaceView)
        streamButton = findViewById(R.id.streamButton)

        streamButton.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
                streamButton.text = "Start Streaming"
            } else {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                } else {
                    startStreaming()
                    streamButton.text = "Stop Streaming"
                }
            }
            isStreaming = !isStreaming
        }
    }

    private fun startStreaming() {
        val serverUrl = "112.187.163.193"//"10.0.2.2" //localhost
        val port = 9999

        val imageChannel = Channel<ByteArray>()
        val cameraJob = launch(Dispatchers.IO) {
            startCamera(serverUrl, port, imageChannel)
        }

//        val socketJob = launch(Dispatchers.IO) {
//            startSocket(serverUrl, port, imageChannel)
//        }
        launch {
            cameraJob.join()
//            socketJob.join()
        }
    }

    private suspend fun startCamera(serverUrl: String,
                                    port: Int,
                                    imageChannel: Channel<ByteArray>) {

        try {


            val cameraManager =
                getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]



            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            streamingConfirm = true
            val handlerThread = HandlerThread("CameraThread")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            val socket = Socket(serverUrl, port)
            outputStream = DataOutputStream(socket.getOutputStream())

            val processingJob = Job()
            val processingScope = CoroutineScope(Dispatchers.IO + processingJob)

            cameraManager.openCamera(
                cameraId,
                object : android.hardware.camera2.CameraDevice.StateCallback() {
                    override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                        this@MainActivity.camera = camera
                        val surface = surfaceView.holder.surface
//                        val preview_image_format = ImageFormat.YUV_420_888
                        val preview_image_format = ImageFormat.JPEG
                        val imageReader = ImageReader.newInstance(
                           surfaceView.width/5, surfaceView.height/5, preview_image_format, 32
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
                                            } else {
                                                isProcessingImage = false
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to outputStream Write", e)
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
                                        Log.e(TAG, "Failed to set up capture request", e)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e(TAG, "Failed to configure camera capture session")
                                }
                            },
                            null
                        )
                    }

                    override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                        Log.e(TAG, "Camera disconnected")
                    }

                    override fun onError(
                        camera: android.hardware.camera2.CameraDevice,
                        error: Int
                    ) {
                        Log.e(TAG, "Camera error: $error")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
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
            Log.e(TAG, "Error processing image", e)
        } finally {
            isProcessingImage = false
        }

    }
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

    private fun stopStreaming() {
        try {
            camera.close()
            streamingConfirm = false
            outputStream.close()
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
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
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
