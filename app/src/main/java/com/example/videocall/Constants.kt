package com.example.videocall

import com.google.firebase.firestore.QueryDocumentSnapshot
import org.webrtc.IceCandidate

object Constants {
        var isCallEnded: Boolean = false
        var isIntiatedNow : Boolean = true
        const val SERVER_URL = "serverUrl"
        const val KEY_TYPE = "type"
        const val SDP = "sdp"
        const val SDP_CANDIDATE = "sdpCandidate"
        const val SDP_MID = "sdpMid"
        const val SDP_LINE_INDEX = "sdpMLineIndex"
        const val SCENARIO_TAG_LOG= "TT2: "

        fun fillIceCandidate(data: Map<String, Any>) =
                IceCandidate(
                        data[SDP_MID].toString(), Math.toIntExact(
                                data[SDP_LINE_INDEX] as Long
                        ), data[SDP_CANDIDATE].toString()
                )

        fun fillIceCandidate(data: QueryDocumentSnapshot): IceCandidate =
                IceCandidate(
                        data[SDP_MID].toString(), Math.toIntExact(
                                data[SDP_LINE_INDEX] as Long
                        ), data[SDP_CANDIDATE].toString()
                )
}