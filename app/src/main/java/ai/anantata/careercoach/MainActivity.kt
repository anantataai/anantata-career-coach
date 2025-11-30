package ai.anantata.careercoach

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anantata.careercoach.ui.theme.AnantataCoachTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "AnantataCoach"

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "anantata_prefs"
    private val ONBOARDING_COMPLETED = "onboarding_completed"
    private val FIRST_ASSESSMENT_COMPLETED = "first_assessment_completed"
    private val USER_ID_KEY = "user_device_id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = getOrCreateUserId()
        Log.d(TAG, "🚀 App started with userId: $userId")

        setContent {
            AnantataCoachTheme {
                MainApp(
                    userId = userId,
                    isOnboardingCompleted = isOnboardingCompleted(),
                    isFirstAssessmentCompleted = isFirstAssessmentCompleted(),
                    onOnboardingComplete = { completeOnboarding() },
                    onFirstAssessmentComplete = { completeFirstAssessment() }
                )
            }
        }
    }

    private fun getOrCreateUserId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var userId = prefs.getString(USER_ID_KEY, null)

        if (userId == null) {
            userId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            prefs.edit().putString(USER_ID_KEY, userId).apply()
        }

        return userId
    }

    private fun isOnboardingCompleted(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ONBOARDING_COMPLETED, false)
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ONBOARDING_COMPLETED, true).apply()
    }

    private fun isFirstAssessmentCompleted(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(FIRST_ASSESSMENT_COMPLETED, false)
    }

    private fun completeFirstAssessment() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(FIRST_ASSESSMENT_COMPLETED, true).apply()
    }
}

/**
 * Допоміжна функція для парсингу відповідей з JSON строки
 */
