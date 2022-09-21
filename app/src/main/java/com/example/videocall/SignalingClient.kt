package com.example.videocall

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import kotlin.coroutines.CoroutineContext

class SignalingClient(
    private val meetingID: String,
    private val listener: SignalingClientListener,
) : CoroutineScope {

    private val job = Job()

    companion object {
        val TAG = this::class.simpleName
        const val KEY_TYPE = "type"
        const val SDP = "sdp"
        const val SDP_CANDIDATE = "sdpCandidate"
        const val SDP_MID = "sdpMid"
        const val SDP_LINE_INDEX = "sdpMLineIndex"

    }

    init {
        connect()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    val network = Firebase.firestore
    var sdpType: String? = null

    private fun connect() = launch {
        network.enableNetwork().addOnCanceledListener {
            listener.onConnectionEstablished()
        }
        //todo sendData

        try {
            offerAnswerEndObserver()
            candidateAddedToTheCallObserver()
        } catch (cause: Throwable) {
        }
    }

    private fun candidateAddedToTheCallObserver() {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingID)
            .collection(CollectionEnum.CANDIDATES.value)
            .addSnapshotListener { querysnapshot, error ->
                if (checkError(error)) return@addSnapshotListener

                //                        condidateAddedToTheCall(data)
            }
    }

    private fun offerAnswerEndObserver() {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingID)
            .addSnapshotListener { snapshot, error ->
                if (checkError(error)) return@addSnapshotListener
                val data = snapshot?.data
                if (snapshot != null && snapshot.exists() && data?.containsKey(KEY_TYPE) != null) {
                    offerAnswerEnd(data)
                }
            }
    }

    private fun checkError(error: FirebaseFirestoreException?): Boolean {
        if (error != null) {
            Log.w(TAG, "listen:error", error)
            return true
        }
        return false
    }

    private fun offerAnswerEnd(data: MutableMap<String, Any>) {
        val type = data.getValue(KEY_TYPE).toString()
        val description = data[SDP].toString()
        when (type) {
            SDPType.OFFER.value -> {
                listener.onOfferReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        description
                    )
                )
                sdpType = SDPType.OFFER.value
            }
            SDPType.ANSWER.value -> {
                listener.onAnswerReceived(
                    SessionDescription(
                        SessionDescription.Type.ANSWER,
                        description
                    )
                )
                sdpType = SDPType.ANSWER.value
            }
            SDPType.END_CALL.value -> {
                if (!Constants.isIntiatedNow) {
                    listener.onCallEnded()
                    sdpType = SDPType.END_CALL.value
                }
            }
        }
        Log.d(TAG,"snapshot data:$data")
    }

    private fun candidateAddedToTheCall() {
        TODO("Not yet implemented")
    }

    fun destroy() {
        job.complete()
    }

}