package com.example.videocall

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.lang.reflect.Type

class MainActivity : AppCompatActivity() {

    private lateinit var startMeeting: MaterialButton
    private lateinit var meetingId: TextView
    private lateinit var joinMeeting: MaterialButton

    private val network = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUiViews()
        initUiComponents()
        initUiListener()
    }

    private fun initUiViews() {
        startMeeting = findViewById(R.id.start_meeting)
        meetingId = findViewById(R.id.meeting_id)
        joinMeeting = findViewById(R.id.join_meeting)
    }

    private fun initUiComponents() {
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true
    }

    private fun initUiListener() {
        startMeetingListener()
        joinMeetingListener()
    }

    private fun startMeetingListener() {
        startMeeting.setOnClickListener {
            if (meetingId.text.toString().trim().isNullOrEmpty())
                meetingId.error = getString(R.string.meetingIdError)
            else {
                network.collection(CollectionEnum.CALLS.value)
                    .document(meetingId.text.toString())
                    .get()
                    .addOnSuccessListener { documentSnapshot ->
                        if (documentSnapshot[Constants.KEY_TYPE] == TypeEnum.OFFER.value
                            || documentSnapshot[Constants.KEY_TYPE] == TypeEnum.ANSWER.value
                            || documentSnapshot[Constants.KEY_TYPE] == TypeEnum.END_CALL.value
                        )
                            meetingId.error = getString(R.string.newMeetingIdError)
                        else {
                            val intent = Intent(this, RTCActivity::class.java)
                            intent.putExtra(
                                RTCActivity.ARGUMENT_MEETING_ID,
                                meetingId.text.toString()
                            )
                            intent.putExtra(RTCActivity.ARGUMENT_IS_JOIN, false)
                            startActivity(intent)
                        }
                    }.addOnFailureListener {
                        meetingId.error = it.cause?.message
                    }
            }
        }
    }

    private fun joinMeetingListener() {
        joinMeeting.setOnClickListener {
            if (meetingId.text.toString().trim().isNullOrEmpty())
                meetingId.error = getString(R.string.meetingIdError)
            else {
                val intent = Intent(this, RTCActivity::class.java)
                intent.putExtra(RTCActivity.ARGUMENT_MEETING_ID, meetingId.text.toString())
                intent.putExtra(RTCActivity.ARGUMENT_IS_JOIN, true)
                startActivity(intent)
            }
        }
    }
}