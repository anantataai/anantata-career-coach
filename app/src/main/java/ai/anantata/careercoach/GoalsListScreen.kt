package ai.anantata.careercoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsListScreen(
    userId: String,
    supabaseRepo: SupabaseRepository,
    onBack: () -> Unit,
    onAddNewGoal: () -> Unit,
    onGoalSelected: (String) -> Unit // goalId
) {
    val scope = rememberCoroutineScope()

    var goals by remember { mutableStateOf<List<GoalItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf<GoalItem?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var isSettingPrimary by remember { mutableStateOf(false) }

    // Завантажити цілі при першому відкритті
    LaunchedEffect(Unit) {
        isLoading = true
        goals = supabaseRepo.getGoals(userId)
        isLoading = false
    }

    // Функція оновлення списку
    fun refreshGoals() {
        scope.launch {
            goals = supabaseRepo.getGoals(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📁 Мої цілі (${goals.size}/3)",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            when {
                isLoading -> {
                    // Завантаження
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                goals.isEmpty() -> {
                    // Немає цілей
                    NoGoalsContent(
                        onAddGoal = onAddNewGoal,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    // Список цілей
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(goals) { goal ->
                            GoalListItemCard(
                                goal = goal,
                                isSettingPrimary = isSettingPrimary,
                                onSetPrimary = {
                                    scope.launch {
                                        isSettingPrimary = true
                                        supabaseRepo.setPrimaryGoal(userId, goal.id)
                                        refreshGoals()
                                        isSettingPrimary = false
                                    }
                                },
                                onDelete = {
                                    showDeleteDialog = goal
                                },
                                onSelect = {
                                    onGoalSelected(goal.id)
                                }
                            )
                        }

                        // Кнопка додавання нової цілі (якщо менше 3)
                        if (goals.size < 3) {
                            item {
                                AddNewGoalCard(
                                    availableSlots = 3 - goals.size,
                                    onAdd = onAddNewGoal
                                )
                            }
                        }

                        // Відступ знизу
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    // Діалог підтвердження видалення
    showDeleteDialog?.let { goalToDelete ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) showDeleteDialog = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Видалити ціль?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ви впевнені, що хочете видалити ціль:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${goalToDelete.title}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠️ Це також видалить всі пов'язані стратегічні кроки та завдання!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            val success = supabaseRepo.deleteGoal(goalToDelete.id)
                            if (success) {
                                refreshGoals()
                            }
                            isDeleting = false
                            showDeleteDialog = null
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Видалити")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = null },
                    enabled = !isDeleting
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun GoalListItemCard(
    goal: GoalItem,
    isSettingPrimary: Boolean,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isPrimary) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (goal.isPrimary) 4.dp else 2.dp
        ),
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок з іконкою зірки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (goal.isPrimary) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Головна ціль",
                            tint = Color(0xFFFFB300), // Золотий колір
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Зарплата
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = goal.targetSalary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Статус
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when(goal.status) {
                        "active" -> "🔄"
                        "paused" -> "⏸️"
                        "completed" -> "✅"
                        else -> "📋"
                    },
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when(goal.status) {
                        "active" -> "Активна"
                        "paused" -> "На паузі"
                        "completed" -> "Завершена"
                        else -> goal.status
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопки дій
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка "Зробити головною" (якщо не головна)
                if (!goal.isPrimary) {
                    OutlinedButton(
                        onClick = onSetPrimary,
                        enabled = !isSettingPrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSettingPrimary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Головна",
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Placeholder для головної цілі
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Головна",
                                fontSize = 12.sp,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Кнопка видалення
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Видалити",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AddNewGoalCard(
    availableSlots: Int,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = onAdd
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Додати ціль",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Додати нову ціль",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "(доступно ще $availableSlots)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NoGoalsContent(
    onAddGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎯",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "У вас ще немає цілей",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Пройдіть оцінку щоб створити вашу першу кар'єрну ціль",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddGoal,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Пройти оцінку")
        }
    }
}