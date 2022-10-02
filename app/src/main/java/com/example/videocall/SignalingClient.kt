package com.example.videocall

import android.util.Log
import com.example.videocall.Constants.KEY_TYPE
import com.example.videocall.Constants.SDP
import com.example.videocall.Constants.TYPE_ANSWER_CANDIDATE
import com.example.videocall.Constants.TYPE_OFFER_CANDIDATE
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import kotlin.coroutines.CoroutineContext

class SignalingClient(
    private val meetingID: String,
    private val listener: SignalingClientListener,
) : CoroutineScope {

    private val job = Job()

    companion object {
        val TAG = this::class.simpleName
    }

    init {
        connect()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    val network = Firebase.firestore
    var sdpType: String? = null


    fun sendIceCandidate(candidate: IceCandidate?, isJoin: Boolean) = runBlocking {
        val type = when {
            isJoin -> TYPE_ANSWER_CANDIDATE
            else -> TYPE_OFFER_CANDIDATE
        }
        val candidateConstant = hashMapOf(
            "serverUrl" to candidate?.serverUrl,
            "sdpMid" to candidate?.sdpMid,
            "sdpMLineIndex" to candidate?.sdpMLineIndex,
            "sdpCandidate" to candidate?.sdp,
            "type" to type
        )

        network.collection(CollectionEnum.CALLS.value)
            .document(meetingID)
            .collection(CollectionEnum.CANDIDATES.value)
            .document(type)
            .set(candidateConstant)
            .addOnSuccessListener {
                Log.e(TAG, "sendIceCandidate: Success")
            }.addOnFailureListener {
                Log.e(TAG, "sendIceCandidate: Error $it")
            }
    }

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

    private fun offerAnswerEndObserver() {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingID)
            .addSnapshotListener { snapshot, error ->
                if (checkError(error)) return@addSnapshotListener
                val data = snapshot?.data
                if (snapshot != null && snapshot.exists() && data?.containsKey(KEY_TYPE) != null && data.containsKey(
                        KEY_TYPE
                    )
                ) {
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
            SDPTypeEnum.OFFER.value -> {
                listener.onOfferReceived(
                    SessionDescription(
                        SessionDescription.Type.OFFER,
                        description
                    )
                )
                sdpType = SDPTypeEnum.OFFER.value
            }
            SDPTypeEnum.ANSWER.value -> {
                listener.onAnswerReceived(
                    SessionDescription(
                        SessionDescription.Type.ANSWER,
                        description
                    )
                )
                sdpType = SDPTypeEnum.ANSWER.value
            }
            SDPTypeEnum.END_CALL.value -> {
                if (!Constants.isIntiatedNow) {
                    listener.onCallEnded()
                    sdpType = SDPTypeEnum.END_CALL.value
                }
            }
        }
        Log.d(TAG, "snapshot data:$data")
    }


    private fun candidateAddedToTheCallObserver() {
        network.collection(CollectionEnum.CALLS.value)
            .document(meetingID)
            .collection(CollectionEnum.CANDIDATES.value)
            .addSnapshotListener { querysnapshot, error ->
                if (checkError(error)) return@addSnapshotListener
                if (querysnapshot != null && !querysnapshot.isEmpty) {
                    for (dataSnapshot in querysnapshot) {
                        val data = dataSnapshot.data
                        val type = data.getValue(KEY_TYPE).toString()
                        if (dataSnapshot != null && dataSnapshot.exists() && data.containsKey(
                                KEY_TYPE
                            )
                        ) candidateAddedToTheCall(type, data)

                        Log.e(TAG, "candidateQuery: $dataSnapshot")
                    }
                }
            }
    }

    private fun candidateAddedToTheCall(
        type: String,
        data: Map<String, Any>
    ) {
        when {
            sdpType == SDPTypeEnum.OFFER.value && type == TYPE_OFFER_CANDIDATE -> {
                listener.onIceCandidateReceived(
                    Constants.fillIceCandidate(data)
                )
            }
            sdpType == SDPTypeEnum.ANSWER.value && type == TYPE_ANSWER_CANDIDATE -> {
                Constants.fillIceCandidate(data)
            }
        }
    }

    fun destroy() {
        job.complete()
    }

}