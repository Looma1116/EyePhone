package com.example.eyephone

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.Socket


class MainActivity : AppCompatActivity() {

    private lateinit var camera: android.hardware.camera2.CameraDevice
    private lateinit var surfaceView: SurfaceView
    private lateinit var streamButton: Button
    private lateinit var outputStream: DataOutputStream

    private var isStreaming = false

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


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
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                } else {
                    startStreaming()
                    streamButton.text = "Stop Streaming"
                }
            }
            isStreaming = !isStreaming
        }
    }

    private fun startStreaming() {
        val serverUrl = "10.0.2.2" //localhost
        val port = 9999
        println("111111111111")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("22222222222")
                val socket = Socket(serverUrl, port)

                outputStream = DataOutputStream(socket.getOutputStream())

                val cameraManager =
                    getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val cameraId = cameraManager.cameraIdList[0]

                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return@launch
                }
                println("333333333")

                val handlerThread = HandlerThread("CameraThread")
                handlerThread.start()
                val handler = Handler(handlerThread.looper)



                cameraManager.openCamera(
                    cameraId,
                    object : android.hardware.camera2.CameraDevice.StateCallback() {
                        override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                            this@MainActivity.camera = camera
                            val surface = surfaceView.holder.surface
                            val preview_image_format = ImageFormat.YUV_420_888
                            val imageReader = ImageReader.newInstance(
                                surfaceView.width, surfaceView.height, preview_image_format, 4
                            )
                            print(imageReader)

                            // 왜 호출이 안되지?
                            imageReader.setOnImageAvailableListener(
                                { reader ->
                                    try{
                                    println("ImageReader listener called 야호!")
                                    val image = reader.acquireLatestImage()
                                    if (image.planes != null){
                                        var buffer = image.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        outputStream.write(bytes)
                                        outputStream.flush()
                                        image.close()
                                        }
                                    }
                                    catch (e: Exception) {
                                        Log.e(TAG, "Failed to outputStream Write", e)
                                    }
                                },
                                handler//Handler(Looper.getMainLooper())
                            )

                            println("55555555")

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
                                                        //println("Repeating Request) //Thread identical 15 lines : 넘 많이 찍혀서 생략됨"
                                                    }
                                                },
                                                null,

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
    }



    private fun stopStreaming() {
        try {
            camera.close()
            outputStream.close()
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
                isStreaming = true
                startStreaming();
                streamButton.text = "Stop Streaming"

            }
        }
    }
}
