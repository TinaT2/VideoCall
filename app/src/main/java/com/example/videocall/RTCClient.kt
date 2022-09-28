package com.example.videocall

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.webrtc.*
import org.webrtc.PeerConnection.Observer


class RTCClient(context: Application, observer: PeerConnection.Observer) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
        private const val FIELD_TRIALS = "WebRTC-H264HighProfile/Enabled/"
        private const val OFFER_TO_RECEIVE_VIDEO = "OfferToReceiveVideo"
        private const val TRUE = "true"
        private const val ICE_SERVER = "stun:stun.l.google.com:19302"
        private const val END_CALL_SNAIL = "END_CALL"
    }

    private val rootEglBase: EglBase = EglBase.create()

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    val TAG = this::class.simpleName

    var remoteSessionDescription: SessionDescription? = null
    val network = Firebase.firestore

    private val iceServer = listOf(
        PeerConnection.IceServer.builder(ICE_SERVER)
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }

    private val audioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }


    init {
        initPeerConnectionFactory(context)
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials(FIELD_TRIALS)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory =
        PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()

    private fun buildPeerConnection(observer: Observer) =
        peerConnectionFactory.createPeerConnection(iceServer, observer)


    private fun PeerConnection.call(sdpObserver: SdpObserver, meetingId: String) {
        createOffer(
            sdpObserver(sdpObserver, meetingId, SDPTypeEnum.OFFER),
            createMediaConstraints()
        )
    }


    private fun PeerConnection.answer(sdpObserver: SdpObserver, meetingId: String) {
        createAnswer(
            sdpObserver(sdpObserver, meetingId, SDPTypeEnum.ANSWER),
            createMediaConstraints()
        )
    }

    private fun createMediaConstraints(): MediaConstraints {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair(OFFER_TO_RECEIVE_VIDEO, TRUE))
        }
        return constraints
    }

    private fun PeerConnection.sdpObserver(
        sdpObserver: SdpObserver,
        meetingId: String,
        type: SDPTypeEnum
    ) = object : SdpObserver by sdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
            setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    if (type == SDPTypeEnum.ANSWER)
                        makeValueToSetOnFirestoreDB()
                    Log.e(TAG, "onCreateSuccess: Description $p0")
                }

                override fun onSetSuccess() {
                    if (type == SDPTypeEnum.OFFER)
                        makeValueToSetOnFirestoreDB()
                }

                private fun makeValueToSetOnFirestoreDB() {
                    val value = hashMapOf(
                        Constants.SDP to sessionDescription?.description,
                        Constants.KEY_TYPE to sessionDescription?.type
                    )
                  setValueOnFirestoreDB(value,meetingId)
                    Log.e(TAG, "onSetSuccess")
                }

                override fun onCreateFailure(p0: String?) {
                    Log.e(TAG, "onCreateFailure: $p0")
                }

                override fun onSetFailure(p0: String?) {
                    Log.e(TAG, "onSetFailure: $p0")
                }

            }, sessionDescription)
            sdpObserver.onCreateSuccess(sessionDescription)
        }

        override fun onSetFailure(p0: String?) {
            Log.e(TAG, "onSetFailure: $p0")
        }

        override fun onCreateFailure(p0: String?) {
            Log.e(TAG, "onCreateFailure: $p0")
        }
    }

    fun setValueOnFirestoreDB(value: HashMap<String, String>, meetingId: String) {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingId)
            .set(value)
            .addOnSuccessListener { Log.e(TAG, "DocumentSnapshot added") }
            .addOnFailureListener { e -> Log.e(TAG, "Error adding document", e) }
    }

    private fun PeerConnection.sdpObserver() = object : SdpObserver {
        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
            Log.e(TAG, "onCreateSuccessRemoteSession: Description $sessionDescription")
        }

        override fun onSetSuccess() {
            Log.e(TAG, "onSetSuccessRemoteSession")
        }

        override fun onSetFailure(p0: String?) {
            Log.e(TAG, "onSetFailure: $p0")
        }

        override fun onCreateFailure(p0: String?) {
            Log.e(TAG, "onCreateFailure: $p0")
        }
    }

    fun call(sdpObserver: SdpObserver, meetingID: String) =
        peerConnection?.call(sdpObserver, meetingID)

    fun answer(sdpObserver: SdpObserver, meetingID: String) =
        peerConnection?.answer(sdpObserver, meetingID)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        remoteSessionDescription = sessionDescription
        peerConnection?.run {
            setRemoteDescription(sdpObserver(), sessionDescription)
        }
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun endCall(meetingId: String) {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingId)
            .collection(CollectionEnum.CANDIDATES.value)
            .get()
            .addOnSuccessListener {
                val iceCandidateArray: MutableList<IceCandidate> = mutableListOf()
                for (dataSnapshot in it) {
                    if ((dataSnapshot.contains(Constants.KEY_TYPE)) &&
                        (dataSnapshot[Constants.KEY_TYPE] == Constants.TYPE_OFFER_CANDIDATE ||
                                dataSnapshot[Constants.KEY_TYPE] == Constants.TYPE_ANSWER_CANDIDATE)
                    ) {
                        iceCandidateArray.add(
                            Constants.fillIceCandidate(dataSnapshot)
                        )
                    }
                }
                peerConnection?.removeIceCandidates(iceCandidateArray.toTypedArray())
            }

        val endCall = hashMapOf(Constants.KEY_TYPE to END_CALL_SNAIL)
        setValueOnFirestoreDB(endCall,meetingId)
        peerConnection?.close()
    }

    fun enableVideo(videoEnabled:Boolean){
        localVideoTrack?.setEnabled(videoEnabled)
    }
    fun enableAudio(audioEnabled:Boolean){
        localAudioTrack?.setEnabled(audioEnabled)
    }


    //todo- try catch
    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run{
            deviceNames.find {
                isFrontFacing(it)
            }?.let{
                createCapturer(it,null)
            }?:throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run{
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext,null)
    }

    fun startLocalVideoCapture(localVideoOutput:SurfaceViewRenderer){
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource)
        localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack?.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    fun switchCamera(){
        videoCapturer.switchCamera(null)
    }
}