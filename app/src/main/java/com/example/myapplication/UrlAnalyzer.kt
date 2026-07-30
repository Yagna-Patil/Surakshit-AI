package com.example.myapplication

import java.util.regex.Pattern

object UrlAnalyzer {

    private val SUSPICIOUS_DOMAINS = listOf(
        "ngrok", "bit.ly", "tinyurl", "cutt.ly", ".xyz", ".top", ".club", ".work",
        ".online", ".site", ".info", ".apk", "free-claim", "login-update", "verify-bank"
    )

    fun containsUrl(text: String): Boolean {
        val urlPattern = Pattern.compile(
            "(https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?",
            Pattern.CASE_INSENSITIVE
        )
        return urlPattern.matcher(text).find() || text.lowercase().contains(".apk")
    }

    fun isDangerousUrl(urlText: String): Boolean {
        val lower = urlText.lowercase()
        return SUSPICIOUS_DOMAINS.any { lower.contains(it) } || lower.contains(".apk")
    }
}