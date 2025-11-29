package ai.anantata.careercoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentHistoryScreen(
    userId: String,
    onBack: () -> Unit,
    onViewResult: (AssessmentHistoryItem) -> Unit,
    onDiscussPlan: (AssessmentHistoryItem) -> Unit
) {
    val supabaseRepo = remember { SupabaseRepository() }
    var assessments by remember { mutableStateOf<List<AssessmentHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Завантажити історію при відкритті екрану
    LaunchedEffect(Unit) {
        isLoading = true
        assessments = supabaseRepo.getAssessmentHistory(userId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("МОЇ РЕЗУЛЬТАТИ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                // Loading state
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (assessments.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📋",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Історія порожня",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пройдіть першу оцінку щоб побачити результати тут",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                // List with results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(assessments) { assessment ->
                        AssessmentHistoryCard(
                            assessment = assessment,
                            onView = { onViewResult(assessment) },
                            onShare = {
                                // Парсимо відповіді для шерінгу
                                val answersMap = parseAnswersFromJson(assessment.answers)
                                val goalAnswer = answersMap["8"] ?: "Досягти кар'єрної мети"
                                val salaryAnswer = answersMap["9"] ?: "Збільшити дохід"

                                shareResult(
                                    context = context,
                                    goalAnswer = goalAnswer,
                                    salaryAnswer = salaryAnswer
                                )
                            },
                            onDiscuss = { onDiscussPlan(assessment) },
                            onDelete = { itemToDelete = assessment.id }
                        )
                    }

                    // Кнопка "Видалити всі дані" внизу
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Видалити всі дані")
                        }
                    }
                }
            }
        }
    }

    // Діалог видалення одного результату
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Видалити результат?") },
            text = { Text("Цю дію не можна буде скасувати.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = supabaseRepo.deleteAssessment(itemToDelete!!)
                            if (success) {
                                // Оновити список
                                assessments = supabaseRepo.getAssessmentHistory(userId)
                            }
                            itemToDelete = null
                        }
                    }
                ) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

    // Діалог видалення всіх даних
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("⚠️ Видалити всі дані?") },
            text = {
                Text(
                    "Будуть видалені ВСІ ваші результати оцінок.\n\n" +
                            "Цю дію НЕ МОЖНА буде скасувати!"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = supabaseRepo.deleteAllUserData(userId)
                            if (success) {
                                // Очистити список
                                assessments = emptyList()
                            }
                            showDeleteAllDialog = false
                        }
                    }
                ) {
                    Text("Видалити все", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun AssessmentHistoryCard(
    assessment: AssessmentHistoryItem,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDiscuss: () -> Unit,
    onDelete: () -> Unit
) {
    // Парсимо відповіді для відображення мети і ЗП
    val answersMap = parseAnswersFromJson(assessment.answers)
    val goalAnswer = answersMap["8"] ?: "Кар'єрна мета"
    val salaryAnswer = answersMap["9"] ?: ""

    // Скорочуємо назву мети для компактності
    val shortGoal = when {
        goalAnswer.contains("спеціаліст", ignoreCase = true) -> "Стати спеціалістом"
        goalAnswer.contains("керівник", ignoreCase = true) -> "Стати керівником"
        goalAnswer.contains("бізнес", ignoreCase = true) -> "Власний бізнес"
        goalAnswer.contains("змінити", ignoreCase = true) -> "Змінити сферу"
        else -> goalAnswer.take(25) + if (goalAnswer.length > 25) "..." else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Верхній рядок: Мета
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Мета - головний заголовок
                    Text(
                        text = shortGoal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // ЗП
                    if (salaryAnswer.isNotEmpty()) {
                        Text(
                            text = "💰 $salaryAnswer",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Кнопка видалення
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("×", fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Нижній рядок: Дата і Match Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Дата
                Text(
                    text = formatDate(assessment.createdAt),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Match Score з індикатором
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Відповідність: ${assessment.matchScore}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = getScoreColor(assessment.matchScore),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Переглянути
                Button(
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Переглянути", fontSize = 14.sp)
                }

                // Поділитися
                OutlinedButton(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Обговорити план
            Button(
                onClick = onDiscuss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("💬 Обговорити план", fontSize = 14.sp)
            }
        }
    }
}

/**
 * ВИПРАВЛЕННЯ #34: Форматування дати з конвертацією UTC → Київ
 */
fun formatDate(isoDate: String): String {
    return try {
        // Парсимо дату з UTC
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")

        // Форматуємо для відображення в часовому поясі Києва
        val outputFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("uk", "UA"))
        outputFormat.timeZone = TimeZone.getTimeZone("Europe/Kyiv")

        val date = inputFormat.parse(isoDate)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        // Якщо не вдалося розпарсити - просто показати як є
        isoDate.take(10) // yyyy-MM-dd
    }
}

/**
 * Колір індикатора по Match Score
 */
fun getScoreColor(score: Int): Color {
    return when {
        score >= 70 -> Color(0xFF10b981) // 🟢 Зелений
        score >= 50 -> Color(0xFFfbbf24) // 🟡 Жовтий
        else -> Color(0xFFef4444)         // 🔴 Червоний
    }
}