package com.example.videocall

enum class SDPTypeEnum(val value: String) {
    ANSWER("Answer"),
    END_CALL("End Call"),
    OFFER("Offer")
}
enum class TypeEnum(val value: String) {
    ANSWER("ANSWER"),
    END_CALL("END_CALL"),
    OFFER("OFFER"),
    TYPE_OFFER_CANDIDATE("offerCandidate"),
    TYPE_ANSWER_CANDIDATE("answerCandidate")
}