fun parseAnswersFromJson(answersJson: String): Map<String, String> {
    return try {
        val jsonObject = JSONObject(answersJson)
        val map = mutableMapOf<String, String>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.getString(key)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Генерує контекст плану для обговорення в чаті
 */
fun generatePlanContext(
    goalAnswer: String,
    salaryAnswer: String,
    actionPlan: String
): String {
    return buildString {
        appendLine("🎯 Твоя мета: $goalAnswer")
        appendLine("💰 Бажаний дохід: $salaryAnswer")
        appendLine()
        appendLine("📋 Твій план з 10 кроків:")
        appendLine(actionPlan)
        appendLine()
        appendLine("З чого ти готовий почати свій шлях до успіху? 🚀")
    }
}

@Composable
fun MainApp(
    userId: String,
    isOnboardingCompleted: Boolean,
    isFirstAssessmentCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
    onFirstAssessmentComplete: () -> Unit
) {
    var showOnboarding by remember { mutableStateOf(!isOnboardingCompleted) }
    var showFirstAssessment by remember { mutableStateOf(!isFirstAssessmentCompleted && isOnboardingCompleted) }
    var showHistory by remember { mutableStateOf(false) }

    // v1.5: Нові стани для навігації
    var showDashboard by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showStrategy by remember { mutableStateOf(false) }
    var showGoalsList by remember { mutableStateOf(false) }

    // Стан для перегляду збереженого результату
    var viewingHistoryItem by remember { mutableStateOf<AssessmentHistoryItem?>(null) }

    // Стан для переходу в чат з контекстом плану
    var chatWithPlanContext by remember { mutableStateOf<AssessmentHistoryItem?>(null) }

    // Стан для запуску нової оцінки
    var triggerNewAssessment by remember { mutableStateOf(false) }

    when {
        showOnboarding -> {
            OnboardingScreen(
                onFinish = {
                    onOnboardingComplete()
                    showOnboarding = false
                    showFirstAssessment = true
                }
            )
        }

        showFirstAssessment -> {
            FirstAssessmentFlow(
                userId = userId,
                onComplete = {
                    onFirstAssessmentComplete()
                    showFirstAssessment = false
                    // v1.5: Після першого assessment — показуємо Dashboard
                    showDashboard = true
                }
            )
        }

        // Показ збереженого результату з історії
        viewingHistoryItem != null -> {
            val item = viewingHistoryItem!!
            val parsedResult = parseAssessmentResults(item.gapAnalysis, item.actionPlan)

            val answersMap = parseAnswersFromJson(item.answers)
            val goalAnswer = answersMap["8"] ?: ""
            val salaryAnswer = answersMap["9"] ?: ""

            AssessmentResultsScreen(
                result = parsedResult,
                isViewMode = true,
                goalAnswer = goalAnswer,
                salaryAnswer = salaryAnswer,
                onBackToChat = {
                    viewingHistoryItem = null
                    showHistory = true
                },
                onRetakeAssessment = {
                    viewingHistoryItem = null
                    showHistory = false
                    triggerNewAssessment = true
                },
                onDiscussPlan = {
                    chatWithPlanContext = item
                    viewingHistoryItem = null
                }
            )
        }

        showHistory -> {
            AssessmentHistoryScreen(
                userId = userId,
                onBack = {
                    showHistory = false
                    showDashboard = true
                },
                onViewResult = { historyItem ->
                    viewingHistoryItem = historyItem
                    showHistory = false
                },
                onDiscussPlan = { historyItem ->
                    chatWithPlanContext = historyItem
                    showHistory = false
                    showChat = true
                }
            )
        }

        // v1.5: Екран стратегії
        showStrategy -> {
            StrategyScreen(
                userId = userId,
                onBack = {
                    showStrategy = false
                    showDashboard = true
                }
            )
        }

        // v1.5: Екран списку цілей
        showGoalsList -> {
            GoalsListScreen(
                userId = userId,
                onBack = {
                    showGoalsList = false
                    showDashboard = true
                },
                onGoalSelected = {
                    showGoalsList = false
                    showDashboard = true
                }
            )
        }

        // v1.5: Головний Dashboard з завданнями
        showDashboard -> {
            GoalDashboardScreen(
                userId = userId,
                onOpenChat = {
                    showDashboard = false
                    showChat = true
                },
                onOpenStrategy = {
                    showDashboard = false
                    showStrategy = true
                },
                onOpenGoalsList = {
                    showDashboard = false
                    showGoalsList = true
                },
                onOpenHistory = {
                    showDashboard = false
                    showHistory = true
                },
                onStartNewAssessment = {
                    showDashboard = false
                    triggerNewAssessment = true
                }
            )
        }

        // v1.5: Чат (тепер окремий екран)
        showChat -> {
            ChatScreen(
                userId = userId,
                onOpenHistory = {
                    showChat = false
                    showHistory = true
                },
                onBackToDashboard = {
                    showChat = false
                    showDashboard = true
                },
                initialPlanContext = chatWithPlanContext,
                onPlanContextConsumed = { chatWithPlanContext = null },
                triggerAssessment = triggerNewAssessment,
                onAssessmentTriggered = { triggerNewAssessment = false }
            )
        }

        else -> {
            // За замовчуванням — показуємо Dashboard якщо є ціль, інакше чат
            LaunchedEffect(Unit) {
                val supabaseRepo = SupabaseRepository()
                val primaryGoal = supabaseRepo.getPrimaryGoal(userId)
                Log.d(TAG, "📊 Primary goal check: ${primaryGoal?.title ?: "NULL"}")
                if (primaryGoal != null) {
                    showDashboard = true
                } else {
                    showChat = true
                }
            }

            // Показуємо лоадер поки визначаємо куди йти
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun FirstAssessmentFlow(
    userId: String,
    onComplete: () -> Unit
) {
    val geminiRepo = remember { GeminiRepository() }
    val supabaseRepo = remember { SupabaseRepository() }
    val conversationId = remember { java.util.UUID.randomUUID().toString() }

    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var savedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var savedGapAnalysis by remember { mutableStateOf("") }
    var savedActionPlan by remember { mutableStateOf("") }

    // v1.5: Зберігаємо згенерований план
    var generatedPlan by remember { mutableStateOf<GeneratedPlan?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)
    }

    if (showResultsScreen && assessmentResult != null) {
        AssessmentResultsScreen(
            result = assessmentResult!!,
            isViewMode = false,
            goalAnswer = savedAnswers[8] ?: "",
            salaryAnswer = savedAnswers[9] ?: "",
            onBackToChat = { onComplete() },
            onRetakeAssessment = {
                showResultsScreen = false
                assessmentResult = null
                savedAnswers = emptyMap()
                generatedPlan = null
            },
            onDiscussPlan = {
                Log.d(TAG, "🎯 onDiscussPlan clicked!")
                Log.d(TAG, "📦 generatedPlan = $generatedPlan")

                // Після першого assessment — зберігаємо план і завершуємо
                // ВАЖЛИВО: чекаємо завершення перед переходом!
                scope.launch {
                    if (generatedPlan != null) {
                        Log.d(TAG, "💾 Saving plan: ${generatedPlan!!.goal.title}")
                        val goalId = supabaseRepo.saveCompletePlan(
                            userId = userId,
                            plan = generatedPlan!!,
                            makePrimary = true
                        )
                        Log.d(TAG, "✅ Plan saved with goalId: $goalId")
                    } else {
                        Log.e(TAG, "❌ generatedPlan is NULL!")
                    }
                    // Тільки після збереження завершуємо
                    onComplete()
                }
            }
        )
    } else if (isProcessing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Аналізую ваш профіль...",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    } else {
        AssessmentScreenUI(
            assessmentType = "Повну",
            geminiRepo = geminiRepo,
            onComplete = { answersMap ->
                savedAnswers = answersMap
                Log.d(TAG, "📝 Assessment completed with ${answersMap.size} answers")

                scope.launch {
                    isProcessing = true

                    try {
                        val questions = geminiRepo.generateAssessmentQuestions("Повну")
                        Log.d(TAG, "📋 Generated ${questions.size} questions")

                        // v1.5: Використовуємо нову функцію генерації плану
                        Log.d(TAG, "🔄 Calling generateGoalWithPlan...")
                        val plan = geminiRepo.generateGoalWithPlan(answersMap, questions)
                        generatedPlan = plan
                        Log.d(TAG, "✅ Plan generated: ${plan.goal.title}, ${plan.strategicSteps.size} steps, ${plan.weeklyTasks.size} tasks")

                        // Формуємо gapAnalysis у форматі який розуміє parseAssessmentResults
                        savedGapAnalysis = buildString {
                            appendLine("Match Score: ${plan.matchScore}%")
                            appendLine()
                            appendLine("СИЛЬНІ СТОРОНИ:")
                            appendLine("- Мотивація до досягнення мети")
                            appendLine("- Готовність до змін")
                            appendLine()
                            appendLine("ЩО ПОТРІБНО РОЗВИНУТИ:")
                            appendLine(plan.gapAnalysis)
                            appendLine()
                            appendLine("ОЦІНКА ЗАРПЛАТИ: ${plan.goal.targetSalary}")
                            appendLine("ЧАС ДО МЕТИ: 6-12 місяців")
                        }

                        // Формуємо actionPlan у форматі який розуміє parseAssessmentResults
                        savedActionPlan = plan.strategicSteps.joinToString("\n\n") { step ->
                            buildString {
                                appendLine("КРОК ${step.number}: ${step.title}")
                                appendLine(step.description)
                                appendLine("⏰ Час: ${step.timeframe}")
                                appendLine("🔥 Пріоритет: ${if (step.number <= 3) "Високий" else "Середній"}")
                            }
                        }

                        supabaseRepo.saveMessage(conversationId, "assistant", savedGapAnalysis)
                        supabaseRepo.saveMessage(conversationId, "assistant", savedActionPlan)

                        // Парсимо результат стандартною функцією
                        assessmentResult = parseAssessmentResults(savedGapAnalysis, savedActionPlan)
                        Log.d(TAG, "📊 Parsed result: matchScore=${assessmentResult?.matchScore}")

                        // Зберігаємо результат в assessment_results (для історії)
                        assessmentResult?.let { result ->
                            supabaseRepo.saveAssessmentResult(
                                userId = userId,
                                matchScore = result.matchScore,
                                gapAnalysis = savedGapAnalysis,
                                actionPlan = savedActionPlan,
                                answers = answersMap
                            )
                        }

                        showResultsScreen = true

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in assessment: ${e.message}")
                        e.printStackTrace()
                        onComplete()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onCancel = { onComplete() }
        )
    }
}

@Composable
fun ChatScreen(
    userId: String,
    onOpenHistory: () -> Unit,
    onBackToDashboard: () -> Unit = {},
    initialPlanContext: AssessmentHistoryItem? = null,
    onPlanContextConsumed: () -> Unit = {},
    triggerAssessment: Boolean = false,
    onAssessmentTriggered: () -> Unit = {}
) {
    val geminiRepo = remember { GeminiRepository() }
    val supabaseRepo = remember { SupabaseRepository() }
    val conversationId = remember { java.util.UUID.randomUUID().toString() }

    val context = LocalContext.current

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAssessmentScreen by remember { mutableStateOf(false) }
    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    var savedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var savedGapAnalysis by remember { mutableStateOf("") }
    var savedActionPlan by remember { mutableStateOf("") }
    var latestAssessment by remember { mutableStateOf<AssessmentHistoryItem?>(null) }
    var isLoadingHistory by remember { mutableStateOf(true) }

    // v1.5: Згенерований план
    var generatedPlan by remember { mutableStateOf<GeneratedPlan?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)
        val history = supabaseRepo.getAssessmentHistory(userId)
        latestAssessment = history.firstOrNull()
        isLoadingHistory = false
    }

    LaunchedEffect(triggerAssessment) {
        if (triggerAssessment) {
            showAssessmentScreen = true
            onAssessmentTriggered()
        }
    }

    LaunchedEffect(initialPlanContext) {
        if (initialPlanContext != null) {
            val answersMap = parseAnswersFromJson(initialPlanContext.answers)
            val goalAnswer = answersMap["8"] ?: "Досягти кар'єрної мети"
            val salaryAnswer = answersMap["9"] ?: "Збільшити дохід"

            val contextMessage = generatePlanContext(
                goalAnswer = goalAnswer,
                salaryAnswer = salaryAnswer,
                actionPlan = initialPlanContext.actionPlan
            )

            messages = listOf(ChatMessage("assistant", contextMessage))
            onPlanContextConsumed()
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            showFabMenu = false
        }
    }

    if (showResultsScreen && assessmentResult != null) {
        AssessmentResultsScreen(
            result = assessmentResult!!,
            isViewMode = false,
            goalAnswer = savedAnswers[8] ?: "",
            salaryAnswer = savedAnswers[9] ?: "",
            onBackToChat = { showResultsScreen = false },
            onRetakeAssessment = {
                showResultsScreen = false
                assessmentResult = null
                savedAnswers = emptyMap()
                generatedPlan = null
                showAssessmentScreen = true
            },
            onDiscussPlan = {
                Log.d(TAG, "🎯 ChatScreen: onDiscussPlan clicked!")
                Log.d(TAG, "📦 ChatScreen: generatedPlan = $generatedPlan")

                // v1.5: Зберігаємо план і переходимо на Dashboard
                // ВАЖЛИВО: чекаємо завершення перед переходом!
                scope.launch {
                    if (generatedPlan != null) {
                        Log.d(TAG, "💾 ChatScreen: Saving plan...")
                        val goalId = supabaseRepo.saveCompletePlan(
                            userId = userId,
                            plan = generatedPlan!!,
                            makePrimary = true
                        )
                        Log.d(TAG, "✅ ChatScreen: Plan saved with goalId: $goalId")

                        // Тільки після збереження переходимо далі
                        showResultsScreen = false
                        onBackToDashboard()
                    } else {
                        Log.e(TAG, "❌ ChatScreen: generatedPlan is NULL!")
                        showResultsScreen = false
                        onBackToDashboard()
                    }
                }
            }
        )
    }
    else if (showAssessmentScreen) {
        AssessmentScreenUI(
            assessmentType = "Повну",
            geminiRepo = geminiRepo,
            onComplete = { answersMap ->
                savedAnswers = answersMap
                Log.d(TAG, "📝 ChatScreen: Assessment completed")

                scope.launch {
                    val questions = geminiRepo.generateAssessmentQuestions("Повну")

                    showAssessmentScreen = false

                    messages = messages + ChatMessage(
                        "assistant",
                        "✅ Оцінку завершено! Аналізую ваш профіль..."
                    )

                    isLoading = true

                    try {
                        // v1.5: Використовуємо нову функцію
                        Log.d(TAG, "🔄 ChatScreen: Calling generateGoalWithPlan...")
                        val plan = geminiRepo.generateGoalWithPlan(answersMap, questions)
                        generatedPlan = plan
                        Log.d(TAG, "✅ ChatScreen: Plan generated: ${plan.goal.title}")

                        // Формуємо gapAnalysis
                        savedGapAnalysis = buildString {
                            appendLine("Match Score: ${plan.matchScore}%")
                            appendLine()
                            appendLine("СИЛЬНІ СТОРОНИ:")
                            appendLine("- Мотивація до досягнення мети")
                            appendLine("- Готовність до змін")
                            appendLine()
                            appendLine("ЩО ПОТРІБНО РОЗВИНУТИ:")
                            appendLine(plan.gapAnalysis)
                            appendLine()
                            appendLine("ОЦІНКА ЗАРПЛАТИ: ${plan.goal.targetSalary}")
                            appendLine("ЧАС ДО МЕТИ: 6-12 місяців")
                        }

                        messages = messages + ChatMessage("assistant", savedGapAnalysis)
                        supabaseRepo.saveMessage(conversationId, "assistant", savedGapAnalysis)

                        listState.animateScrollToItem(messages.size - 1)

                        messages = messages + ChatMessage(
                            "assistant",
                            "📋 Генерую персоналізований план з 10 кроків...\n\n⏳ Це може зайняти до 30 секунд."
                        )

                        listState.animateScrollToItem(messages.size - 1)

                        // Формуємо actionPlan
                        savedActionPlan = plan.strategicSteps.joinToString("\n\n") { step ->
                            buildString {
                                appendLine("КРОК ${step.number}: ${step.title}")
                                appendLine(step.description)
                                appendLine("⏰ Час: ${step.timeframe}")
                                appendLine("🔥 Пріоритет: ${if (step.number <= 3) "Високий" else "Середній"}")
                            }
                        }

                        messages = messages + ChatMessage("assistant", savedActionPlan)
                        supabaseRepo.saveMessage(conversationId, "assistant", savedActionPlan)

                        // Парсимо результат стандартною функцією
                        assessmentResult = parseAssessmentResults(savedGapAnalysis, savedActionPlan)

                        assessmentResult?.let { result ->
                            supabaseRepo.saveAssessmentResult(
                                userId = userId,
                                matchScore = result.matchScore,
                                gapAnalysis = savedGapAnalysis,
                                actionPlan = savedActionPlan,
                                answers = answersMap
                            )
                        }

                        showResultsScreen = true

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ ChatScreen: Error: ${e.message}")
                        messages = messages + ChatMessage(
                            "assistant",
                            "Вибачте, сталася помилка: ${e.message}"
                        )
                    } finally {
                        isLoading = false
                    }

                    listState.animateScrollToItem(messages.size - 1)
                }
            },
            onCancel = {
                showAssessmentScreen = false
            }
        )
    }
    else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty() && !isLoadingHistory && initialPlanContext == null) {
                        item {
                            WelcomeMessageCard(
                                latestAssessment = latestAssessment,
                                onDiscussPlan = {
                                    latestAssessment?.let { assessment ->
                                        val answersMap = parseAnswersFromJson(assessment.answers)
                                        val goalAnswer = answersMap["8"] ?: "Досягти кар'єрної мети"
                                        val salaryAnswer = answersMap["9"] ?: "Збільшити дохід"

                                        val contextMessage = generatePlanContext(
                                            goalAnswer = goalAnswer,
                                            salaryAnswer = salaryAnswer,
                                            actionPlan = assessment.actionPlan
                                        )

                                        messages = listOf(ChatMessage("assistant", contextMessage))
                                    }
                                }
                            )
                        }
                    }

                    items(messages) { message ->
                        MessageBubble(message)
                    }

                    if (isLoading) {
                        item {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Напишіть повідомлення...") },
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMessage = inputText
                                messages = messages + ChatMessage("user", userMessage)
                                scope.launch {
                                    supabaseRepo.saveMessage(conversationId, "user", userMessage)
                                }
                                inputText = ""
                                isLoading = true

                                scope.launch {
                                    try {
                                        val aiResponse = StringBuilder()
                                        geminiRepo.sendMessage(userMessage).collect { chunk ->
                                            aiResponse.append(chunk)
                                        }

                                        messages = messages + ChatMessage("assistant", aiResponse.toString())
                                        supabaseRepo.saveMessage(conversationId, "assistant", aiResponse.toString())
                                    } catch (e: Exception) {
                                        messages = messages + ChatMessage(
                                            "assistant",
                                            "Вибачте, сталася помилка: ${e.message}"
                                        )
                                    } finally {
                                        isLoading = false
                                    }

                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Text("→")
                    }
                }
            }

            // FAB меню
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showFabMenu && !isLoading) {
                    // v1.5: Кнопка "Dashboard"
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onBackToDashboard()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📊", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Прогрес", fontSize = 14.sp)
                        }
                    }

                    // Відгук
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=ai.anantata.careercoach")
                                setPackage("com.android.vending")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://play.google.com/store/apps/details?id=ai.anantata.careercoach")
                                }
                                context.startActivity(browserIntent)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⭐", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Відгук", fontSize = 14.sp)
                        }
                    }

                    // Історія
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onOpenHistory()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📋", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Історія", fontSize = 14.sp)
                        }
                    }

                    // Нова оцінка
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            showAssessmentScreen = true
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎯", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Нова оцінка", fontSize = 14.sp)
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        if (!isLoading) {
                            showFabMenu = !showFabMenu
                        }
                    },
                    containerColor = if (isLoading) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Icon(
                        imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Меню",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
        }
    }
}

