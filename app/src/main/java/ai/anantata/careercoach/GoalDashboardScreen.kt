package ai.anantata.careercoach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Головний екран з ціллю та прогресом (v2.0)
 *
 * 🆕 v2.0: НОВА СТРУКТУРА
 * - БЛОК = 100 кроків (10 напрямків × 10 кроків)
 * - Без прив'язки до часу — працюй у своєму темпі
 * - Після 100 кроків → генерується новий блок
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDashboardScreen(
    userId: String,
    onOpenChat: () -> Unit,
    onOpenStrategy: () -> Unit,
    onOpenGoalsList: () -> Unit,
    onStartNewAssessment: () -> Unit
) {
    val supabaseRepo = remember { SupabaseRepository() }
    val geminiRepo = remember { GeminiRepository() }
    val scope = rememberCoroutineScope()

    // Стани
    var primaryGoal by remember { mutableStateOf<GoalItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showGenerateBlockDialog by remember { mutableStateOf(false) }
    var isGeneratingNextBlock by remember { mutableStateOf(false) }
    var showNeedCompleteDialog by remember { mutableStateOf(false) } // 🆕 Діалог "треба виконати 100 кроків"

    // 🆕 v2.0: Блоки та напрямки
    var currentBlock by remember { mutableStateOf(1) }
    var maxBlock by remember { mutableStateOf(1) }
    var directionsWithSteps by remember { mutableStateOf<List<DirectionWithSteps>>(emptyList()) }
    var blockStats by remember { mutableStateOf<BlockStats?>(null) }

    // Прогрес цілі
    var goalProgress by remember { mutableStateOf<GoalProgress?>(null) }

    // Стан розгорнутих напрямків
    val expandedDirections = remember { mutableStateListOf<String>() }

    // Стан розгорнутих кроків (для детального опису)
    val expandedSteps = remember { mutableStateListOf<String>() }

    // Стан генерації детального опису
    var generatingDescriptionForStep by remember { mutableStateOf<String?>(null) }

    // Завантажуємо дані
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            primaryGoal = supabaseRepo.getPrimaryGoal(userId)

            primaryGoal?.let { goal ->
                maxBlock = supabaseRepo.getMaxBlockNumber(goal.id).coerceAtLeast(1)
                currentBlock = maxBlock

                directionsWithSteps = supabaseRepo.getDirectionsWithSteps(goal.id, currentBlock)
                blockStats = supabaseRepo.getBlockStats(goal.id, currentBlock)
                goalProgress = supabaseRepo.calculateGoalProgress(goal.id)

                // Розгортаємо перший напрямок з невиконаними кроками
                expandedDirections.clear()
                directionsWithSteps
                    .firstOrNull { it.pendingCount > 0 }
                    ?.let { expandedDirections.add(it.direction.id) }
            }
        } catch (e: Exception) {
            println("❌ Error loading dashboard: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Функція завантаження блоку
    fun loadBlock(blockNumber: Int) {
        scope.launch {
            primaryGoal?.let { goal ->
                currentBlock = blockNumber
                directionsWithSteps = supabaseRepo.getDirectionsWithSteps(goal.id, blockNumber)
                blockStats = supabaseRepo.getBlockStats(goal.id, blockNumber)

                // Розгортаємо перший напрямок з невиконаними кроками
                expandedDirections.clear()
                directionsWithSteps
                    .firstOrNull { it.pendingCount > 0 }
                    ?.let { expandedDirections.add(it.direction.id) }
            }
        }
    }

    // Функція оновлення статусу кроку
    fun updateStepStatus(step: StepItem, newStatus: String) {
        scope.launch {
            val success = supabaseRepo.updateStepStatus(step.id, newStatus)
            if (success) {
                primaryGoal?.let { goal ->
                    // Оновлюємо дані
                    directionsWithSteps = supabaseRepo.getDirectionsWithSteps(goal.id, currentBlock)
                    blockStats = supabaseRepo.getBlockStats(goal.id, currentBlock)
                    goalProgress = supabaseRepo.calculateGoalProgress(goal.id)
                }
            }
        }
    }

    // Функція генерації детального опису кроку
    fun generateStepDescription(step: StepItem, directionTitle: String) {
        scope.launch {
            generatingDescriptionForStep = step.id

            try {
                primaryGoal?.let { goal ->
                    val detailedDescription = geminiRepo.generateStepDetails(
                        stepTitle = step.title,
                        stepDescription = step.description,
                        directionTitle = directionTitle,
                        goalTitle = goal.title
                    )

                    // Зберігаємо в БД
                    supabaseRepo.updateStepDetailedDescription(step.id, detailedDescription)

                    // Оновлюємо локально
                    directionsWithSteps = directionsWithSteps.map { dws ->
                        dws.copy(
                            steps = dws.steps.map { s ->
                                if (s.id == step.id) {
                                    s.copy(detailedDescription = detailedDescription)
                                } else s
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                println("❌ Error generating step description: ${e.message}")
            } finally {
                generatingDescriptionForStep = null
            }
        }
    }

    // Функція генерації наступного блоку
    fun generateNextBlock() {
        scope.launch {
            isGeneratingNextBlock = true
            showGenerateBlockDialog = false

            try {
                primaryGoal?.let { goal ->
                    val directions = directionsWithSteps.map { it.direction }
                    val allSteps = directionsWithSteps.flatMap { it.steps }
                    val completedSteps = allSteps.filter { it.status == "done" }
                    val skippedSteps = allSteps.filter { it.status == "skipped" }

                    val newPlan = geminiRepo.generateNextBlock(
                        goalTitle = goal.title,
                        targetSalary = goal.targetSalary,
                        previousDirections = directions,
                        completedSteps = completedSteps,
                        skippedSteps = skippedSteps,
                        blockNumber = maxBlock + 1
                    )

                    val saved = supabaseRepo.saveNextBlock(goal.id, newPlan, maxBlock + 1)

                    if (saved) {
                        maxBlock += 1
                        loadBlock(maxBlock)
                        goalProgress = supabaseRepo.calculateGoalProgress(goal.id)
                    }
                }
            } catch (e: Exception) {
                println("❌ Error generating next block: ${e.message}")
            } finally {
                isGeneratingNextBlock = false
            }
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мій прогрес") },
                actions = {
                    IconButton(onClick = onOpenGoalsList) {
                        Text("📋", fontSize = 20.sp)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (primaryGoal == null) {
            NoGoalScreen(
                onStartAssessment = onStartNewAssessment,
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
                // Картка цілі
                item {
                    GoalCardSimplified(
                        goal = primaryGoal!!,
                        progress = goalProgress
                    )
                }

                // 🆕 v2.0: Навігація по блоках
                item {
                    BlockNavigationHeader(
                        currentBlock = currentBlock,
                        maxBlock = maxBlock,
                        blockStats = blockStats,
                        onPreviousBlock = {
                            if (currentBlock > 1) loadBlock(currentBlock - 1)
                        },
                        onNextBlock = {
                            if (currentBlock < maxBlock) loadBlock(currentBlock + 1)
                        }
                    )
                }

                // Заголовок "10 напрямків, 100 кроків"
                item {
                    Text(
                        text = "10 НАПРЯМКІВ, 100 КРОКІВ ДО МЕТИ!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 🆕 v2.0: Список напрямків
                if (directionsWithSteps.isEmpty()) {
                    item {
                        EmptyBlockCard()
                    }
                } else {
                    items(directionsWithSteps) { directionWithSteps ->
                        val isExpanded = expandedDirections.contains(directionWithSteps.direction.id)

                        DirectionCard(
                            directionWithSteps = directionWithSteps,
                            isExpanded = isExpanded,
                            expandedSteps = expandedSteps,
                            generatingDescriptionForStep = generatingDescriptionForStep,
                            isCurrentBlock = currentBlock == maxBlock,
                            onToggleExpand = {
                                if (isExpanded) {
                                    expandedDirections.remove(directionWithSteps.direction.id)
                                } else {
                                    expandedDirections.add(directionWithSteps.direction.id)
                                }
                            },
                            onToggleStepExpand = { stepId ->
                                if (expandedSteps.contains(stepId)) {
                                    expandedSteps.remove(stepId)
                                } else {
                                    expandedSteps.add(stepId)
                                    // Генеруємо опис якщо його немає
                                    val step = directionWithSteps.steps.find { it.id == stepId }
                                    if (step != null && step.detailedDescription.isNullOrBlank()) {
                                        generateStepDescription(step, directionWithSteps.direction.title)
                                    }
                                }
                            },
                            onStepStatusChange = { step, newStatus ->
                                updateStepStatus(step, newStatus)
                            }
                        )
                    }
                }

                // 🆕 v2.0: Кнопка генерації наступного блоку
                if (currentBlock == maxBlock && !isGeneratingNextBlock) {
                    item {
                        GenerateNextBlockButton(
                            blockNumber = currentBlock,
                            isBlockComplete = blockStats?.isComplete == true,
                            onClick = {
                                // 🆕 Перевірка: чи виконано всі 100 кроків
                                if (blockStats?.isComplete == true) {
                                    showGenerateBlockDialog = true
                                } else {
                                    showNeedCompleteDialog = true
                                }
                            }
                        )
                    }
                }

                // Індикатор генерації
                if (isGeneratingNextBlock) {
                    item {
                        GeneratingBlockIndicator()
                    }
                }

                // Відступ знизу
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Діалог генерації нового блоку
    if (showGenerateBlockDialog) {
        GenerateBlockDialog(
            currentBlock = currentBlock,
            blockStats = blockStats,
            onDismiss = { showGenerateBlockDialog = false },
            onGenerateNext = { generateNextBlock() },
            onDiscussWithCoach = {
                showGenerateBlockDialog = false
                onOpenChat()
            }
        )
    }

    // 🆕 Діалог "треба виконати 100 кроків"
    if (showNeedCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showNeedCompleteDialog = false },
            icon = { Text(text = "🎯", fontSize = 48.sp) },
            title = {
                Text(
                    text = "Спочатку завершіть Блок ${currentBlock}",
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    val done = blockStats?.done ?: 0
                    val remaining = 100 - done

                    Text(
                        text = "Вам потрібно виконати ще $remaining кроків з 100, щоб розблокувати Блок ${currentBlock + 1}.",
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Прогрес
                    LinearProgressIndicator(
                        progress = { done.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFFE0E0E0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Прогрес: $done/100 (${done}%)",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showNeedCompleteDialog = false }) {
                    Text("Зрозуміло! 💪")
                }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 🆕 v2.0: НАВІГАЦІЯ ПО БЛОКАХ
// ════════════════════════════════════════════════════════════════

@Composable
fun BlockNavigationHeader(
    currentBlock: Int,
    maxBlock: Int,
    blockStats: BlockStats?,
    onPreviousBlock: () -> Unit,
    onNextBlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1) // 🆕 Яскравий жовтуватий замість сірого
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Навігація
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка "Попередній"
                IconButton(
                    onClick = onPreviousBlock,
                    enabled = currentBlock > 1,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "◀",
                        fontSize = 20.sp,
                        color = if (currentBlock > 1)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // БЛОК X
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "БЛОК $currentBlock",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (blockStats != null) {
                        Text(
                            text = "Виконано: ${blockStats.done}/100",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Кнопка "Наступний"
                IconButton(
                    onClick = onNextBlock,
                    enabled = currentBlock < maxBlock,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "▶",
                        fontSize = 20.sp,
                        color = if (currentBlock < maxBlock)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // Прогрес-бар блоку
            if (blockStats != null && blockStats.total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { blockStats.done.toFloat() / blockStats.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        blockStats.progressPercent >= 100 -> Color(0xFF4CAF50)
                        blockStats.progressPercent >= 50 -> Color(0xFFFFC107)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 🆕 v2.0: КАРТКА НАПРЯМКУ
// ════════════════════════════════════════════════════════════════

@Composable
fun DirectionCard(
    directionWithSteps: DirectionWithSteps,
    isExpanded: Boolean,
    expandedSteps: List<String>,
    generatingDescriptionForStep: String?,
    isCurrentBlock: Boolean,
    onToggleExpand: () -> Unit,
    onToggleStepExpand: (String) -> Unit,
    onStepStatusChange: (StepItem, String) -> Unit
) {
    val direction = directionWithSteps.direction
    val steps = directionWithSteps.steps
    val doneCount = directionWithSteps.doneCount
    val totalCount = directionWithSteps.totalCount

    val isComplete = doneCount == 10
    val hasProgress = doneCount > 0

    val cardColor = when {
        isComplete -> Color(0xFF4CAF50).copy(alpha = 0.15f) // Зелений для завершених
        hasProgress -> Color(0xFFFFF3E0) // 🆕 Яскравий помаранчевий для в прогресі
        else -> Color(0xFFFAFAFA) // 🆕 Світлий білий замість сірого
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasProgress) 4.dp else 1.dp
        )
    ) {
        Column {
            // ═══════════════════════════════════════════════════════
            // ЗАГОЛОВОК НАПРЯМКУ
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Номер напрямку
                DirectionNumberBadge(
                    number = direction.directionNumber,
                    isComplete = isComplete,
                    hasProgress = hasProgress
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Назва
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = direction.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Прогрес
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isComplete) {
                        Text(text = "✅", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "$doneCount/$totalCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isComplete -> Color(0xFF4CAF50)
                            hasProgress -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ═══════════════════════════════════════════════════════
            // РОЗГОРНУТИЙ КОНТЕНТ — КРОКИ
            // ═══════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEach { step ->
                        val isStepExpanded = expandedSteps.contains(step.id)
                        val isGenerating = generatingDescriptionForStep == step.id

                        StepItemCard(
                            step = step,
                            isExpanded = isStepExpanded,
                            isGeneratingDescription = isGenerating,
                            isEditable = isCurrentBlock,
                            onToggleExpand = { onToggleStepExpand(step.id) },
                            onStatusChange = { newStatus ->
                                onStepStatusChange(step, newStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectionNumberBadge(
    number: Int,
    isComplete: Boolean,
    hasProgress: Boolean
) {
    val backgroundColor = when {
        isComplete -> Color(0xFF4CAF50)
        hasProgress -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(backgroundColor, CircleShape),
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

// ════════════════════════════════════════════════════════════════
// 🆕 v2.0: КАРТКА КРОКУ
// ════════════════════════════════════════════════════════════════

@Composable
fun StepItemCard(
    step: StepItem,
    isExpanded: Boolean,
    isGeneratingDescription: Boolean,
    isEditable: Boolean,
    onToggleExpand: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (step.status) {
                "done" -> Color(0xFFE8F5E9) // 🆕 Яскравий зелений
                "skipped" -> Color(0xFFFFF8E1) // 🆕 Жовтуватий замість сірого
                else -> Color.White // 🆕 Білий замість сірого
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // 🆕 Більша тінь
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок кроку
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Чекбокс
                StepStatusButton(
                    status = step.status,
                    onToggle = {
                        if (isEditable) {
                            when (step.status) {
                                "pending" -> onStatusChange("done")
                                "done" -> onStatusChange("pending")
                                "skipped" -> onStatusChange("pending")
                            }
                        }
                    },
                    isEnabled = isEditable
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Назва кроку (клікабельна для розгортання)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Text(
                        text = "${step.localNumber}. ${step.title}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (step.status == "done") TextDecoration.LineThrough else null,
                        color = if (step.status == "done" || step.status == "skipped")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Кнопка пропустити
                if (step.status == "pending" && isEditable) {
                    IconButton(
                        onClick = { onStatusChange("skipped") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("⏭️", fontSize = 18.sp)
                    }
                }
            }

            // Розгорнутий детальний опис
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Роздільник
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isGeneratingDescription) {
                        // Генерується опис
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Генерую детальний опис...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else if (!step.detailedDescription.isNullOrBlank()) {
                        // Показуємо детальний опис
                        Text(
                            text = "📝 Детальний опис:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = step.detailedDescription,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    } else {
                        // Короткий опис (fallback)
                        Text(
                            text = step.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepStatusButton(
    status: String,
    onToggle: () -> Unit,
    isEnabled: Boolean = true
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(36.dp),
        enabled = isEnabled
    ) {
        Text(
            text = when (status) {
                "done" -> "✅"
                "skipped" -> "⏭️"
                else -> "🔲"
            },
            fontSize = 22.sp,
            color = if (isEnabled) Color.Unspecified else Color.Unspecified.copy(alpha = 0.5f)
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 🆕 v2.0: КНОПКА ГЕНЕРАЦІЇ НАСТУПНОГО БЛОКУ
// ════════════════════════════════════════════════════════════════

@Composable
fun GenerateNextBlockButton(
    blockNumber: Int,
    isBlockComplete: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlockComplete)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
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
                text = if (isBlockComplete) "🎉" else "🚀",
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isBlockComplete)
                    "Завершити блок → Генерувати Блок ${blockNumber + 1}"
                else
                    "Згенерувати Блок ${blockNumber + 1} →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBlockComplete)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun GeneratingBlockIndicator() {
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
                text = "Генерую новий блок (100 кроків)...",
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun GenerateBlockDialog(
    currentBlock: Int,
    blockStats: BlockStats?,
    onDismiss: () -> Unit,
    onGenerateNext: () -> Unit,
    onDiscussWithCoach: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(text = "🎉", fontSize = 48.sp) },
        title = {
            Text(
                text = "Генерувати Блок ${currentBlock + 1}?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (blockStats != null) {
                    Text(text = "✅ Виконано: ${blockStats.done} кроків")
                    if (blockStats.skipped > 0) {
                        Text(text = "⏭️ Пропущено: ${blockStats.skipped}")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ШІ створить новий блок з 10 напрямків та 100 кроків на основі вашого прогресу",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onGenerateNext) {
                Text("🚀 Генерувати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscussWithCoach) {
                Text("💬 Обговорити")
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════
// EXISTING COMPONENTS (спрощені)
// ════════════════════════════════════════════════════════════════

@Composable
fun GoalCardSimplified(
    goal: GoalItem,
    progress: GoalProgress?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4A90D9) // 🆕 Яскравий синій
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🎯 ${goal.title}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White // 🆕 Білий текст
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰 ${goal.targetSalary}",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f) // 🆕 Білий текст
                )

                if (progress != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Прогрес: ",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f) // 🆕 Білий текст
                        )
                        Text(
                            text = "${progress.overallPercent}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                progress.overallPercent >= 80 -> Color(0xFFAED581) // 🆕 Світло-зелений
                                progress.overallPercent >= 50 -> Color(0xFFFFE082) // 🆕 Світло-жовтий
                                else -> Color.White
                            }
                        )
                    }
                }
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.overallPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        progress.overallPercent >= 80 -> Color(0xFF81C784) // 🆕 Зелений
                        progress.overallPercent >= 50 -> Color(0xFFFFD54F) // 🆕 Жовтий
                        else -> Color.White
                    },
                    trackColor = Color.White.copy(alpha = 0.3f) // 🆕 Білий трек
                )
            }
        }
    }
}

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
                Text(text = "🎯", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Почни свій шлях!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Пройди оцінку щоб отримати персональний план: 10 напрямків, 100 кроків!",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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

@Composable
fun EmptyBlockCard() {
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
            Text(text = "📝", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Блок ще не згенеровано",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// DEPRECATED (для зворотної сумісності)
// ════════════════════════════════════════════════════════════════

@Deprecated("Use GoalCardSimplified")
@Composable
fun GoalCardWithProgress(goal: GoalItem, progress: GoalProgress?, onOpenStrategy: () -> Unit) {
    GoalCardSimplified(goal = goal, progress = progress)
}

@Deprecated("Use GoalCardSimplified")
@Composable
fun GoalCard(goal: GoalItem, onOpenStrategy: () -> Unit) {
    GoalCardSimplified(goal = goal, progress = null)
}