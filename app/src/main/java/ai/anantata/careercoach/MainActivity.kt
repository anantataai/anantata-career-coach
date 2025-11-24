package ai.anantata.careercoach

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anantata.careercoach.ui.theme.AnantataCoachTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "anantata_prefs"
    private val ONBOARDING_COMPLETED = "onboarding_completed"
    private val FIRST_ASSESSMENT_COMPLETED = "first_assessment_completed" // ДОДАНО

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnantataCoachTheme {
                MainApp(
                    isOnboardingCompleted = isOnboardingCompleted(),
                    isFirstAssessmentCompleted = isFirstAssessmentCompleted(), // ДОДАНО
                    onOnboardingComplete = { completeOnboarding() },
                    onFirstAssessmentComplete = { completeFirstAssessment() } // ДОДАНО
                )
            }
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ONBOARDING_COMPLETED, false)
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ONBOARDING_COMPLETED, true).apply()
    }

    // ДОДАНО: First assessment tracking
    private fun isFirstAssessmentCompleted(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(FIRST_ASSESSMENT_COMPLETED, false)
    }

    private fun completeFirstAssessment() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(FIRST_ASSESSMENT_COMPLETED, true).apply()
    }
}

// ОНОВЛЕНО: Тепер 3 стани - Onboarding, FirstAssessment, Chat
@Composable
fun MainApp(
    isOnboardingCompleted: Boolean,
    isFirstAssessmentCompleted: Boolean,
    onOnboardingComplete: () -> Unit,
    onFirstAssessmentComplete: () -> Unit
) {
    var showOnboarding by remember { mutableStateOf(!isOnboardingCompleted) }
    var showFirstAssessment by remember { mutableStateOf(!isFirstAssessmentCompleted && isOnboardingCompleted) }

    when {
        // КРОК 1: Показати Onboarding якщо не завершено
        showOnboarding -> {
            OnboardingScreen(
                onFinish = {
                    onOnboardingComplete()
                    showOnboarding = false
                    showFirstAssessment = true // Після onboarding → assessment
                }
            )
        }

        // КРОК 2: Показати перший Assessment одразу після Onboarding
        showFirstAssessment -> {
            FirstAssessmentFlow(
                onComplete = {
                    onFirstAssessmentComplete()
                    showFirstAssessment = false
                }
            )
        }

        // КРОК 3: Показати звичайний ChatScreen
        else -> {
            ChatScreen()
        }
    }
}

// НОВИЙ: Обгортка для першого Assessment без діалогу вибору
@Composable
fun FirstAssessmentFlow(
    onComplete: () -> Unit
) {
    val geminiRepo = remember { GeminiRepository() }
    val supabaseRepo = remember { SupabaseRepository() }
    val conversationId = remember { java.util.UUID.randomUUID().toString() }

    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)
    }

    if (showResultsScreen && assessmentResult != null) {
        // Показати результати
        AssessmentResultsScreen(
            result = assessmentResult!!,
            onBackToChat = {
                onComplete() // Завершити first assessment flow
            },
            onRetakeAssessment = {
                // Перепройти assessment
                showResultsScreen = false
                assessmentResult = null
            }
        )
    } else if (isProcessing) {
        // Показати loading під час обробки
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
        // Показати Assessment екран (ЗАВЖДИ повна версія - 15 питань)
        AssessmentScreenUI(
            assessmentType = "Повну", // ЗАВЖДИ повна версія
            geminiRepo = geminiRepo,
            onComplete = { answers ->
                scope.launch {
                    isProcessing = true

                    try {
                        val questions = geminiRepo.generateAssessmentQuestions("Повну")
                        val gapAnalysis = geminiRepo.analyzeCareerGap(answers, questions)

                        supabaseRepo.saveMessage(conversationId, "assistant", gapAnalysis)

                        val actionPlan = geminiRepo.generateActionPlan(answers, questions, gapAnalysis)
                        supabaseRepo.saveMessage(conversationId, "assistant", actionPlan)

                        assessmentResult = parseAssessmentResults(gapAnalysis, actionPlan)

                        showResultsScreen = true

                    } catch (e: Exception) {
                        // Якщо помилка - просто завершити flow
                        onComplete()
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onCancel = {
                // Якщо користувач скасував - все одно завершити flow
                onComplete()
            }
        )
    }
}

// ============================================
// ВСЯ РЕШТА КОДУ БЕЗ ЗМІН
// ============================================

@Composable
fun ChatScreen() {
    val geminiRepo = remember { GeminiRepository() }
    val supabaseRepo = remember { SupabaseRepository() }
    val conversationId = remember { java.util.UUID.randomUUID().toString() }

    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAssessmentDialog by remember { mutableStateOf(false) }
    var showAssessmentScreen by remember { mutableStateOf(false) }
    var showResultsScreen by remember { mutableStateOf(false) }
    var assessmentType by remember { mutableStateOf("") }
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    if (showResultsScreen && assessmentResult != null) {
        AssessmentResultsScreen(
            result = assessmentResult!!,
            onBackToChat = {
                showResultsScreen = false
            },
            onRetakeAssessment = {
                showResultsScreen = false
                assessmentResult = null
                showAssessmentDialog = true
            }
        )
    }
    else if (showAssessmentScreen) {
        AssessmentScreenUI(
            assessmentType = assessmentType,
            geminiRepo = geminiRepo,
            onComplete = { answers ->
                scope.launch {
                    val questions = geminiRepo.generateAssessmentQuestions(assessmentType)

                    showAssessmentScreen = false

                    messages = messages + ChatMessage(
                        "assistant",
                        "✅ Оцінку завершено! Аналізую ваш профіль..."
                    )

                    isLoading = true

                    try {
                        val gapAnalysis = geminiRepo.analyzeCareerGap(answers, questions)
                        messages = messages + ChatMessage("assistant", gapAnalysis)
                        supabaseRepo.saveMessage(conversationId, "assistant", gapAnalysis)

                        listState.animateScrollToItem(messages.size - 1)

                        messages = messages + ChatMessage(
                            "assistant",
                            "📋 Генерую персоналізований план з 10 кроків...\n\n⏳ Це може зайняти до 30 секунд."
                        )

                        listState.animateScrollToItem(messages.size - 1)

                        val actionPlan = geminiRepo.generateActionPlan(answers, questions, gapAnalysis)
                        messages = messages + ChatMessage("assistant", actionPlan)
                        supabaseRepo.saveMessage(conversationId, "assistant", actionPlan)

                        assessmentResult = parseAssessmentResults(gapAnalysis, actionPlan)

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
                    .padding(bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = { showAssessmentDialog = true }
                ) {
                    Text("🎤", fontSize = 24.sp)
                }
            }
        }

        if (showAssessmentDialog) {
            AssessmentDialog(
                onDismiss = { showAssessmentDialog = false },
                onStart = { type ->
                    showAssessmentDialog = false
                    assessmentType = type
                    showAssessmentScreen = true
                }
            )
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

@Composable
fun AssessmentDialog(
    onDismiss: () -> Unit,
    onStart: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Career Assessment") },
        text = {
            Column {
                Text(
                    text = "AI оцінить ваш кар'єрний профіль та дасть персоналізовані рекомендації",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onStart("Повну") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Повна оцінка", fontSize = 16.sp)
                        Text("15 питань • ~10 хвилин", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onStart("Швидку") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Швидка оцінка", fontSize = 16.sp)
                        Text("5 питань • ~3 хвилини", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

data class ChatMessage(
    val role: String,
    val content: String
)