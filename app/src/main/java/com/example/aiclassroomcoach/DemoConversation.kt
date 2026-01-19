package com.example.aiclassroomcoach

object DemoConversation {
    val teacherPrompt = "Class is noisy during fractions lesson. Need quick steps to calm and explain equivalent fractions."

    val aiResponseExample = """
A) 60-Second Stabilize
1) Raise your hand and silently count down from three.
2) Say: "Eyes on me. Pencils down." Wait for quiet.
3) Point to one row: "Row one, show me ready." Repeat for others.

B) Teach the Concept (2 minutes)
1) Draw one circle, split into 2. Shade one half.
2) Draw another circle, split into 4. Shade two parts.
3) Say: "One out of two is the same as two out of four. Same amount." Use their example.

C) Check for Understanding (30 seconds)
1) Ask: "If I shade 3 out of 6, is it the same as 1 out of 2?"
2) Ask: "Show fingers: same or not same?"

D) Extend for Fast Finishers
1) Ask them to draw two shapes and find matching fractions in notebooks.

E) If It Still Fails
1) Reset: "We only need one idea: same size shaded." Redraw with bigger shapes.
""".trimIndent()
}