/**
 * Картка привітального повідомлення
 */
@Composable
fun WelcomeMessageCard(
    latestAssessment: AssessmentHistoryItem?,
    onDiscussPlan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (latestAssessment != null) {
                val answersMap = parseAnswersFromJson(latestAssessment.answers)
                val goalAnswer = answersMap["8"] ?: "Досягти кар'єрної мети"
                val salaryAnswer = answersMap["9"] ?: "Збільшити дохід"

                Text(
                    text = "👋 З поверненням!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "🎯 Твоя мета:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = goalAnswer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💰 Бажаний дохід:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = salaryAnswer,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "📋 У тебе є план з 10 кроків для досягнення мети.",
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDiscussPlan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💬 Обговорити план")
                }

            } else {
                Text(
                    text = "👋 Привіт!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Я твій кар'єрний помічник.",
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Натисни 🎯 щоб пройти оцінку та отримати персональний план з 10 кроків до твоєї мети!",
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.role == "user")
            Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (message.role == "user")
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.role == "user")
                    MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

data class ChatMessage(
    val role: String,
    val content: String
)

// ════════════════════════════════════════════════════════════════
// ЗАГЛУШКИ для ще не створених екранів (будуть в наступних кроках)
// ════════════════════════════════════════════════════════════════

/**
 * Екран стратегії (заглушка — буде в Кроці 3)
 */
@Composable
fun StrategyScreen(
    userId: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📋 Екран стратегії", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("(Буде додано в наступному кроці)")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("← Назад")
            }
        }
    }
}

/**
 * Екран списку цілей (заглушка — буде в Кроці 4)
 */
@Composable
fun GoalsListScreen(
    userId: String,
    onBack: () -> Unit,
    onGoalSelected: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📁 Список цілей", fontSize = 24.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("(Буде додано в наступному кроці)")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("← Назад")
            }
        }
    }
}