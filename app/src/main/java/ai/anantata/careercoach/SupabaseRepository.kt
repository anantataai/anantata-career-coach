package ai.anantata.careercoach

import ai.anantata.careercoach.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SupabaseRepository {
    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
    }

    private val baseUrl = BuildConfig.SUPABASE_URL
    private val apiKey = BuildConfig.SUPABASE_KEY

    suspend fun createConversation(conversationId: String) {
        try {
            supabase.from("conversations").insert(
                mapOf(
                    "id" to conversationId,
                    "created_at" to Clock.System.now().toString()
                )
            )
        } catch (e: Exception) {
            println("Error creating conversation: ${e.message}")
        }
    }

    suspend fun saveMessage(
        conversationId: String,
        role: String,
        content: String
    ) {
        try {
            val truncatedContent = if (content.length > 5000) {
                content.take(5000) + "\n\n[Повідомлення обрізано через довжину]"
            } else {
                content
            }

            supabase.from("messages").insert(
                mapOf(
                    "conversation_id" to conversationId,
                    "role" to role,
                    "content" to truncatedContent,
                    "created_at" to Clock.System.now().toString()
                )
            )
        } catch (e: Exception) {
            println("Error saving message: ${e.message}")
        }
    }

    // ============================================
    // ASSESSMENT RESULTS (HTTP напряму)
    // ============================================

    suspend fun saveAssessmentResult(
        userId: String,
        matchScore: Int,
        gapAnalysis: String,
        actionPlan: String,
        answers: Map<Int, String>
    ) = withContext(Dispatchers.IO) {
        try {
            // Конвертуємо answers в JSON
            val answersJson = JSONObject().apply {
                answers.forEach { (key, value) ->
                    put(key.toString(), value)
                }
            }.toString()

            // Створюємо JSON для запиту
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("match_score", matchScore)
                put("gap_analysis", gapAnalysis)
                put("action_plan", actionPlan)
                put("answers", answersJson)
                put("created_at", Clock.System.now().toString())
            }.toString()

            // HTTP POST запит
            val url = URL("$baseUrl/rest/v1/assessment_results")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == 201 || responseCode == 200) {
                println("✅ Assessment result saved for user: $userId")
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                println("❌ Error saving (HTTP $responseCode): $error")
            }

            connection.disconnect()

        } catch (e: Exception) {
            println("❌ Error saving assessment result: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun getAssessmentHistory(userId: String): List<AssessmentHistoryItem> = withContext(Dispatchers.IO) {
        try {
            // HTTP GET запит - тепер включає answers
            val url = URL("$baseUrl/rest/v1/assessment_results?user_id=eq.$userId&select=id,user_id,match_score,gap_analysis,action_plan,answers,created_at&order=created_at.desc")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            // Парсимо JSON
            val jsonArray = JSONArray(response)
            val items = mutableListOf<AssessmentHistoryItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    AssessmentHistoryItem(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        matchScore = obj.getInt("match_score"),
                        gapAnalysis = obj.getString("gap_analysis"),
                        actionPlan = obj.getString("action_plan"),
                        answers = obj.optString("answers", "{}"),
                        createdAt = obj.getString("created_at")
                    )
                )
            }

            println("✅ Fetched ${items.size} assessment results")
            items

        } catch (e: Exception) {
            println("❌ Error fetching assessment history: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun deleteAssessment(assessmentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/assessment_results?id=eq.$assessmentId")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "DELETE"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode in 200..299) {
                println("✅ Assessment deleted: $assessmentId")
                true
            } else {
                println("❌ Error deleting (HTTP $responseCode)")
                false
            }
        } catch (e: Exception) {
            println("❌ Error deleting assessment: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteAllUserData(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/assessment_results?user_id=eq.$userId")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "DELETE"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            if (responseCode in 200..299) {
                println("✅ All user data deleted for: $userId")
                true
            } else {
                println("❌ Error deleting all data (HTTP $responseCode)")
                false
            }
        } catch (e: Exception) {
            println("❌ Error deleting user data: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

// ============================================
// DATA CLASS
// ============================================

data class AssessmentHistoryItem(
    val id: String,
    val userId: String,
    val matchScore: Int,
    val gapAnalysis: String,
    val actionPlan: String,
    val answers: String,
    val createdAt: String
)