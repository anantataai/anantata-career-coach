package ai.anantata.careercoach

/**
 * Data class для збереженого результату assessment'у
 * Використовується для передачі даних з історії на екран перегляду
 */
data class SavedAssessmentResult(
    val id: String,
    val matchScore: Int,
    val gapAnalysis: String,
    val actionPlan: String,
    val answers: String,
    val createdAt: String
)