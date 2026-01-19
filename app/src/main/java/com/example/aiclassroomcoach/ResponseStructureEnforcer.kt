package com.example.aiclassroomcoach

object ResponseStructureEnforcer {
    private val requiredSections = listOf(
        "A) 60-Second Stabilize",
        "B) Teach the Concept (2 minutes)",
        "C) Check for Understanding (30 seconds)",
        "D) Extend for Fast Finishers",
        "E) If It Still Fails"
    )

    fun enforce(rawText: String): String {
        val normalized = rawText.trim()
        val containsAll = requiredSections.all { normalized.contains(it) }
        if (containsAll) {
            return normalized
        }

        return buildString {
            appendLine("A) 60-Second Stabilize")
            appendLine("1) ${fallbackLine(normalized, 1)}")
            appendLine("2) Calm, short reset with a clear instruction.")
            appendLine()
            appendLine("B) Teach the Concept (2 minutes)")
            appendLine("1) Use the teacher's example. Keep sentences short.")
            appendLine()
            appendLine("C) Check for Understanding (30 seconds)")
            appendLine("1) Ask: What is the first step we do?")
            appendLine()
            appendLine("D) Extend for Fast Finishers")
            appendLine("1) Ask them to write two new examples in notebooks.")
            appendLine()
            appendLine("E) If It Still Fails")
            appendLine("1) Pause, breathe, and restate the key point in one sentence.")
        }
    }

    private fun fallbackLine(normalized: String, index: Int): String {
        return if (normalized.isNotBlank()) {
            "Step ${index}: ${normalized.take(120)}"
        } else {
            "Step ${index}: Ask for eyes forward and count down from three."
        }
    }
}
