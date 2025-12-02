package ai.anantata.careercoach

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
 * Екран стратегії — 10 напрямків до мети (v2.0)
 * 🆕 Оновлено на нову термінологію: directions замість strategicSteps
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
    var directions by remember { mutableStateOf<List<DirectionItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 🆕 v2.0: Поточний блок та прогрес
    var currentBlock by remember { mutableStateOf(1) }
    var goalProgress by remember { mutableStateOf<GoalProgress?>(null) }

    // Завантажуємо дані
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            primaryGoal = supabaseRepo.getPrimaryGoal(userId)
            primaryGoal?.let { goal ->
                // 🆕 v2.0: Отримуємо напрямки
                directions = supabaseRepo.getDirections(goal.id, blockNumber = 1)

                // Отримуємо поточний блок
                currentBlock = supabaseRepo.getCurrentBlockNumber(goal.id).coerceAtLeast(1)

                // Отримуємо загальний прогрес
                goalProgress = supabaseRepo.calculateGoalProgress(goal.id)
            }
        } catch (e: Exception) {
            println("Error loading strategy: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Функція оновлення статусу напрямку
    fun updateDirectionStatus(direction: DirectionItem, newStatus: String) {
        scope.launch {
            val success = supabaseRepo.updateDirectionStatus(direction.id, newStatus)
            if (success) {
                directions = directions.map {
                    if (it.id == direction.id) it.copy(status = newStatus) else it
                }
                // 🆕 v2.0: Оновлюємо загальний прогрес
                primaryGoal?.let { goal ->
                    goalProgress = supabaseRepo.calculateGoalProgress(goal.id)
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
        } else if (primaryGoal == null || directions.isEmpty()) {
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
                // 🆕 v2.0: Заголовок з ціллю та прогресом
                item {
                    StrategyHeaderWithProgress(
                        goal = primaryGoal!!,
                        progress = goalProgress,
                        currentBlock = currentBlock,
                        directionsCompleted = directions.count { it.status == "done" },
                        directionsInProgress = directions.count { it.status == "in_progress" },
                        totalDirections = directions.size
                    )
                }

                // 🆕 v2.0: Легенда блоків
                item {
                    BlockLegendCard(currentBlock = currentBlock)
                }

                // Список напрямків
                items(directions) { direction ->
                    DirectionCardWithBlock(
                        direction = direction,
                        currentBlock = currentBlock,
                        onStatusChange = { newStatus ->
                            updateDirectionStatus(direction, newStatus)
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
 * 🆕 v2.0: Заголовок стратегії з детальним прогресом
 */
@Composable
fun StrategyHeaderWithProgress(
    goal: GoalItem,
    progress: GoalProgress?,
    currentBlock: Int,
    directionsCompleted: Int,
    directionsInProgress: Int,
    totalDirections: Int
) {
    val directionsProgressPercent = if (totalDirections > 0) {
        ((directionsCompleted * 100) + (directionsInProgress * 50)) / totalDirections
    } else 0

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

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = goal.targetSalary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🆕 v2.0: Поточний блок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "📦 Блок $currentBlock",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Прогрес напрямків
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
                    text = "$directionsProgressPercent%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { directionsProgressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    directionsProgressPercent >= 80 -> Color(0xFF4CAF50)
                    directionsProgressPercent >= 50 -> Color(0xFFFFC107)
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Статистика напрямків
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "✅", fontSize = 20.sp)
                    Text(
                        text = "$directionsCompleted",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "Завершено",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔄", fontSize = 20.sp)
                    Text(
                        text = "$directionsInProgress",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107)
                    )
                    Text(
                        text = "В процесі",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "⏳", fontSize = 20.sp)
                    Text(
                        text = "${totalDirections - directionsCompleted - directionsInProgress}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Очікує",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * 🆕 v2.0: Легенда блоків
 */
@Composable
fun BlockLegendCard(currentBlock: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Активний напрямок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Активний",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Майбутній напрямок
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Очікує",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Поточний блок
            Text(
                text = "📦 Блок $currentBlock",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 🆕 v2.0: Картка напрямку з блоком
 */
@Composable
fun DirectionCardWithBlock(
    direction: DirectionItem,
    currentBlock: Int,
    onStatusChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Визначаємо чи напрямок активний
    val isActiveNow = direction.blockNumber == currentBlock
    val isPast = direction.blockNumber < currentBlock
    val isFuture = direction.blockNumber > currentBlock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                direction.status == "done" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                direction.status == "in_progress" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                isActiveNow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActiveNow && direction.status != "done") 4.dp else 1.dp
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
                // Номер напрямку з індикатором активності
                DirectionNumberBadge(
                    number = direction.directionNumber,
                    status = direction.status,
                    isActiveNow = isActiveNow
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Контент
                Column(modifier = Modifier.weight(1f)) {
                    // Назва напрямку
                    Text(
                        text = direction.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (direction.status == "done")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 🆕 v2.0: Блок напрямку
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Іконка
                        Text(
                            text = if (isActiveNow) "🟢" else if (isPast) "✓" else "📦",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        // Блок
                        Text(
                            text = "Блок ${direction.blockNumber}",
                            fontSize = 13.sp,
                            color = if (isActiveNow)
                                Color(0xFF4CAF50)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isActiveNow) FontWeight.Medium else FontWeight.Normal
                        )

                        // Кількість кроків
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• 10 кроків",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // Позначка "зараз"
                        if (isActiveNow && direction.status != "done") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text(
                                    text = "АКТИВНИЙ",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Опис (розгорнутий)
                    if (expanded && direction.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = direction.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Кнопка статусу
                DirectionStatusButton(
                    status = direction.status,
                    onToggle = {
                        val newStatus = when (direction.status) {
                            "pending" -> "in_progress"
                            "in_progress" -> "done"
                            "done" -> "pending"
                            else -> "pending"
                        }
                        onStatusChange(newStatus)
                    }
                )
            }

            // Кнопки вибору статусу (розгорнуті)
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        text = "⏳ Очікує",
                        isSelected = direction.status == "pending",
                        onClick = { onStatusChange("pending") }
                    )
                    StatusChip(
                        text = "🔄 В процесі",
                        isSelected = direction.status == "in_progress",
                        onClick = { onStatusChange("in_progress") }
                    )
                    StatusChip(
                        text = "✅ Виконано",
                        isSelected = direction.status == "done",
                        onClick = { onStatusChange("done") }
                    )
                }
            }
        }
    }
}

/**
 * 🆕 v2.0: Бейдж з номером напрямку та індикатором активності
 */
@Composable
fun DirectionNumberBadge(
    number: Int,
    status: String,
    isActiveNow: Boolean
) {
    val backgroundColor = when {
        status == "done" -> Color(0xFF4CAF50)
        status == "in_progress" -> Color(0xFFFFC107)
        isActiveNow -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box {
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

        // Індикатор активності
        if (isActiveNow && status != "done") {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF4CAF50))
                    .align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * 🆕 v2.0: Кнопка статусу напрямку
 */
@Composable
fun DirectionStatusButton(
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