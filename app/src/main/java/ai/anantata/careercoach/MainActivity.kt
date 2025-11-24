package ai.anantata.careercoach

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anantata.careercoach.ui.theme.AnantataCoachTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "anantata_prefs"
    private val ONBOARDING_COMPLETED = "onboarding_completed"
    private val FIRST_ASSESSMENT_COMPLETED = "first_assessment_completed"
    private val USER_ID_KEY = "user_device_id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = getOrCreateUserId()

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

    // Стан для перегляду збереженого результату
    var viewingHistoryItem by remember { mutableStateOf<AssessmentHistoryItem?>(null) }

    // Стан для переходу в чат з контекстом плану
    var chatWithPlanContext by remember { mutableStateOf<AssessmentHistoryItem?>(null) }

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
                }
            )
        }

        // Показ збереженого результату з історії
        viewingHistoryItem != null -> {
            val item = viewingHistoryItem!!
            val parsedResult = parseAssessmentResults(item.gapAnalysis, item.actionPlan)

            // Парсимо відповіді для шерінгу
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
                },
                onDiscussPlan = {
                    // Перехід в чат з контекстом цього плану
                    chatWithPlanContext = item
                    viewingHistoryItem = null
                }
            )
        }

        showHistory -> {
            AssessmentHistoryScreen(
                userId = userId,
                onBack = { showHistory = false },
                onViewResult = { historyItem ->
                    viewingHistoryItem = historyItem
                    showHistory = false
                },
                onDiscussPlan = { historyItem ->
                    // Перехід в чат з контекстом плану
                    chatWithPlanContext = historyItem
                    showHistory = false
                }
            )
        }

        else -> {
            ChatScreen(
                userId = userId,
                onOpenHistory = { showHistory = true },
                initialPlanContext = chatWithPlanContext,
                onPlanContextConsumed = { chatWithPlanContext = null }
            )
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

    // Зберігаємо відповіді для шерінгу
    var savedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var savedGapAnalysis by remember { mutableStateOf("") }
    var savedActionPlan by remember { mutableStateOf("") }

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
            },
            onDiscussPlan = {
                // Після першого assessment — просто завершуємо і переходимо в чат
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
                // Зберігаємо відповіді
                savedAnswers = answersMap

                scope.launch {
                    isProcessing = true

                    try {
                        val questions = geminiRepo.generateAssessmentQuestions("Повну")
                        val gapAnalysis = geminiRepo.analyzeCareerGap(answersMap, questions)
                        savedGapAnalysis = gapAnalysis
                        supabaseRepo.saveMessage(conversationId, "assistant", gapAnalysis)

                        val actionPlan = geminiRepo.generateActionPlan(answersMap, questions, gapAnalysis)
                        savedActionPlan = actionPlan
                        supabaseRepo.saveMessage(conversationId, "assistant", actionPlan)

                        assessmentResult = parseAssessmentResults(gapAnalysis, actionPlan)

                        assessmentResult?.let { result ->
                            supabaseRepo.saveAssessmentResult(
                                userId = userId,
                                matchScore = result.matchScore,
                                gapAnalysis = gapAnalysis,
                                actionPlan = actionPlan,
                                answers = answersMap
                            )
                        }

                        showResultsScreen = true

                    } catch (e: Exception) {
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
    initialPlanContext: AssessmentHistoryItem? = null,
    onPlanContextConsumed: () -> Unit = {}
) {
    val geminiRepo = remember { GeminiRepository() }
    val supabaseRepo = remember { SupabaseRepository() }
    val conversationId = remember { java.util.UUID.randomUUID().toString() }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAssessmentScreen by remember { mutableStateOf(false) }
    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    // Зберігаємо відповіді для шерінгу
    var savedAnswers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    // Останній результат для привітання
    var latestAssessment by remember { mutableStateOf<AssessmentHistoryItem?>(null) }
    var isLoadingHistory by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Завантажуємо останній результат при відкритті
    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)

        // Завантажуємо історію щоб отримати останній результат
        val history = supabaseRepo.getAssessmentHistory(userId)
        latestAssessment = history.firstOrNull()
        isLoadingHistory = false
    }

    // Обробляємо контекст плану якщо переходимо з історії/результатів
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
            },
            onDiscussPlan = {
                showResultsScreen = false
                // Додаємо план в чат
                val goalAnswer = savedAnswers[8] ?: "Досягти кар'єрної мети"
                val salaryAnswer = savedAnswers[9] ?: "Збільшити дохід"

                // Отримуємо actionPlan з результату
                val actionPlanText = assessmentResult?.actionSteps?.joinToString("\n") { step ->
                    "Крок ${step.number}: ${step.title}\n${step.description}"
                } ?: ""

                val contextMessage = generatePlanContext(goalAnswer, salaryAnswer, actionPlanText)
                messages = messages + ChatMessage("assistant", contextMessage)
            }
        )
    }
    else if (showAssessmentScreen) {
        AssessmentScreenUI(
            assessmentType = "Повну",
            geminiRepo = geminiRepo,
            onComplete = { answersMap ->
                // Зберігаємо відповіді
                savedAnswers = answersMap

                scope.launch {
                    val questions = geminiRepo.generateAssessmentQuestions("Повну")

                    showAssessmentScreen = false

                    messages = messages + ChatMessage(
                        "assistant",
                        "✅ Оцінку завершено! Аналізую ваш профіль..."
                    )

                    isLoading = true

                    try {
                        val gapAnalysis = geminiRepo.analyzeCareerGap(answersMap, questions)
                        messages = messages + ChatMessage("assistant", gapAnalysis)
                        supabaseRepo.saveMessage(conversationId, "assistant", gapAnalysis)

                        listState.animateScrollToItem(messages.size - 1)

                        messages = messages + ChatMessage(
                            "assistant",
                            "📋 Генерую персоналізований план з 10 кроків...\n\n⏳ Це може зайняти до 30 секунд."
                        )

                        listState.animateScrollToItem(messages.size - 1)

                        val actionPlan = geminiRepo.generateActionPlan(answersMap, questions, gapAnalysis)
                        messages = messages + ChatMessage("assistant", actionPlan)
                        supabaseRepo.saveMessage(conversationId, "assistant", actionPlan)

                        assessmentResult = parseAssessmentResults(gapAnalysis, actionPlan)

                        assessmentResult?.let { result ->
                            supabaseRepo.saveAssessmentResult(
                                userId = userId,
                                matchScore = result.matchScore,
                                gapAnalysis = gapAnalysis,
                                actionPlan = actionPlan,
                                answers = answersMap
                            )
                        }

                        showResultsScreen = true

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
            },
            onCancel = {
                showAssessmentScreen = false
                messages = messages + ChatMessage("assistant", "Оцінку скасовано.")
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
                    // Привітальне повідомлення
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showFabMenu) {
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
                            Text("🎯", fontSize = 20.sp)  // Нова іконка!
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Нова оцінка", fontSize = 14.sp)
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu }
                ) {
                    Icon(
                        imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Меню"
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
                // Персоналізоване привітання
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
                // Привітання для нового користувача
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
            modifier = Modifier.widthIn(max = 280.dp)
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