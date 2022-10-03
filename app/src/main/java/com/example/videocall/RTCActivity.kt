package com.example.videocall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.webrtc.*

@ExperimentalCoroutinesApi
class RTCActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
        const val ARGUMENT_MEETING_ID = "meetingID"
        const val ARGUMENT_IS_JOIN = "isJoin"
    }

    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignalingClient
    private lateinit var remoteView: SurfaceViewRenderer
    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteViewLoading: ProgressBar
    private lateinit var micButton: ImageView
    private lateinit var videoButton: ImageView
    private lateinit var endCallButton: ImageView
    private lateinit var switchCameraButton: ImageView
    private lateinit var audioOutputButton: ImageView

    private val audioManager by lazy { RTCAudioManager.create(this) }

    val TAG = "MainActivity"

    private var meetingID: String = "test-call"

    private var isJoin = false

    private var isMute = false

    private var isVideoPaused = false

    private var inSpeakerMode = true

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
//            signallingClient.send(p0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtc)
        initUiViews()
        if (intent.hasExtra("meetingID"))
            meetingID = intent.getStringExtra("meetingID")!!
        if (intent.hasExtra("isJoin"))
            isJoin = intent.getBooleanExtra("isJoin", false)

        checkCameraAndAudioPermission()
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        switchCameraButton.setOnClickListener {
            rtcClient.switchCamera()
        }

        audioOutputButton.setOnClickListener {
            if (inSpeakerMode) {
                inSpeakerMode = false
                audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
            } else {
                inSpeakerMode = true
                audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
            }
        }
        videoButton.setOnClickListener {
            if (isVideoPaused) {
                isVideoPaused = false
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                isVideoPaused = true
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            rtcClient.enableVideo(isVideoPaused)
        }
        micButton.setOnClickListener {
            if (isMute) {
                isMute = false
                micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
            } else {
                isMute = true
                micButton.setImageResource(R.drawable.ic_baseline_mic_24)
            }
            rtcClient.enableAudio(isMute)
        }
        endCallButton.setOnClickListener {
            rtcClient.endCall(meetingID)
            remoteView.isGone = false
            Constants.isCallEnded = true
            finish()
            startActivity(Intent(this@RTCActivity, MainActivity::class.java))
        }
    }


    private fun initUiViews() {
        remoteView = findViewById(R.id.remote_view)
        localView = findViewById(R.id.local_view)
        remoteViewLoading = findViewById(R.id.remote_view_loading)
        micButton = findViewById(R.id.mic_button)
        videoButton = findViewById(R.id.video_button)
        endCallButton = findViewById(R.id.end_call_button)
        switchCameraButton = findViewById(R.id.switch_camera_button)
        audioOutputButton = findViewById(R.id.audio_output_button)
    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    private fun onCameraAndAudioPermissionGranted() {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    signallingClient.sendIceCandidate(p0, isJoin)
                    rtcClient.addIceCandidate(p0)
                }

                override fun onAddStream(p0: MediaStream?) {
                    super.onAddStream(p0)
                    Log.e(TAG, "onAddStream: $p0")
                    p0?.videoTracks?.get(0)?.addSink(remoteView)
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.e(TAG, "onIceConnectionChange: $p0")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.e(TAG, "onIceConnectionReceivingChange: $p0")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.e(TAG, "onConnectionChange: $newState")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.e(TAG, "onDataChannel: $p0")
                }

                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.e(TAG, "onStandardizedIceConnectionChange: $newState")
                }

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    Log.e(TAG, "onAddTrack: $p0 \n $p1")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    Log.e(TAG, "onTrack: $transceiver")
                }
            }
        )

        rtcClient.initSurfaceView(remoteView)
        rtcClient.initSurfaceView(localView)
        rtcClient.startLocalVideoCapture(localView)
        signallingClient = SignalingClient(meetingID, createSignallingClientListener())
        if (!isJoin)
            rtcClient.call(sdpObserver, meetingID)
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            endCallButton.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            rtcClient.answer(sdpObserver, meetingID)
            remoteViewLoading.isGone = true
        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            remoteViewLoading.isGone = true
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }

        override fun onCallEnded() {
            if (!Constants.isCallEnded) {
                Constants.isCallEnded = true
                rtcClient.endCall(meetingID)
                finish()
                startActivity(Intent(this@RTCActivity, MainActivity::class.java))
            }
        }
    }

    private fun requestCameraAndAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) &&
            !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
                CAMERA_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera And Audio Permission Required")
            .setMessage("This app need the camera and audio to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraAndAudioPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_AUDIO_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraAndAudioPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera and Audio Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        signallingClient.destroy()
        super.onDestroy()
    }
}