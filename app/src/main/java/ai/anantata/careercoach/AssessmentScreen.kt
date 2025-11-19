package ai.anantata.careercoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreenUI(
    assessmentType: String,
    geminiRepo: GeminiRepository,
    onComplete: (Map<Int, String>) -> Unit,
    onCancel: () -> Unit
) {
    var questions by remember { mutableStateOf<List<AssessmentQuestion>>(emptyList()) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var customInput by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            questions = geminiRepo.generateAssessmentQuestions(assessmentType)
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Завантаження питань...")
            }
        }
        return
    }

    if (questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не вдалося завантажити питання")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onCancel) {
                    Text("Назад")
                }
            }
        }
        return
    }

    val currentQuestion = questions[currentQuestionIndex]
    val progress = (currentQuestionIndex + 1).toFloat() / questions.size
    val hasAnswer = answers.containsKey(currentQuestion.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Career Assessment")
                        Text(
                            "Питання ${currentQuestionIndex + 1} з ${questions.size}",
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Скасувати")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = currentQuestion.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (currentQuestion.inputType == "select_or_custom") {
                    currentQuestion.options?.forEach { option ->
                        val isCustomOption = option.contains("Інше")
                        val isSelected = if (isCustomOption) {
                            showCustomInput
                        } else {
                            answers[currentQuestion.id] == option
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isCustomOption) {
                                    showCustomInput = true
                                    customInput = ""
                                } else {
                                    showCustomInput = false
                                    customInput = ""
                                    answers = answers + (currentQuestion.id to option)
                                }
                            },
                            label = { Text(option) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            label = {
                                Text(
                                    if (currentQuestion.id == 4) "Ким ви хочете стати?"
                                    else "Яку зарплату хочете?"
                                )
                            },
                            placeholder = {
                                Text(
                                    if (currentQuestion.id == 4) "наприклад: Президент США, Ілон Маск..."
                                    else "наприклад: $1,000,000/міс..."
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (customInput.isNotBlank()) {
                                    answers = answers + (currentQuestion.id to customInput)
                                    showCustomInput = false
                                }
                            },
                            enabled = customInput.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Підтвердити")
                        }
                    }
                } else {
                    currentQuestion.options?.forEach { option ->
                        val isSelected = answers[currentQuestion.id] == option

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                answers = answers + (currentQuestion.id to option)
                            },
                            label = { Text(option) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentQuestionIndex > 0) {
                    OutlinedButton(
                        onClick = {
                            currentQuestionIndex--
                            showCustomInput = false
                            customInput = ""
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Назад")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentQuestionIndex < questions.size - 1) {
                            currentQuestionIndex++
                            showCustomInput = false
                            customInput = ""
                        } else {
                            onComplete(answers)
                        }
                    },
                    enabled = hasAnswer && !showCustomInput
                ) {
                    Text(
                        if (currentQuestionIndex < questions.size - 1) "Далі" else "Завершити"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
    }
}