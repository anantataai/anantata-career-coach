package ai.anantata.careercoach

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Головний екран з ціллю та тижневими завданнями (v1.5)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDashboardScreen(
    userId: String,
    onOpenChat: () -> Unit,
    onOpenStrategy: () -> Unit,
    onOpenGoalsList: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartNewAssessment: () -> Unit
) {
    val supabaseRepo = remember { SupabaseRepository() }
    val geminiRepo = remember { GeminiRepository() }
    val scope = rememberCoroutineScope()

    // Стани
    var primaryGoal by remember { mutableStateOf<GoalItem?>(null) }
    var strategicSteps by remember { mutableStateOf<List<StrategicStepItem>>(emptyList()) }
    var weeklyTasks by remember { mutableStateOf<List<WeeklyTaskItem>>(emptyList()) }
    var currentWeek by remember { mutableStateOf(1) }
    var weekStats by remember { mutableStateOf(WeekStats(0, 0, 0, 0)) }
    var isLoading by remember { mutableStateOf(true) }
    var showWeekCompleteDialog by remember { mutableStateOf(false) }
    var isGeneratingNextWeek by remember { mutableStateOf(false) }

    // Завантажуємо дані при відкритті
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            // Отримуємо головну ціль
            primaryGoal = supabaseRepo.getPrimaryGoal(userId)

            primaryGoal?.let { goal ->
                // Отримуємо стратегічні кроки
                strategicSteps = supabaseRepo.getStrategicSteps(goal.id)

                // Отримуємо поточний тиждень
                currentWeek = supabaseRepo.getCurrentWeekNumber(goal.id).coerceAtLeast(1)

                // Отримуємо завдання поточного тижня
                weeklyTasks = supabaseRepo.getWeeklyTasks(goal.id, currentWeek)

                // Отримуємо статистику
                weekStats = supabaseRepo.getWeekStats(goal.id, currentWeek)
            }
        } catch (e: Exception) {
            println("❌ Error loading dashboard: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Функція оновлення статусу завдання
    fun updateTaskStatus(task: WeeklyTaskItem, newStatus: String) {
        scope.launch {
            val success = supabaseRepo.updateTaskStatus(task.id, newStatus)
            if (success) {
                // Оновлюємо локальний список
                weeklyTasks = weeklyTasks.map {
                    if (it.id == task.id) it.copy(status = newStatus) else it
                }

                // Оновлюємо статистику
                primaryGoal?.let { goal ->
                    weekStats = supabaseRepo.getWeekStats(goal.id, currentWeek)

                    // Перевіряємо чи завершено тиждень
                    if (weekStats.isComplete) {
                        showWeekCompleteDialog = true
                    }
                }
            }
        }
    }

    // Функція генерації наступного тижня
    fun generateNextWeek() {
        scope.launch {
            isGeneratingNextWeek = true
            showWeekCompleteDialog = false

            try {
                primaryGoal?.let { goal ->
                    val completedTasks = weeklyTasks.filter { it.status == "done" }
                    val skippedTasks = weeklyTasks.filter { it.status == "skipped" }

                    // Генеруємо нові завдання
                    val newTasks = geminiRepo.generateNextWeekTasks(
                        goalTitle = goal.title,
                        targetSalary = goal.targetSalary,
                        strategicSteps = strategicSteps,
                        completedTasks = completedTasks,
                        skippedTasks = skippedTasks,
                        currentWeek = currentWeek + 1
                    )

                    // Зберігаємо в базу
                    val saved = supabaseRepo.saveWeeklyTasks(goal.id, currentWeek + 1, newTasks)

                    if (saved) {
                        currentWeek += 1
                        weeklyTasks = supabaseRepo.getWeeklyTasks(goal.id, currentWeek)
                        weekStats = supabaseRepo.getWeekStats(goal.id, currentWeek)
                    }
                }
            } catch (e: Exception) {
                println("❌ Error generating next week: ${e.message}")
            } finally {
                isGeneratingNextWeek = false
            }
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мій прогрес") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Text("📋", fontSize = 20.sp)
                    }
                    IconButton(onClick = onOpenGoalsList) {
                        Text("📁", fontSize = 20.sp)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenChat,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text("💬", fontSize = 24.sp)
            }
        }
    ) { paddingValues ->

        if (isLoading) {
            // Лоадер
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (primaryGoal == null) {
            // Немає цілі — пропонуємо створити
            NoGoalScreen(
                onStartAssessment = onStartNewAssessment,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            // Головний контент
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Картка цілі
                item {
                    GoalCard(
                        goal = primaryGoal!!,
                        onOpenStrategy = onOpenStrategy
                    )
                }

                // Заголовок тижня з прогресом
                item {
                    WeekHeader(
                        weekNumber = currentWeek,
                        stats = weekStats
                    )
                }

                // Список завдань
                if (weeklyTasks.isEmpty()) {
                    item {
                        EmptyTasksCard()
                    }
                } else {
                    items(weeklyTasks) { task ->
                        TaskItemCard(
                            task = task,
                            onStatusChange = { newStatus ->
                                updateTaskStatus(task, newStatus)
                            }
                        )
                    }
                }

                // Кнопка генерації наступного тижня (якщо всі виконані)
                if (weekStats.isComplete && !isGeneratingNextWeek) {
                    item {
                        GenerateNextWeekButton(
                            onClick = { showWeekCompleteDialog = true }
                        )
                    }
                }

                // Індикатор генерації
                if (isGeneratingNextWeek) {
                    item {
                        GeneratingWeekIndicator()
                    }
                }

                // Відступ знизу для FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Діалог завершення тижня
    if (showWeekCompleteDialog) {
        WeekCompleteDialog(
            weekNumber = currentWeek,
            stats = weekStats,
            onDismiss = { showWeekCompleteDialog = false },
            onGenerateNext = { generateNextWeek() },
            onDiscussWithCoach = {
                showWeekCompleteDialog = false
                onOpenChat()
            }
        )
    }
}

/**
 * Екран коли немає цілі
 */
@Composable
fun NoGoalScreen(
    onStartAssessment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎯",
                    fontSize = 64.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Почни свій шлях!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Пройди оцінку щоб отримати персональний план з 10 кроків до твоєї мети",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onStartAssessment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚀 Почати оцінку", fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Картка головної цілі
 */
@Composable
fun GoalCard(
    goal: GoalItem,
    onOpenStrategy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⭐",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ГОЛОВНА ЦІЛЬ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = goal.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = goal.targetSalary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenStrategy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📋 Переглянути стратегію")
            }
        }
    }
}

/**
 * Заголовок тижня з прогресом
 */
@Composable
fun WeekHeader(
    weekNumber: Int,
    stats: WeekStats
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📅 Тиждень $weekNumber",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Виконано: ${stats.done}/${stats.total}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Прогрес-бар
        LinearProgressIndicator(
            progress = { stats.progressPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = when {
                stats.progressPercent >= 80 -> Color(0xFF4CAF50) // Зелений
                stats.progressPercent >= 50 -> Color(0xFFFFC107) // Жовтий
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (stats.skipped > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "⏭️ Пропущено: ${stats.skipped}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Один елемент завдання (картка)
 */
@Composable
fun TaskItemCard(
    task: WeeklyTaskItem,
    onStatusChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (task.status) {
                "done" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                "skipped" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Чекбокс/статус (emoji замість іконок)
                TaskStatusButton(
                    status = task.status,
                    onToggle = {
                        when (task.status) {
                            "pending" -> onStatusChange("done")
                            "done" -> onStatusChange("pending")
                            "skipped" -> onStatusChange("pending")
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Номер і назва
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${task.taskNumber}. ${task.title}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.status == "done") TextDecoration.LineThrough else null,
                        color = if (task.status == "done" || task.status == "skipped")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Кнопка "пропустити"
                if (task.status == "pending") {
                    IconButton(
                        onClick = { onStatusChange("skipped") }
                    ) {
                        Text("⏭️", fontSize = 20.sp)
                    }
                }
            }

            // Опис (розгорнутий)
            if (expanded && task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = task.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Кнопка статусу завдання (emoji)
 */
@Composable
fun TaskStatusButton(
    status: String,
    onToggle: () -> Unit
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(40.dp)
    ) {
        Text(
            text = when (status) {
                "done" -> "✅"
                "skipped" -> "⏭️"
                else -> "🔲"
            },
            fontSize = 24.sp
        )
    }
}

/**
 * Пуста картка коли немає завдань
 */
@Composable
fun EmptyTasksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📝",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Завдання ще не згенеровані",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Кнопка генерації наступного тижня
 */
@Composable
fun GenerateNextWeekButton(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎉",
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Тиждень завершено! Далі →",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * Індикатор генерації тижня
 */
@Composable
fun GeneratingWeekIndicator() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Генерую завдання на наступний тиждень...",
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Діалог завершення тижня
 */
@Composable
fun WeekCompleteDialog(
    weekNumber: Int,
    stats: WeekStats,
    onDismiss: () -> Unit,
    onGenerateNext: () -> Unit,
    onDiscussWithCoach: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text(text = "🎉", fontSize = 48.sp)
        },
        title = {
            Text(
                text = "Тиждень $weekNumber завершено!",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "✅ Виконано: ${stats.done}/10 завдань"
                )
                if (stats.skipped > 0) {
                    Text(
                        text = "⏭️ Пропущено: ${stats.skipped}"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Готовий до наступного рівня?",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(onClick = onGenerateNext) {
                Text("🚀 Тиждень ${weekNumber + 1}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscussWithCoach) {
                Text("💬 Обговорити")
            }
        }
    )
}