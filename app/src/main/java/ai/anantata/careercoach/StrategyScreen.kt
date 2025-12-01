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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Екран стратегії — 10 стратегічних кроків до мети
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyScreen(
    userId: String,
    onBack: () -> Unit
) {
    val supabaseRepo = remember { SupabaseRepository() }
    val scope = rememberCoroutineScope()

    // Стани
    var primaryGoal by remember { mutableStateOf<GoalItem?>(null) }
    var strategicSteps by remember { mutableStateOf<List<StrategicStepItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Завантажуємо дані
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            primaryGoal = supabaseRepo.getPrimaryGoal(userId)
            primaryGoal?.let { goal ->
                strategicSteps = supabaseRepo.getStrategicSteps(goal.id)
            }
        } catch (e: Exception) {
            println("Error loading strategy: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Функція оновлення статусу кроку
    fun updateStepStatus(step: StrategicStepItem, newStatus: String) {
        scope.launch {
            val success = supabaseRepo.updateStrategicStepStatus(step.id, newStatus)
            if (success) {
                strategicSteps = strategicSteps.map {
                    if (it.id == step.id) it.copy(status = newStatus) else it
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Стратегія") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (primaryGoal == null || strategicSteps.isEmpty()) {
            NoStrategyScreen(
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Заголовок з ціллю
                item {
                    StrategyHeader(
                        goal = primaryGoal!!,
                        stepsCompleted = strategicSteps.count { it.status == "done" },
                        totalSteps = strategicSteps.size
                    )
                }

                // Список стратегічних кроків
                items(strategicSteps) { step ->
                    StrategicStepCard(
                        step = step,
                        onStatusChange = { newStatus ->
                            updateStepStatus(step, newStatus)
                        }
                    )
                }

                // Відступ знизу
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Заголовок стратегії з прогресом
 */
@Composable
fun StrategyHeader(
    goal: GoalItem,
    stepsCompleted: Int,
    totalSteps: Int
) {
    val progressPercent = if (totalSteps > 0) (stepsCompleted * 100 / totalSteps) else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = goal.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = goal.targetSalary,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Прогрес стратегії:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "$stepsCompleted/$totalSteps кроків",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    progressPercent >= 80 -> Color(0xFF4CAF50)
                    progressPercent >= 50 -> Color(0xFFFFC107)
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
        }
    }
}

/**
 * Картка одного стратегічного кроку
 */
@Composable
fun StrategicStepCard(
    step: StrategicStepItem,
    onStatusChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (step.status) {
                "done" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                "in_progress" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
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
                verticalAlignment = Alignment.Top
            ) {
                // Номер кроку
                StepNumberBadge(
                    number = step.stepNumber,
                    status = step.status
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Контент
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (step.status == "done")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (step.timeframe.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.timeframe,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (expanded && step.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = step.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Кнопка статусу
                StepStatusButton(
                    status = step.status,
                    onToggle = {
                        val newStatus = when (step.status) {
                            "pending" -> "in_progress"
                            "in_progress" -> "done"
                            "done" -> "pending"
                            else -> "pending"
                        }
                        onStatusChange(newStatus)
                    }
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        text = "Очікує",
                        isSelected = step.status == "pending",
                        onClick = { onStatusChange("pending") }
                    )
                    StatusChip(
                        text = "В процесі",
                        isSelected = step.status == "in_progress",
                        onClick = { onStatusChange("in_progress") }
                    )
                    StatusChip(
                        text = "Виконано",
                        isSelected = step.status == "done",
                        onClick = { onStatusChange("done") }
                    )
                }
            }
        }
    }
}

/**
 * Бейдж з номером кроку
 */
@Composable
fun StepNumberBadge(
    number: Int,
    status: String
) {
    val backgroundColor = when (status) {
        "done" -> Color(0xFF4CAF50)
        "in_progress" -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Кнопка статусу кроку
 */
@Composable
fun StepStatusButton(
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
                "in_progress" -> "🔄"
                else -> "⏳"
            },
            fontSize = 24.sp
        )
    }
}

/**
 * Чіп вибору статусу
 */
@Composable
fun StatusChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Екран коли немає стратегії
 */
@Composable
fun NoStrategyScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "📋",
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Стратегія не знайдена",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Пройди оцінку щоб отримати персональний план",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}