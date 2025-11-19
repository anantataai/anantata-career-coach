package ai.anantata.careercoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ParsedAssessmentResult(
    val matchScore: Int,
    val strengths: List<String>,
    val gaps: List<String>,
    val expectedSalary: String,
    val timeToGoal: String,
    val actionSteps: List<ParsedActionStep>
)

data class ParsedActionStep(
    val number: Int,
    val title: String,
    val description: String,
    val timeEstimate: String,
    val priority: String
)

fun parseAssessmentResults(gapAnalysis: String, actionPlan: String): ParsedAssessmentResult {
    val matchScore = Regex("Match Score[:\\s]+(\\d+)%")
        .find(gapAnalysis)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 75

    val strengths = mutableListOf<String>()
    val strengthsSection = Regex(
        "СИЛЬНІ СТОРОНИ:(.*?)(?=ЩО ПОТРІБНО|GAPS|$)",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).find(gapAnalysis)?.groupValues?.getOrNull(1)

    strengthsSection?.split("\n")?.forEach { line ->
        val cleaned = line.trim()
            .removePrefix("-").removePrefix("•").removePrefix("*")
            .removePrefix("✓").trim()
        if (cleaned.length > 3 && !cleaned.contains("СТОРОНИ")) {
            strengths.add(cleaned)
        }
    }

    val gaps = mutableListOf<String>()
    val gapsSection = Regex(
        "ЩО ПОТРІБНО РОЗВИНУТИ:(.*?)(?=ОЦІНКА ЗАРПЛАТИ|ЧАС ДО МЕТИ|$)",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).find(gapAnalysis)?.groupValues?.getOrNull(1)

    gapsSection?.split("\n")?.forEach { line ->
        val cleaned = line.trim()
            .removePrefix("-").removePrefix("•").removePrefix("*")
            .removePrefix("→").trim()
        if (cleaned.length > 3 && !cleaned.contains("РОЗВИНУТИ")) {
            gaps.add(cleaned)
        }
    }

    val salary = Regex("ОЦІНКА ЗАРПЛАТИ[:\\s]+([^\n]+)", RegexOption.IGNORE_CASE)
        .find(gapAnalysis)?.groupValues?.getOrNull(1)?.trim() ?: "Не визначено"

    val timeToGoal = Regex("ЧАС ДО МЕТИ[:\\s]+([^\n]+)", RegexOption.IGNORE_CASE)
        .find(gapAnalysis)?.groupValues?.getOrNull(1)?.trim() ?: "Не визначено"

    val steps = mutableListOf<ParsedActionStep>()
    val stepRegex = Regex(
        "КРОК (\\d+):(.*?)(?=КРОК \\d+:|ЗАГАЛЬНИЙ|$)",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    stepRegex.findAll(actionPlan).forEach { match ->
        val stepNumber = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val stepContent = match.groupValues.getOrNull(2)?.trim() ?: ""

        val lines = stepContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.firstOrNull {
            !it.startsWith("⏰") &&
                    !it.startsWith("🔥") &&
                    !it.startsWith("Час:") &&
                    !it.startsWith("Пріоритет:") &&
                    it.length > 5
        }?.take(100) ?: "Крок $stepNumber"

        val time = Regex("⏰\\s*Час[:\\s]+([^\n]+)", RegexOption.IGNORE_CASE)
            .find(stepContent)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("Час[:\\s]+([^\n]+)", RegexOption.IGNORE_CASE)
                .find(stepContent)?.groupValues?.getOrNull(1)?.trim()
            ?: "Не визначено"

        val priority = when {
            stepContent.contains("Критично", ignoreCase = true) -> "Критично"
            stepContent.contains("Високий", ignoreCase = true) -> "Високий"
            else -> "Середній"
        }

        val descriptionLines = lines.filter { line ->
            !line.startsWith("⏰") &&
                    !line.startsWith("🔥") &&
                    !line.startsWith("💡") &&
                    !line.contains("Ресурси:") &&
                    line != title
        }.take(3)

        val description = descriptionLines.joinToString(" ").take(200)

        if (stepNumber in 1..10 && title.isNotEmpty()) {
            steps.add(ParsedActionStep(
                number = stepNumber,
                title = title,
                description = description.ifEmpty { "Детальний опис кроку $stepNumber" },
                timeEstimate = time,
                priority = priority
            ))
        }
    }

    steps.sortBy { it.number }

    if (steps.isEmpty()) {
        for (i in 1..10) {
            steps.add(ParsedActionStep(
                number = i,
                title = "Крок $i - Розвиток навичок",
                description = "Працюйте над розвитком необхідних компетенцій",
                timeEstimate = "1-2 тижні",
                priority = if (i <= 3) "Високий" else "Середній"
            ))
        }
    }

    return ParsedAssessmentResult(
        matchScore = matchScore,
        strengths = strengths.ifEmpty { listOf("Мотивація до навчання", "Готовність до змін") },
        gaps = gaps.ifEmpty { listOf("Потрібен розвиток технічних навичок") },
        expectedSalary = salary,
        timeToGoal = timeToGoal,
        actionSteps = steps.take(10)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentResultsScreen(
    result: ParsedAssessmentResult,
    onBackToChat: () -> Unit,
    onRetakeAssessment: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результати Assessment") },
                navigationIcon = {
                    IconButton(onClick = onBackToChat) {
                        Icon(Icons.Default.ArrowBack, "Назад")
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
                .padding(16.dp)
        ) {
            MatchScoreCard(score = result.matchScore)

            Spacer(modifier = Modifier.height(16.dp))

            StrengthsCard(strengths = result.strengths)

            Spacer(modifier = Modifier.height(12.dp))

            GapsCard(gaps = result.gaps)

            Spacer(modifier = Modifier.height(16.dp))

            InfoCards(
                salary = result.expectedSalary,
                timeToGoal = result.timeToGoal
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "🎯 Ваш план дій:",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            result.actionSteps.forEach { step ->
                ActionStepCard(
                    number = step.number,
                    title = step.title,
                    description = step.description,
                    timeEstimate = step.timeEstimate,
                    priority = step.priority
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetakeAssessment,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Пройти Assessment знову")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onBackToChat,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Повернутись до чату")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
