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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.anantata.careercoach.ui.theme.AnantataCoachTheme
import kotlinx.coroutines.launch

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

        showHistory -> {
            AssessmentHistoryScreen(
                userId = userId,
                onBack = { showHistory = false },
                onViewResult = { /* TODO */ }
            )
        }

        else -> {
            ChatScreen(
                userId = userId,
                onOpenHistory = { showHistory = true }
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

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        supabaseRepo.createConversation(conversationId)
    }

    if (showResultsScreen && assessmentResult != null) {
        AssessmentResultsScreen(
            result = assessmentResult!!,
            onBackToChat = { onComplete() },
            onRetakeAssessment = {
                showResultsScreen = false
                assessmentResult = null
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
        // ВИКЛИК ФУНКЦІЇ З AssessmentScreen.kt
        AssessmentScreenUI(
            assessmentType = "Повну",
            geminiRepo = geminiRepo,
            onComplete = { answersMap ->
                scope.launch {
                    isProcessing = true

                    try {
                        val questions = geminiRepo.generateAssessmentQuestions("Повну")
                        val gapAnalysis = geminiRepo.analyzeCareerGap(answersMap, questions)
                        supabaseRepo.saveMessage(conversationId, "assistant", gapAnalysis)

                        val actionPlan = geminiRepo.generateActionPlan(answersMap, questions, gapAnalysis)
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
    onOpenHistory: () -> Unit
) {
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
    var assessmentResult by remember { mutableStateOf<ParsedAssessmentResult?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    if (showResultsScreen && assessmentResult != null) {
        AssessmentResultsScreen(
            result = assessmentResult!!,
            onBackToChat = { showResultsScreen = false },
            onRetakeAssessment = {
                showResultsScreen = false
                assessmentResult = null
                showAssessmentDialog = true
            }
        )
    }
    else if (showAssessmentScreen) {
        // ВИКЛИК ФУНКЦІЇ З AssessmentScreen.kt
        AssessmentScreenUI(
            assessmentType = "Повну",
            geminiRepo = geminiRepo,
            onComplete = { answersMap ->
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
                            Text("🎤", fontSize = 20.sp)
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