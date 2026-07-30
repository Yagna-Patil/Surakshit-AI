package com.example.myapplication

object CallAnalyzer {

    private val DIGITAL_ARREST_KEYWORDS = listOf(
        "cbi", "police", "cyber cell", "digital arrest", "warrant",
        "illegal package", "customs", "court order", "jail", "trai", "supreme court"
    )

    private val BILL_SCAM_KEYWORDS = listOf(
        "electricity", "power disconnect", "light bill", "bijli bill", "officer demand"
    )

    fun isDigitalArrestCall(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return DIGITAL_ARREST_KEYWORDS.any { lower.contains(it) }
    }

    fun isElectricityCall(transcript: String): Boolean {
        val lower = transcript.lowercase()
        return BILL_SCAM_KEYWORDS.any { lower.contains(it) }
    }
}