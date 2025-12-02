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
import java.util.UUID

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

    // ════════════════════════════════════════════════════════════════
    // ASSESSMENT RESULTS (існуючий код)
    // ════════════════════════════════════════════════════════════════

    suspend fun saveAssessmentResult(
        userId: String,
        matchScore: Int,
        gapAnalysis: String,
        actionPlan: String,
        answers: Map<Int, String>
    ) = withContext(Dispatchers.IO) {
        try {
            val answersJson = JSONObject().apply {
                answers.forEach { (key, value) ->
                    put(key.toString(), value)
                }
            }.toString()

            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("match_score", matchScore)
                put("gap_analysis", gapAnalysis)
                put("action_plan", actionPlan)
                put("answers", answersJson)
                put("created_at", Clock.System.now().toString())
            }.toString()

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
            val url = URL("$baseUrl/rest/v1/assessment_results?user_id=eq.$userId&select=id,user_id,match_score,gap_analysis,action_plan,answers,created_at&order=created_at.desc")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

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

    // ════════════════════════════════════════════════════════════════
    // GOALS — CRUD для цілей
    // ════════════════════════════════════════════════════════════════

    suspend fun createGoal(
        userId: String,
        title: String,
        targetSalary: String,
        assessmentId: String? = null,
        isPrimary: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            val goalId = UUID.randomUUID().toString()

            val requestBody = JSONObject().apply {
                put("id", goalId)
                put("user_id", userId)
                put("title", title)
                put("target_salary", targetSalary)
                if (assessmentId != null) {
                    put("assessment_id", assessmentId)
                }
                put("is_primary", isPrimary)
                put("status", "active")
                put("created_at", Clock.System.now().toString())
                put("updated_at", Clock.System.now().toString())
            }.toString()

            val url = URL("$baseUrl/rest/v1/goals")
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
            connection.disconnect()

            if (responseCode in 200..299) {
                println("✅ Goal created: $goalId")
                goalId
            } else {
                println("❌ Error creating goal (HTTP $responseCode)")
                null
            }
        } catch (e: Exception) {
            println("❌ Error creating goal: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    suspend fun getGoals(userId: String): List<GoalItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?user_id=eq.$userId&order=created_at.desc")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<GoalItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    GoalItem(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        assessmentId = obj.optString("assessment_id", null),
                        title = obj.getString("title"),
                        targetSalary = obj.optString("target_salary", ""),
                        isPrimary = obj.getBoolean("is_primary"),
                        status = obj.getString("status"),
                        createdAt = obj.getString("created_at"),
                        updatedAt = obj.getString("updated_at")
                    )
                )
            }

            println("✅ Fetched ${items.size} goals")
            items

        } catch (e: Exception) {
            println("❌ Error fetching goals: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getGoalById(goalId: String): GoalItem? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?id=eq.$goalId&limit=1")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                GoalItem(
                    id = obj.getString("id"),
                    userId = obj.getString("user_id"),
                    assessmentId = obj.optString("assessment_id", null),
                    title = obj.getString("title"),
                    targetSalary = obj.optString("target_salary", ""),
                    isPrimary = obj.getBoolean("is_primary"),
                    status = obj.getString("status"),
                    createdAt = obj.getString("created_at"),
                    updatedAt = obj.getString("updated_at")
                )
            } else {
                println("⚠️ Goal not found: $goalId")
                null
            }
        } catch (e: Exception) {
            println("❌ Error fetching goal by id: ${e.message}")
            null
        }
    }

    suspend fun getPrimaryGoal(userId: String): GoalItem? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?user_id=eq.$userId&is_primary=eq.true&limit=1")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                GoalItem(
                    id = obj.getString("id"),
                    userId = obj.getString("user_id"),
                    assessmentId = obj.optString("assessment_id", null),
                    title = obj.getString("title"),
                    targetSalary = obj.optString("target_salary", ""),
                    isPrimary = obj.getBoolean("is_primary"),
                    status = obj.getString("status"),
                    createdAt = obj.getString("created_at"),
                    updatedAt = obj.getString("updated_at")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Error fetching primary goal: ${e.message}")
            null
        }
    }

    suspend fun getGoalsCount(userId: String): Int = withContext(Dispatchers.IO) {
        try {
            val goals = getGoals(userId)
            goals.size
        } catch (e: Exception) {
            0
        }
    }

    suspend fun resetAllPrimaryGoals(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?user_id=eq.$userId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use { os ->
                os.write("""{"is_primary": false}""".toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Reset is_primary for ALL goals of user: $userId (HTTP $responseCode)")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error resetting primary goals: ${e.message}")
            false
        }
    }

    suspend fun setPrimaryGoal(userId: String, goalId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resetAllPrimaryGoals(userId)

            val setUrl = URL("$baseUrl/rest/v1/goals?id=eq.$goalId")
            val setConnection = setUrl.openConnection() as HttpURLConnection
            setConnection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            setConnection.outputStream.use { os ->
                os.write("""{"is_primary": true, "updated_at": "${Clock.System.now()}"}""".toByteArray())
            }
            val responseCode = setConnection.responseCode
            setConnection.disconnect()

            println("✅ Primary goal set: $goalId")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error setting primary goal: ${e.message}")
            false
        }
    }

    suspend fun updateGoalStatus(goalId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?id=eq.$goalId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use { os ->
                os.write("""{"status": "$status", "updated_at": "${Clock.System.now()}"}""".toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Goal status updated: $goalId -> $status")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error updating goal status: ${e.message}")
            false
        }
    }

    suspend fun deleteGoal(goalId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/goals?id=eq.$goalId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "DELETE"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Goal deleted: $goalId")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error deleting goal: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: DIRECTIONS — 10 напрямків розвитку
    // Таблиця: strategic_steps (перевикористовуємо)
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає 10 напрямків для блоку
     * @return Map<Int, String> - номер напрямку -> ID напрямку
     */
    suspend fun saveDirections(
        goalId: String,
        directions: List<GeneratedDirection>,
        blockNumber: Int = 1
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        try {
            println("💾 saveDirections: ${directions.size} directions for goal $goalId, block $blockNumber")

            val directionIdMap = mutableMapOf<Int, String>()
            val directionsArray = JSONArray()

            directions.forEach { direction ->
                val directionId = UUID.randomUUID().toString()
                directionIdMap[direction.number] = directionId

                directionsArray.put(JSONObject().apply {
                    put("id", directionId)
                    put("goal_id", goalId)
                    put("step_number", direction.number)
                    put("title", direction.title)
                    put("description", direction.description)
                    put("timeframe", "Блок $blockNumber")
                    put("start_week", blockNumber)
                    put("end_week", blockNumber)
                    put("status", "pending")
                    put("created_at", Clock.System.now().toString())
                    put("updated_at", Clock.System.now().toString())
                })
            }

            val url = URL("$baseUrl/rest/v1/strategic_steps")
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
                os.write(directionsArray.toString().toByteArray())
            }
            val responseCode = connection.responseCode

            val errorResponse = if (responseCode !in 200..299) {
                connection.errorStream?.bufferedReader()?.readText() ?: "No error details"
            } else null

            connection.disconnect()

            if (responseCode in 200..299) {
                println("✅ Directions saved: ${directions.size} for goal $goalId, block $blockNumber")
                println("✅ Direction ID map: $directionIdMap")
                directionIdMap
            } else {
                println("❌ Error saving directions (HTTP $responseCode): $errorResponse")
                emptyMap()
            }
        } catch (e: Exception) {
            println("❌ Error saving directions: ${e.message}")
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Отримує напрямки для блоку
     */
    suspend fun getDirections(goalId: String, blockNumber: Int = 1): List<DirectionItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/strategic_steps?goal_id=eq.$goalId&start_week=eq.$blockNumber&order=step_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<DirectionItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    DirectionItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        directionNumber = obj.getInt("step_number"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        status = obj.getString("status"),
                        blockNumber = obj.optInt("start_week", 1)
                    )
                )
            }

            println("✅ Fetched ${items.size} directions for block $blockNumber")
            items
        } catch (e: Exception) {
            println("❌ Error fetching directions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отримує всі напрямки для цілі (всіх блоків)
     */
    suspend fun getAllDirections(goalId: String): List<DirectionItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/strategic_steps?goal_id=eq.$goalId&order=start_week.asc,step_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<DirectionItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    DirectionItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        directionNumber = obj.getInt("step_number"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        status = obj.getString("status"),
                        blockNumber = obj.optInt("start_week", 1)
                    )
                )
            }

            items
        } catch (e: Exception) {
            println("❌ Error fetching all directions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отримує map номер напрямку -> ID напрямку
     */
    suspend fun getDirectionIdMap(goalId: String, blockNumber: Int = 1): Map<Int, String> = withContext(Dispatchers.IO) {
        try {
            val directions = getDirections(goalId, blockNumber)
            directions.associate { it.directionNumber to it.id }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Оновлює статус напрямку
     */
    suspend fun updateDirectionStatus(directionId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/strategic_steps?id=eq.$directionId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use { os ->
                os.write("""{"status": "$status", "updated_at": "${Clock.System.now()}"}""".toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error updating direction status: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: STEPS — 100 кроків (10 на кожен напрямок)
    // Таблиця: weekly_tasks (перевикористовуємо)
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає 100 кроків для блоку
     * @param steps - список з 100 кроків
     * @param directionIdMap - map номер напрямку -> ID напрямку
     */
    suspend fun saveSteps(
        goalId: String,
        steps: List<GeneratedStep>,
        directionIdMap: Map<Int, String>,
        blockNumber: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            println("💾 saveSteps called: ${steps.size} steps, directionIdMap size: ${directionIdMap.size}")

            if (steps.isEmpty()) {
                println("⚠️ saveSteps: No steps to save!")
                return@withContext false
            }

            println("💾 First step: #${steps.first().number} - ${steps.first().title}")
            println("💾 Last step: #${steps.last().number} - ${steps.last().title}")

            val stepsArray = JSONArray()

            steps.forEach { step ->
                val directionId = directionIdMap[step.directionNumber]

                if (directionId == null) {
                    println("⚠️ No direction ID for step ${step.number} (direction ${step.directionNumber})")
                }

                stepsArray.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("goal_id", goalId)
                    put("week_number", blockNumber)
                    put("task_number", step.number)
                    put("title", step.title)
                    put("description", step.description)
                    put("status", "pending")
                    if (directionId != null) {
                        put("strategic_step_id", directionId)
                    }
                    put("created_at", Clock.System.now().toString())
                })
            }

            println("💾 JSON array prepared with ${stepsArray.length()} items")
            println("💾 JSON size: ${stepsArray.toString().length} bytes")

            val url = URL("$baseUrl/rest/v1/weekly_tasks")
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
                os.write(stepsArray.toString().toByteArray())
            }

            val responseCode = connection.responseCode

            val errorResponse = if (responseCode !in 200..299) {
                connection.errorStream?.bufferedReader()?.readText() ?: "No error details"
            } else null

            connection.disconnect()

            println("💾 saveSteps HTTP response: $responseCode")
            if (errorResponse != null) {
                println("❌ saveSteps error: $errorResponse")
            }

            if (responseCode in 200..299) {
                println("✅ ${steps.size} steps saved for goal $goalId, block $blockNumber")
                true
            } else {
                println("❌ Error saving steps (HTTP $responseCode): $errorResponse")
                false
            }
        } catch (e: Exception) {
            println("❌ Error saving steps: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Отримує кроки для блоку
     */
    suspend fun getSteps(goalId: String, blockNumber: Int = 1): List<StepItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/weekly_tasks?goal_id=eq.$goalId&week_number=eq.$blockNumber&order=task_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<StepItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val stepNumber = obj.getInt("task_number")
                val directionNumber = ((stepNumber - 1) / 10) + 1
                val localNumber = ((stepNumber - 1) % 10) + 1

                // 🆕 Завантажуємо опис з БД
                val description = obj.optString("description", "")
                // Якщо опис довший за 100 символів - це детальний опис
                val detailedDesc = if (description.length > 100) description else null

                items.add(
                    StepItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        directionId = obj.optString("strategic_step_id", ""),
                        blockNumber = obj.getInt("week_number"),
                        stepNumber = stepNumber,
                        localNumber = localNumber,
                        title = obj.getString("title"),
                        description = description,
                        detailedDescription = detailedDesc, // 🆕 Завантажуємо з БД
                        status = obj.getString("status")
                    )
                )
            }

            println("✅ Fetched ${items.size} steps for block $blockNumber")
            items
        } catch (e: Exception) {
            println("❌ Error fetching steps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отримує кроки для конкретного напрямку
     */
    suspend fun getStepsForDirection(goalId: String, directionId: String, blockNumber: Int = 1): List<StepItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/weekly_tasks?goal_id=eq.$goalId&week_number=eq.$blockNumber&strategic_step_id=eq.$directionId&order=task_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<StepItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val stepNumber = obj.getInt("task_number")
                val directionNumber = ((stepNumber - 1) / 10) + 1
                val localNumber = ((stepNumber - 1) % 10) + 1

                // 🆕 Завантажуємо опис з БД
                val description = obj.optString("description", "")
                val detailedDesc = if (description.length > 100) description else null

                items.add(
                    StepItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        directionId = obj.optString("strategic_step_id", ""),
                        blockNumber = obj.getInt("week_number"),
                        stepNumber = stepNumber,
                        localNumber = localNumber,
                        title = obj.getString("title"),
                        description = description,
                        detailedDescription = detailedDesc,
                        status = obj.getString("status")
                    )
                )
            }

            items
        } catch (e: Exception) {
            println("❌ Error fetching steps for direction: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отримує всі кроки для цілі (всіх блоків)
     */
    suspend fun getAllSteps(goalId: String): List<StepItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/weekly_tasks?goal_id=eq.$goalId&order=week_number.asc,task_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<StepItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val stepNumber = obj.getInt("task_number")
                val localNumber = ((stepNumber - 1) % 10) + 1

                // 🆕 Завантажуємо опис з БД
                val description = obj.optString("description", "")
                val detailedDesc = if (description.length > 100) description else null

                items.add(
                    StepItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        directionId = obj.optString("strategic_step_id", ""),
                        blockNumber = obj.getInt("week_number"),
                        stepNumber = stepNumber,
                        localNumber = localNumber,
                        title = obj.getString("title"),
                        description = description,
                        detailedDescription = detailedDesc,
                        status = obj.getString("status")
                    )
                )
            }

            println("✅ Fetched ${items.size} total steps for goal $goalId")
            items
        } catch (e: Exception) {
            println("❌ Error fetching all steps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Оновлює статус кроку (pending/done/skipped)
     */
    suspend fun updateStepStatus(stepId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val completedAt = if (status == "done") {
                """, "completed_at": "${Clock.System.now()}""""
            } else {
                ""
            }

            val url = URL("$baseUrl/rest/v1/weekly_tasks?id=eq.$stepId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use { os ->
                os.write("""{"status": "$status"$completedAt}""".toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Step status updated: $stepId -> $status")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error updating step status: ${e.message}")
            false
        }
    }

    /**
     * Зберігає детальний опис кроку (генерується on-demand)
     */
    suspend fun updateStepDetailedDescription(stepId: String, detailedDescription: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val escapedDescription = detailedDescription
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

            val url = URL("$baseUrl/rest/v1/weekly_tasks?id=eq.$stepId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "PATCH"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connection.outputStream.use { os ->
                os.write("""{"description": "$escapedDescription"}""".toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Step detailed description saved: $stepId")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error saving step description: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: DIRECTIONS WITH STEPS — Напрямки з кроками
    // ════════════════════════════════════════════════════════════════

    /**
     * Отримує напрямки з кроками для блоку
     */
    suspend fun getDirectionsWithSteps(goalId: String, blockNumber: Int = 1): List<DirectionWithSteps> = withContext(Dispatchers.IO) {
        try {
            val directions = getDirections(goalId, blockNumber)
            val allSteps = getSteps(goalId, blockNumber)

            directions.map { direction ->
                val directionSteps = allSteps.filter { it.directionId == direction.id }
                val doneCount = directionSteps.count { it.status == "done" }
                val skippedCount = directionSteps.count { it.status == "skipped" }

                val status = when {
                    doneCount == 10 -> "done"
                    doneCount > 0 || skippedCount > 0 -> "in_progress"
                    else -> "pending"
                }

                DirectionWithSteps(
                    direction = direction.copy(status = status),
                    steps = directionSteps,
                    doneCount = doneCount,
                    skippedCount = skippedCount
                )
            }
        } catch (e: Exception) {
            println("❌ Error getting directions with steps: ${e.message}")
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: BLOCK MANAGEMENT — Управління блоками
    // ════════════════════════════════════════════════════════════════

    /**
     * Отримує поточний номер блоку для цілі
     */
    suspend fun getCurrentBlockNumber(goalId: String): Int = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/weekly_tasks?goal_id=eq.$goalId&select=week_number&order=week_number.desc&limit=1")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 0) {
                jsonArray.getJSONObject(0).getInt("week_number")
            } else {
                1
            }
        } catch (e: Exception) {
            println("❌ Error getting current block: ${e.message}")
            1
        }
    }

    /**
     * Отримує максимальний номер блоку для цілі
     */
    suspend fun getMaxBlockNumber(goalId: String): Int = getCurrentBlockNumber(goalId)

    /**
     * Перевіряє чи блок завершений (всі 100 кроків done/skipped)
     */
    suspend fun isBlockComplete(goalId: String, blockNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val steps = getSteps(goalId, blockNumber)
            steps.size == 100 && steps.all { it.status == "done" || it.status == "skipped" }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Отримує статистику блоку
     */
    suspend fun getBlockStats(goalId: String, blockNumber: Int): BlockStats = withContext(Dispatchers.IO) {
        try {
            val steps = getSteps(goalId, blockNumber)
            BlockStats(
                total = steps.size,
                done = steps.count { it.status == "done" },
                skipped = steps.count { it.status == "skipped" },
                pending = steps.count { it.status == "pending" }
            )
        } catch (e: Exception) {
            BlockStats(0, 0, 0, 0)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: PROGRESS CALCULATION — Розрахунок прогресу
    // ════════════════════════════════════════════════════════════════

    /**
     * Розраховує загальний прогрес цілі
     */
    suspend fun calculateGoalProgress(goalId: String): GoalProgress = withContext(Dispatchers.IO) {
        try {
            val allSteps = getAllSteps(goalId)
            val doneSteps = allSteps.count { it.status == "done" }
            val totalSteps = allSteps.size

            val directions = getAllDirections(goalId)
            val doneDirections = directions.count { it.status == "done" }
            val inProgressDirections = directions.count { it.status == "in_progress" }

            val currentBlock = getCurrentBlockNumber(goalId)

            val overallProgress = if (totalSteps > 0) (doneSteps * 100) / totalSteps else 0

            GoalProgress(
                overallPercent = overallProgress,
                tasksCompleted = doneSteps,
                tasksTotal = totalSteps,
                stepsCompleted = doneDirections,
                stepsInProgress = inProgressDirections,
                stepsTotal = directions.size,
                currentWeek = currentBlock
            )
        } catch (e: Exception) {
            println("❌ Error calculating goal progress: ${e.message}")
            GoalProgress(0, 0, 0, 0, 0, 0, 1)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CHAT MESSAGES — CRUD для історії чату
    // ════════════════════════════════════════════════════════════════

    suspend fun saveChatMessage(
        userId: String,
        goalId: String,
        role: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val truncatedContent = if (content.length > 10000) {
                content.take(10000) + "\n\n[Повідомлення обрізано]"
            } else {
                content
            }

            val requestBody = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("goal_id", goalId)
                put("role", role)
                put("content", truncatedContent)
                put("created_at", Clock.System.now().toString())
            }.toString()

            val url = URL("$baseUrl/rest/v1/chat_messages")
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
            connection.disconnect()

            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error saving chat message: ${e.message}")
            false
        }
    }

    suspend fun getChatHistory(goalId: String, limit: Int = 50): List<ChatMessageItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/chat_messages?goal_id=eq.$goalId&order=created_at.asc&limit=$limit")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<ChatMessageItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    ChatMessageItem(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        goalId = obj.getString("goal_id"),
                        role = obj.getString("role"),
                        content = obj.getString("content"),
                        createdAt = obj.getString("created_at")
                    )
                )
            }

            items
        } catch (e: Exception) {
            println("❌ Error fetching chat history: ${e.message}")
            emptyList()
        }
    }

    suspend fun clearChatHistory(goalId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/chat_messages?goal_id=eq.$goalId")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "DELETE"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error clearing chat history: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // v2.0: КОМПЛЕКСНА ФУНКЦІЯ — Зберегти весь план
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає повний план: ціль + 10 напрямків + 100 кроків
     * @return ID створеної цілі або null при помилці
     */
    suspend fun saveCompletePlan(
        userId: String,
        plan: GeneratedPlan,
        assessmentId: String? = null,
        makePrimary: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        try {
            println("📦 saveCompletePlan started")
            println("📦 Plan: ${plan.goal.title}")
            println("📦 Directions count: ${plan.directions.size}")
            println("📦 Steps count: ${plan.steps.size}")

            if (plan.directions.isNotEmpty()) {
                println("📦 First direction: ${plan.directions.first().title} - ${plan.directions.first().description.take(50)}")
            }
            if (plan.steps.isNotEmpty()) {
                println("📦 First step: ${plan.steps.first().title}")
            }

            val currentCount = getGoalsCount(userId)
            if (currentCount >= 3) {
                println("❌ Goals limit reached (3)")
                return@withContext null
            }

            if (makePrimary) {
                println("🔄 Resetting is_primary for all existing goals...")
                resetAllPrimaryGoals(userId)
            }

            val goalId = createGoal(
                userId = userId,
                title = plan.goal.title,
                targetSalary = plan.goal.targetSalary,
                assessmentId = assessmentId,
                isPrimary = makePrimary
            )

            if (goalId == null) {
                println("❌ Failed to create goal")
                return@withContext null
            }

            println("✅ New goal created: $goalId (is_primary: $makePrimary)")

            println("📦 Saving ${plan.directions.size} directions...")
            val directionIdMap = saveDirections(goalId, plan.directions, blockNumber = 1)
            if (directionIdMap.isEmpty()) {
                println("⚠️ Warning: Failed to save directions")
            } else {
                println("✅ ${directionIdMap.size} directions saved with ID map")
            }

            println("📦 Saving ${plan.steps.size} steps with directionIdMap size: ${directionIdMap.size}")
            val stepsResult = saveSteps(goalId, plan.steps, directionIdMap, blockNumber = 1)
            println("📦 Steps save result: $stepsResult")

            if (!stepsResult) {
                println("⚠️ Warning: Failed to save steps")
            }

            println("✅ Complete plan saved: $goalId with ${plan.directions.size} directions and ${plan.steps.size} steps")
            goalId

        } catch (e: Exception) {
            println("❌ Error saving complete plan: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Зберігає наступний блок (після завершення попереднього)
     */
    suspend fun saveNextBlock(
        goalId: String,
        plan: GeneratedPlan,
        blockNumber: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val directionIdMap = saveDirections(goalId, plan.directions, blockNumber)
            if (directionIdMap.isEmpty()) {
                println("❌ Failed to save directions for block $blockNumber")
                return@withContext false
            }

            val stepsResult = saveSteps(goalId, plan.steps, directionIdMap, blockNumber)
            if (!stepsResult) {
                println("❌ Failed to save steps for block $blockNumber")
                return@withContext false
            }

            println("✅ Block $blockNumber saved: 10 directions, 100 steps")
            true
        } catch (e: Exception) {
            println("❌ Error saving next block: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DEPRECATED — Старі функції для зворотної сумісності
    // ════════════════════════════════════════════════════════════════

    @Deprecated("Use saveDirections instead")
    suspend fun saveStrategicSteps(
        goalId: String,
        steps: List<GeneratedStrategicStep>
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val directions = steps.map { step ->
            GeneratedDirection(
                number = step.number,
                title = step.title,
                description = step.description
            )
        }
        saveDirections(goalId, directions, 1)
    }

    @Deprecated("Use getDirections instead")
    suspend fun getStrategicSteps(goalId: String): List<StrategicStepItem> = withContext(Dispatchers.IO) {
        val directions = getDirections(goalId, 1)
        directions.map { dir ->
            StrategicStepItem(
                id = dir.id,
                goalId = dir.goalId,
                stepNumber = dir.directionNumber,
                title = dir.title,
                description = dir.description,
                timeframe = "Блок ${dir.blockNumber}",
                status = dir.status,
                startWeek = dir.blockNumber,
                endWeek = dir.blockNumber,
                progressPercent = 0
            )
        }
    }

    @Deprecated("Use getSteps instead")
    suspend fun getWeeklyTasks(goalId: String, weekNumber: Int): List<WeeklyTaskItem> = withContext(Dispatchers.IO) {
        val steps = getSteps(goalId, weekNumber)
        steps.map { step ->
            WeeklyTaskItem(
                id = step.id,
                goalId = step.goalId,
                weekNumber = step.blockNumber,
                taskNumber = step.stepNumber,
                title = step.title,
                description = step.description,
                status = step.status,
                strategicStepId = step.directionId
            )
        }
    }

    @Deprecated("Use updateStepStatus instead")
    suspend fun updateTaskStatus(taskId: String, status: String): Boolean =
        updateStepStatus(taskId, status)

    @Deprecated("Use getCurrentBlockNumber instead")
    suspend fun getCurrentWeekNumber(goalId: String): Int = getCurrentBlockNumber(goalId)

    @Deprecated("Use getMaxBlockNumber instead")
    suspend fun getMaxWeekNumber(goalId: String): Int = getMaxBlockNumber(goalId)

    @Deprecated("Use getBlockStats instead")
    suspend fun getWeekStats(goalId: String, weekNumber: Int): WeekStats {
        val blockStats = getBlockStats(goalId, weekNumber)
        return WeekStats(
            total = blockStats.total,
            done = blockStats.done,
            skipped = blockStats.skipped,
            pending = blockStats.pending
        )
    }

    @Deprecated("Use isBlockComplete instead")
    suspend fun isWeekComplete(goalId: String, weekNumber: Int): Boolean =
        isBlockComplete(goalId, weekNumber)

    @Deprecated("Use getAllSteps instead")
    suspend fun getAllWeeklyTasks(goalId: String): List<WeeklyTaskItem> = withContext(Dispatchers.IO) {
        val steps = getAllSteps(goalId)
        steps.map { step ->
            WeeklyTaskItem(
                id = step.id,
                goalId = step.goalId,
                weekNumber = step.blockNumber,
                taskNumber = step.stepNumber,
                title = step.title,
                description = step.description,
                status = step.status,
                strategicStepId = step.directionId
            )
        }
    }

    @Deprecated("Use saveSteps instead")
    suspend fun saveWeeklyTasks(
        goalId: String,
        weekNumber: Int,
        tasks: List<GeneratedWeeklyTask>,
        stepIdMap: Map<Int, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        val steps = tasks.map { task ->
            GeneratedStep(
                number = task.number,
                localNumber = ((task.number - 1) % 10) + 1,
                title = task.title,
                description = task.description,
                directionNumber = task.strategicStepNumber
            )
        }
        saveSteps(goalId, steps, stepIdMap, weekNumber)
    }
}

// ════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════

data class AssessmentHistoryItem(
    val id: String,
    val userId: String,
    val matchScore: Int,
    val gapAnalysis: String,
    val actionPlan: String,
    val answers: String,
    val createdAt: String
)

data class GoalItem(
    val id: String,
    val userId: String,
    val assessmentId: String?,
    val title: String,
    val targetSalary: String,
    val isPrimary: Boolean,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)

/**
 * v2.0: Статистика блоку (100 кроків)
 */
data class BlockStats(
    val total: Int,
    val done: Int,
    val skipped: Int,
    val pending: Int
) {
    val isComplete: Boolean get() = total == 100 && pending == 0
    val progressPercent: Int get() = if (total > 0) (done * 100 / total) else 0
}

/**
 * v2.0: Напрямок з кроками
 */
data class DirectionWithSteps(
    val direction: DirectionItem,
    val steps: List<StepItem>,
    val doneCount: Int,
    val skippedCount: Int
) {
    val totalCount: Int get() = steps.size
    val pendingCount: Int get() = steps.count { it.status == "pending" }
    val progressPercent: Int get() = if (totalCount > 0) (doneCount * 100 / totalCount) else 0
    val isComplete: Boolean get() = totalCount == 10 && pendingCount == 0
}

/**
 * Прогрес цілі
 */
data class GoalProgress(
    val overallPercent: Int,
    val tasksCompleted: Int,
    val tasksTotal: Int,
    val stepsCompleted: Int,
    val stepsInProgress: Int,
    val stepsTotal: Int,
    val currentWeek: Int
)

// ════════════════════════════════════════════════════════════════
// DEPRECATED DATA CLASSES — для зворотної сумісності
// ════════════════════════════════════════════════════════════════

@Deprecated("Use BlockStats instead")
data class WeekStats(
    val total: Int,
    val done: Int,
    val skipped: Int,
    val pending: Int
) {
    val isComplete: Boolean get() = total > 0 && pending == 0
    val progressPercent: Int get() = if (total > 0) (done * 100 / total) else 0
}

@Deprecated("Use DirectionWithSteps instead")
data class StepWithTasks(
    val step: StrategicStepItem,
    val tasks: List<WeeklyTaskItem>,
    val isActiveForWeek: Boolean
)