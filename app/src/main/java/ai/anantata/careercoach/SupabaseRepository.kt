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
    // GOALS — CRUD для цілей (v1.5)
    // ════════════════════════════════════════════════════════════════

    /**
     * Створює нову ціль
     * @return ID створеної цілі або null при помилці
     */
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

    /**
     * Отримує всі цілі користувача
     */
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

    /**
     * Отримує головну (primary) ціль користувача
     */
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

    /**
     * Перевіряє кількість цілей користувача (ліміт 3)
     */
    suspend fun getGoalsCount(userId: String): Int = withContext(Dispatchers.IO) {
        try {
            val goals = getGoals(userId)
            goals.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 🔧 ВИПРАВЛЕНО: Знімає is_primary з УСІХ цілей користувача
     */
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

    /**
     * Встановлює ціль як головну (і знімає з інших)
     */
    suspend fun setPrimaryGoal(userId: String, goalId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Знімаємо primary з усіх цілей користувача
            resetAllPrimaryGoals(userId)

            // 2. Встановлюємо primary для обраної цілі
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

    /**
     * Оновлює статус цілі (active/paused/completed)
     */
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

    /**
     * Видаляє ціль (каскадно видаляє steps, tasks, messages)
     */
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
    // STRATEGIC STEPS — CRUD для стратегічних кроків
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає 10 стратегічних кроків для цілі
     */
    suspend fun saveStrategicSteps(
        goalId: String,
        steps: List<GeneratedStrategicStep>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val stepsArray = JSONArray()
            steps.forEach { step ->
                stepsArray.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("goal_id", goalId)
                    put("step_number", step.number)
                    put("title", step.title)
                    put("description", step.description)
                    put("timeframe", step.timeframe)
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
                os.write(stepsArray.toString().toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Strategic steps saved for goal: $goalId")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error saving strategic steps: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Отримує стратегічні кроки для цілі
     */
    suspend fun getStrategicSteps(goalId: String): List<StrategicStepItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/strategic_steps?goal_id=eq.$goalId&order=step_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<StrategicStepItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    StrategicStepItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        stepNumber = obj.getInt("step_number"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        timeframe = obj.optString("timeframe", ""),
                        status = obj.getString("status")
                    )
                )
            }

            items
        } catch (e: Exception) {
            println("❌ Error fetching strategic steps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Оновлює статус стратегічного кроку
     */
    suspend fun updateStrategicStepStatus(stepId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/strategic_steps?id=eq.$stepId")
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
            println("❌ Error updating step status: ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WEEKLY TASKS — CRUD для тижневих завдань
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає 10 тижневих завдань
     */
    suspend fun saveWeeklyTasks(
        goalId: String,
        weekNumber: Int,
        tasks: List<GeneratedWeeklyTask>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tasksArray = JSONArray()
            tasks.forEach { task ->
                tasksArray.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("goal_id", goalId)
                    put("week_number", weekNumber)
                    put("task_number", task.number)
                    put("title", task.title)
                    put("description", task.description)
                    put("status", "pending")
                    put("created_at", Clock.System.now().toString())
                })
            }

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
                os.write(tasksArray.toString().toByteArray())
            }
            val responseCode = connection.responseCode
            connection.disconnect()

            println("✅ Weekly tasks saved for goal $goalId, week $weekNumber")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error saving weekly tasks: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Отримує завдання для конкретного тижня
     */
    suspend fun getWeeklyTasks(goalId: String, weekNumber: Int): List<WeeklyTaskItem> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/rest/v1/weekly_tasks?goal_id=eq.$goalId&week_number=eq.$weekNumber&order=task_number.asc")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val jsonArray = JSONArray(response)
            val items = mutableListOf<WeeklyTaskItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    WeeklyTaskItem(
                        id = obj.getString("id"),
                        goalId = obj.getString("goal_id"),
                        weekNumber = obj.getInt("week_number"),
                        taskNumber = obj.getInt("task_number"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        status = obj.getString("status")
                    )
                )
            }

            items
        } catch (e: Exception) {
            println("❌ Error fetching weekly tasks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Отримує поточний номер тижня для цілі (останній)
     */
    suspend fun getCurrentWeekNumber(goalId: String): Int = withContext(Dispatchers.IO) {
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
                0 // Немає завдань ще
            }
        } catch (e: Exception) {
            println("❌ Error getting current week: ${e.message}")
            1
        }
    }

    /**
     * Отримує максимальний номер тижня для цілі
     * Використовується для навігації по тижнях
     * @return Максимальний номер тижня або 1 якщо тижнів немає
     */
    suspend fun getMaxWeekNumber(goalId: String): Int = withContext(Dispatchers.IO) {
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
                val maxWeek = jsonArray.getJSONObject(0).getInt("week_number")
                println("📅 Max week number for goal $goalId: $maxWeek")
                maxWeek
            } else {
                println("📅 No weeks found for goal $goalId, returning 1")
                1
            }
        } catch (e: Exception) {
            println("❌ Error getting max week number: ${e.message}")
            1
        }
    }

    /**
     * Оновлює статус завдання (pending/done/skipped)
     */
    suspend fun updateTaskStatus(taskId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val completedAt = if (status == "done") {
                """, "completed_at": "${Clock.System.now()}""""
            } else {
                ""
            }

            val url = URL("$baseUrl/rest/v1/weekly_tasks?id=eq.$taskId")
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

            println("✅ Task status updated: $taskId -> $status")
            responseCode in 200..299
        } catch (e: Exception) {
            println("❌ Error updating task status: ${e.message}")
            false
        }
    }

    /**
     * Перевіряє чи всі завдання тижня виконані/пропущені
     */
    suspend fun isWeekComplete(goalId: String, weekNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val tasks = getWeeklyTasks(goalId, weekNumber)
            tasks.isNotEmpty() && tasks.all { it.status == "done" || it.status == "skipped" }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Отримує статистику тижня
     */
    suspend fun getWeekStats(goalId: String, weekNumber: Int): WeekStats = withContext(Dispatchers.IO) {
        try {
            val tasks = getWeeklyTasks(goalId, weekNumber)
            WeekStats(
                total = tasks.size,
                done = tasks.count { it.status == "done" },
                skipped = tasks.count { it.status == "skipped" },
                pending = tasks.count { it.status == "pending" }
            )
        } catch (e: Exception) {
            WeekStats(0, 0, 0, 0)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CHAT MESSAGES — CRUD для історії чату
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає повідомлення чату
     */
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

    /**
     * Отримує історію чату для цілі
     */
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

    /**
     * Видаляє історію чату для цілі
     */
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
    // 🔧 ВИПРАВЛЕНО: КОМПЛЕКСНА ФУНКЦІЯ — Зберегти весь план
    // ════════════════════════════════════════════════════════════════

    /**
     * Зберігає повний план: ціль + кроки + завдання
     * 🔧 ВИПРАВЛЕНО: Правильно знімає is_primary зі старих цілей
     * @return ID створеної цілі або null при помилці
     */
    suspend fun saveCompletePlan(
        userId: String,
        plan: GeneratedPlan,
        assessmentId: String? = null,
        makePrimary: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Перевіряємо ліміт цілей
            val currentCount = getGoalsCount(userId)
            if (currentCount >= 3) {
                println("❌ Goals limit reached (3)")
                return@withContext null
            }

            // 2. 🔧 ВИПРАВЛЕНО: Якщо makePrimary — знімаємо primary з УСІХ цілей
            if (makePrimary) {
                println("🔄 Resetting is_primary for all existing goals...")
                resetAllPrimaryGoals(userId)
            }

            // 3. Створюємо ціль (ЗАВЖДИ з Тижня 1!)
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

            // 4. Зберігаємо стратегічні кроки
            val stepsResult = saveStrategicSteps(goalId, plan.strategicSteps)
            if (!stepsResult) {
                println("⚠️ Warning: Failed to save strategic steps")
            }

            // 5. Зберігаємо тижневі завдання (ЗАВЖДИ Тиждень 1!)
            val tasksResult = saveWeeklyTasks(goalId, 1, plan.weeklyTasks)
            if (!tasksResult) {
                println("⚠️ Warning: Failed to save weekly tasks")
            }

            println("✅ Complete plan saved: $goalId with Week 1 tasks")
            goalId

        } catch (e: Exception) {
            println("❌ Error saving complete plan: ${e.message}")
            e.printStackTrace()
            null
        }
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
    val status: String, // "active", "paused", "completed"
    val createdAt: String,
    val updatedAt: String
)

data class WeekStats(
    val total: Int,
    val done: Int,
    val skipped: Int,
    val pending: Int
) {
    val isComplete: Boolean get() = total > 0 && pending == 0
    val progressPercent: Int get() = if (total > 0) (done * 100 / total) else 0
}