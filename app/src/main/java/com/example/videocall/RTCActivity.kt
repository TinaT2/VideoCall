package com.example.videocall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
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
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

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

    val TAG = this::class.simpleName

    private var meetingID: String = "test-call"

    private var isJoin = false

    private var isMute = false

    private var isVideoPaused = false

    private var inSpeakerMode = true

    private val sdpObserver = object : AppSdpObserver() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtc)
        initUiViews()
        initUiComponent()
        initUiListeners()
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

    private fun initUiComponent() {
        getArgument()
        checkCameraAndAudioPermission()
    }
    private fun initUiListeners(){
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        switchCameraListener()
        audioOutputListener()
        videoListener()
        micListener()
        endCallButtonListener()
    }

    private fun endCallButtonListener() {
        endCallButton.setOnClickListener {
            rtcClient.endCall(meetingID)
            remoteView.isGone = false
            Constants.isCallEnded = true
            finish()
            startActivity(Intent(this@RTCActivity, MainActivity::class.java))
        }
    }

    private fun micListener() {
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
    }

    private fun videoListener() {
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
    }

    private fun audioOutputListener() {
        audioOutputButton.setOnClickListener {
            if (inSpeakerMode) {
                earPieceMode()
            } else {
                speakerMode()
            }
        }
    }

    private fun switchCameraListener() {
        switchCameraButton.setOnClickListener {
            rtcClient.switchCamera()
        }
    }

    private fun earPieceMode() {
        inSpeakerMode = false
        audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
        audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
    }

    private fun speakerMode() {
        inSpeakerMode = true
        audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
        audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
    }

    private fun getArgument() {
        if (intent.hasExtra(ARGUMENT_MEETING_ID))
            meetingID = intent.getStringExtra(ARGUMENT_MEETING_ID)!!
        if (intent.hasExtra(ARGUMENT_IS_JOIN))
            isJoin = intent.getBooleanExtra(ARGUMENT_IS_JOIN, false)
    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(
                this,
                AUDIO_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    private fun onCameraAndAudioPermissionGranted() {
        rtcClient = RTCClient(application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(iceCandidate: IceCandidate?) {
                    Log.v(Constants.SCENARIO_TAG_LOG+"Acty-PeerConObsvr:","onIceCandidate")
                    super.onIceCandidate(iceCandidate)
                    signallingClient.sendIceCandidate(iceCandidate, isJoin)
                    rtcClient.addIceCandidate(iceCandidate)
                }

                override fun onAddStream(mediaStream: MediaStream?) {
                    Log.v(Constants.SCENARIO_TAG_LOG+ "Acty-PeerConObsvr:","onAddStream")
                    super.onAddStream(mediaStream)
                    Log.e(TAG, "onAddStream: $mediaStream")
                    mediaStream?.videoTracks?.get(0)?.addSink(remoteView)
                }
            })

        rtcClient.initSurfaceView(remoteView)
        rtcClient.initSurfaceView(localView)
        rtcClient.startLocalVideoCapture(localView)
        signallingClient = SignalingClient(meetingID,createSignallingClientListener())
        if(!isJoin)
            rtcClient.call(sdpObserver,meetingID)
    }

    private fun createSignallingClientListener() = object : SignalingClientListener{
        override fun onConnectionEstablished() {
            Log.v(Constants.SCENARIO_TAG_LOG+"Acty-SignalingCL","onConnectionEstablished")
            endCallButton.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {
            Log.v(Constants.SCENARIO_TAG_LOG+ "Acty-SignalingCL","onOfferReceived")
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            rtcClient.answer(sdpObserver, meetingID)
            remoteViewLoading.isGone = true
        }

        override fun onAnswerReceived(description: SessionDescription) {
            Log.v(Constants.SCENARIO_TAG_LOG+ "Acty-SignalingCL"," onAnswerReceived")
            rtcClient.onRemoteSessionReceived(description)
            Constants.isIntiatedNow = false
            remoteViewLoading.isGone = true
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            Log.v(Constants.SCENARIO_TAG_LOG+ "Acty-SignalingCL","onIceCandidateReceived")
            rtcClient.addIceCandidate(iceCandidate)
        }

        override fun onCallEnded() {
            Log.v(Constants.SCENARIO_TAG_LOG+ "Acty-SignalingCL","onCallEnded")
            if(!Constants.isCallEnded){
                Constants.isCallEnded = true
                rtcClient.endCall(meetingID)
                finish()
                startActivity(Intent(this@RTCActivity,MainActivity::class.java))
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
                this, arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
                CAMERA_AUDIO_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissionRationaleTitle))
            .setMessage(getString(R.string.permissionRationaleMessage))
            .setPositiveButton(getString(R.string.permissionRationalePositive)) { dialog, _ ->
                dialog.dismiss()
                requestCameraAndAudioPermission(true)
            }.setNegativeButton(getString(R.string.permissionRationaleDeny)) { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }.show()
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