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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    // Репозиторії для GoalsListScreen
    val supabaseRepo = remember { SupabaseRepository() }

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
                    showDashboard = true
                }
            )
        }

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

        showStrategy -> {
            StrategyScreen(
                userId = userId,
                onBack = {
                    showStrategy = false
                    showDashboard = true
                }
            )
        }

        showGoalsList -> {
            GoalsListScreen(
                userId = userId,
                supabaseRepo = supabaseRepo,
                onBack = {
                    showGoalsList = false
                    showDashboard = true
                },
                onAddNewGoal = {
                    // Перехід на нову оцінку для створення нової цілі
                    showGoalsList = false
                    triggerNewAssessment = true
                },
                onGoalSelected = { goalId ->
                    // Повернення на Dashboard (можна додати логіку зміни активної цілі)
                    Log.d(TAG, "📁 Goal selected: $goalId")
                    showGoalsList = false
                    showDashboard = true
                }
            )
        }

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
            LaunchedEffect(Unit) {
                val primaryGoal = supabaseRepo.getPrimaryGoal(userId)
                Log.d(TAG, "📊 Primary goal check: ${primaryGoal?.title ?: "NULL"}")
                if (primaryGoal != null) {
                    showDashboard = true
                } else {
                    showChat = true
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// FIRST ASSESSMENT FLOW
// ════════════════════════════════════════════════════════════════

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

    var generatedPlan by remember { mutableStateOf<GeneratedPlan?>(null) }
    var planSaved by remember { mutableStateOf(false) }

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
                planSaved = false
            },
            onDiscussPlan = {
                Log.d(TAG, "🎯 onDiscussPlan clicked! Plan already saved: $planSaved")
                onComplete()
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

                        Log.d(TAG, "🔄 Calling generateGoalWithPlan...")
                        val plan = geminiRepo.generateGoalWithPlan(answersMap, questions)
                        generatedPlan = plan
                        Log.d(TAG, "✅ Plan generated: ${plan.goal.title}, ${plan.strategicSteps.size} steps, ${plan.weeklyTasks.size} tasks")

                        Log.d(TAG, "💾 AUTO-SAVING plan immediately...")
                        val goalId = supabaseRepo.saveCompletePlan(
                            userId = userId,
                            plan = plan,
                            makePrimary = true
                        )
                        if (goalId != null) {
                            planSaved = true
                            Log.d(TAG, "✅ Plan AUTO-SAVED with goalId: $goalId")
                        } else {
                            Log.e(TAG, "❌ Failed to auto-save plan")
                        }

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

                        assessmentResult = parseAssessmentResults(savedGapAnalysis, savedActionPlan)
                        Log.d(TAG, "📊 Parsed result: matchScore=${assessmentResult?.matchScore}")

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

// ════════════════════════════════════════════════════════════════
// CHAT SCREEN — v1.5 з історією чату та контекстом для ШІ
// ════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
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

    val context = LocalContext.current

    // UI стани
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAssessmentScreen by remember { mutableStateOf(false) }
    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    // Дані для контексту ШІ
    var primaryGoal by remember { mutableStateOf<GoalItem?>(null) }
    var strategicSteps by remember { mutableStateOf<List<StrategicStepItem>>(emptyList()) }
    var weeklyTasks by remember { mutableStateOf<List<WeeklyTaskItem>>(emptyList()) }
    var currentWeek by remember { mutableStateOf(1) }

    // Assessment стани
    var savedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var savedGapAnalysis by remember { mutableStateOf("") }
    var savedActionPlan by remember { mutableStateOf("") }
    var latestAssessment by remember { mutableStateOf<AssessmentHistoryItem?>(null) }

    // Завантаження стани
    var isLoadingData by remember { mutableStateOf(true) }

    var generatedPlan by remember { mutableStateOf<GeneratedPlan?>(null) }
    var planSaved by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // ════════════════════════════════════════════════════════════════
    // 🆕 ЗАВАНТАЖЕННЯ ДАНИХ: Goal + Steps + Tasks + Chat History
    // ════════════════════════════════════════════════════════════════
    LaunchedEffect(Unit) {
        isLoadingData = true
        try {
            // Завантажуємо головну ціль
            primaryGoal = supabaseRepo.getPrimaryGoal(userId)
            Log.d(TAG, "💬 Chat: Primary goal loaded: ${primaryGoal?.title ?: "NULL"}")

            primaryGoal?.let { goal ->
                // Завантажуємо стратегічні кроки
                strategicSteps = supabaseRepo.getStrategicSteps(goal.id)
                Log.d(TAG, "💬 Chat: Loaded ${strategicSteps.size} strategic steps")

                // Завантажуємо поточний тиждень
                currentWeek = supabaseRepo.getMaxWeekNumber(goal.id)
                Log.d(TAG, "💬 Chat: Current week: $currentWeek")

                // Завантажуємо завдання поточного тижня
                weeklyTasks = supabaseRepo.getWeeklyTasks(goal.id, currentWeek)
                Log.d(TAG, "💬 Chat: Loaded ${weeklyTasks.size} weekly tasks")

                // 🆕 Завантажуємо історію чату
                val chatHistory = supabaseRepo.getChatHistory(goal.id, 50)
                Log.d(TAG, "💬 Chat: Loaded ${chatHistory.size} chat messages from history")

                if (chatHistory.isNotEmpty()) {
                    messages = chatHistory.map { msg ->
                        ChatMessage(role = msg.role, content = msg.content)
                    }
                }
            }

            // Завантажуємо останній assessment для WelcomeCard
            val history = supabaseRepo.getAssessmentHistory(userId)
            latestAssessment = history.firstOrNull()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading chat data: ${e.message}")
        } finally {
            isLoadingData = false
        }
    }

    // Trigger assessment
    LaunchedEffect(triggerAssessment) {
        if (triggerAssessment) {
            showAssessmentScreen = true
            onAssessmentTriggered()
        }
    }

    // Initial plan context
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

    // Hide FAB menu when loading
    LaunchedEffect(isLoading) {
        if (isLoading) {
            showFabMenu = false
        }
    }

    // Scroll to bottom when new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 🆕 ФУНКЦІЯ: Побудувати контекст для ШІ
    // ════════════════════════════════════════════════════════════════
    fun buildCurrentContext(): String? {
        val goal = primaryGoal ?: return null

        // Конвертуємо ChatMessage в ChatMessageItem для buildAIContext
        val chatMessageItems = messages.takeLast(10).map { msg ->
            ChatMessageItem(
                id = "",
                userId = userId,
                goalId = goal.id,
                role = msg.role,
                content = msg.content,
                createdAt = ""
            )
        }

        return geminiRepo.buildAIContext(
            goalTitle = goal.title,
            targetSalary = goal.targetSalary,
            strategicSteps = strategicSteps,
            weeklyTasks = weeklyTasks,
            currentWeek = currentWeek,
            chatHistory = chatMessageItems
        )
    }

    // ════════════════════════════════════════════════════════════════
    // UI: Assessment Results Screen
    // ════════════════════════════════════════════════════════════════
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
                planSaved = false
                showAssessmentScreen = true
            },
            onDiscussPlan = {
                Log.d(TAG, "🎯 ChatScreen: onDiscussPlan clicked! Plan already saved: $planSaved")
                showResultsScreen = false
                onBackToDashboard()
            }
        )
    }
    // ════════════════════════════════════════════════════════════════
    // UI: Assessment Screen
    // ════════════════════════════════════════════════════════════════
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
                        Log.d(TAG, "🔄 ChatScreen: Calling generateGoalWithPlan...")
                        val plan = geminiRepo.generateGoalWithPlan(answersMap, questions)
                        generatedPlan = plan
                        Log.d(TAG, "✅ ChatScreen: Plan generated: ${plan.goal.title}")

                        Log.d(TAG, "💾 ChatScreen: AUTO-SAVING plan immediately...")
                        val goalId = supabaseRepo.saveCompletePlan(
                            userId = userId,
                            plan = plan,
                            makePrimary = true
                        )
                        if (goalId != null) {
                            planSaved = true
                            // Оновлюємо локальні дані
                            primaryGoal = supabaseRepo.getPrimaryGoal(userId)
                            strategicSteps = supabaseRepo.getStrategicSteps(goalId)
                            weeklyTasks = supabaseRepo.getWeeklyTasks(goalId, 1)
                            currentWeek = 1
                            Log.d(TAG, "✅ ChatScreen: Plan AUTO-SAVED with goalId: $goalId")
                        } else {
                            Log.e(TAG, "❌ ChatScreen: Failed to auto-save plan")
                        }

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

                        // Зберігаємо в chat_messages
                        primaryGoal?.let { goal ->
                            supabaseRepo.saveChatMessage(userId, goal.id, "assistant", savedGapAnalysis)
                        }

                        messages = messages + ChatMessage(
                            "assistant",
                            "📋 Генерую персоналізований план з 10 кроків...\n\n⏳ Це може зайняти до 30 секунд."
                        )

                        savedActionPlan = plan.strategicSteps.joinToString("\n\n") { step ->
                            buildString {
                                appendLine("КРОК ${step.number}: ${step.title}")
                                appendLine(step.description)
                                appendLine("⏰ Час: ${step.timeframe}")
                                appendLine("🔥 Пріоритет: ${if (step.number <= 3) "Високий" else "Середній"}")
                            }
                        }

                        messages = messages + ChatMessage("assistant", savedActionPlan)

                        // Зберігаємо в chat_messages
                        primaryGoal?.let { goal ->
                            supabaseRepo.saveChatMessage(userId, goal.id, "assistant", savedActionPlan)
                        }

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
                }
            },
            onCancel = {
                showAssessmentScreen = false
            }
        )
    }
    // ════════════════════════════════════════════════════════════════
    // UI: Chat Screen
    // ════════════════════════════════════════════════════════════════
    else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💬", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Чат з коучем",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                primaryGoal?.let { goal ->
                                    Text(
                                        text = goal.title,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToDashboard) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Welcome card якщо немає повідомлень
                        if (messages.isEmpty() && !isLoadingData && initialPlanContext == null) {
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

                        // Loading indicator
                        if (isLoadingData) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }

                        // Messages
                        items(messages) { message ->
                            MessageBubble(message)
                        }

                        // Loading indicator for AI response
                        if (isLoading) {
                            item {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Коуч думає...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Input field
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
                            enabled = !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            singleLine = false,
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    val userMessage = inputText
                                    messages = messages + ChatMessage("user", userMessage)
                                    inputText = ""
                                    isLoading = true

                                    scope.launch {
                                        try {
                                            // 🆕 Зберігаємо повідомлення користувача
                                            primaryGoal?.let { goal ->
                                                supabaseRepo.saveChatMessage(userId, goal.id, "user", userMessage)
                                            }

                                            // 🆕 Будуємо контекст
                                            val aiContext = buildCurrentContext()
                                            Log.d(TAG, "💬 AI Context built: ${aiContext?.take(200) ?: "NULL"}...")

                                            // 🆕 Відправляємо з контекстом
                                            val aiResponse = StringBuilder()
                                            if (aiContext != null) {
                                                geminiRepo.sendMessageWithContext(userMessage, aiContext).collect { chunk ->
                                                    aiResponse.append(chunk)
                                                }
                                            } else {
                                                geminiRepo.sendMessage(userMessage).collect { chunk ->
                                                    aiResponse.append(chunk)
                                                }
                                            }

                                            val responseText = aiResponse.toString()
                                            messages = messages + ChatMessage("assistant", responseText)

                                            // 🆕 Зберігаємо відповідь ШІ
                                            primaryGoal?.let { goal ->
                                                supabaseRepo.saveChatMessage(userId, goal.id, "assistant", responseText)
                                            }

                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Chat error: ${e.message}")
                                            messages = messages + ChatMessage(
                                                "assistant",
                                                "Вибачте, сталася помилка: ${e.message}"
                                            )
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading && inputText.isNotBlank(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("→", fontSize = 18.sp)
                        }
                    }
                }

                // ════════════════════════════════════════════════════════════════
                // FAB меню з кнопкою "📊 Прогрес"
                // ════════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showFabMenu && !isLoading) {
                        // 📊 Прогрес (ПОВЕРНУЛИ!)
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onBackToDashboard()
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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

                        // ⭐ Відгук
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
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
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

                        // 📋 Історія
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

                        // 🎯 Нова оцінка
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
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.role == "user") 16.dp else 4.dp,
                bottomEnd = if (message.role == "user") 4.dp else 16.dp
            ),
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