package com.example.videocall

import android.util.Log
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
        const val KEY_TYPE = "type"
        const val SDP = "sdp"
        const val SDP_CANDIDATE = "sdpCandidate"
        const val SDP_MID = "sdpMid"
        const val SDP_LINE_INDEX = "sdpMLineIndex"
        const val TYPE_OFFER_CANDIDATE = "offerCandidate"
        const val TYPE_ANSWER_CANDIDATE = "answerCandidate"

    }

    init {
        connect()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    val network = Firebase.firestore
    var sdpType: String? = null


    fun sendCandidate(candidate: IceCandidate?, isJoin: Boolean) = runBlocking {
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
            sdpType == SDPType.OFFER.value && type == TYPE_OFFER_CANDIDATE -> {
                listener.onIceCandidateReceived(
                    fillIceCandidate(data)
                )
            }
            sdpType == SDPType.ANSWER.value && type == TYPE_ANSWER_CANDIDATE -> {
                fillIceCandidate(data)
            }
        }
    }

    private fun fillIceCandidate(data: Map<String, Any>) =
        IceCandidate(
            data[SDP_MID].toString(), Math.toIntExact(
                data[SDP_LINE_INDEX] as Long
            ), data[SDP_CANDIDATE].toString()
        )

    fun destroy() {
        job.complete()
    }

}