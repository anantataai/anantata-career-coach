package ai.anantata.careercoach

import ai.anantata.careercoach.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import org.json.JSONArray

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES для v1.6 (з підтримкою зв'язку кроків і завдань)
// ═══════════════════════════════════════════════════════════════

data class GeneratedGoal(
    val title: String,
    val targetSalary: String
)

data class GeneratedStrategicStep(
    val number: Int,
    val title: String,
    val description: String,
    val timeframe: String,
    val startWeek: Int,  // NEW: з якого тижня починається
    val endWeek: Int     // NEW: на якому тижні закінчується
)

data class GeneratedWeeklyTask(
    val number: Int,
    val title: String,
    val description: String,
    val strategicStepNumber: Int  // NEW: до якого кроку відноситься (1-10)
)

data class GeneratedPlan(
    val goal: GeneratedGoal,
    val matchScore: Int,
    val gapAnalysis: String,
    val strategicSteps: List<GeneratedStrategicStep>,
    val weeklyTasks: List<GeneratedWeeklyTask>
)

// ═══════════════════════════════════════════════════════════════

class GeminiRepository {

    // Модель для чату - швидка
    private val chatModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
        }
    )

    // Модель для Assessment - більш детермінована для точних розрахунків
    private val assessmentModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.3f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 8192
        }
    )

    // ═══════════════════════════════════════════════════════════════
    // ЧАТОВА ФУНКЦІЯ з КОНТЕКСТОМ (для #35)
    // ═══════════════════════════════════════════════════════════════

    fun sendMessageWithContext(
        message: String,
        context: String? = null
    ): Flow<String> = flow {
        val fullPrompt = if (context != null) {
            """
$context

═══════════════════════════════════════════════════════════════
ПОВІДОМЛЕННЯ КОРИСТУВАЧА:
$message
═══════════════════════════════════════════════════════════════

Відповідай як професійний кар'єрний коуч, враховуючи контекст вище.
Якщо користувач питає про "крок 1", "завдання 2" тощо — це з його плану вище.
Будь конкретним і практичним.
""".trimIndent()
        } else {
            message
        }

        val response = chatModel.generateContentStream(fullPrompt)
        response.collect { chunk ->
            emit(chunk.text ?: "")
        }
    }

    // Стара функція для сумісності
    fun sendMessage(message: String): Flow<String> = sendMessageWithContext(message, null)

    // ═══════════════════════════════════════════════════════════════
    // ГЕНЕРАЦІЯ КОНТЕКСТУ ДЛЯ ШІ (для #35)
    // ═══════════════════════════════════════════════════════════════

    fun buildAIContext(
        goalTitle: String,
        targetSalary: String,
        strategicSteps: List<StrategicStepItem>,
        weeklyTasks: List<WeeklyTaskItem>,
        currentWeek: Int,
        chatHistory: List<ChatMessageItem> = emptyList()
    ): String {
        val stepsText = strategicSteps.joinToString("\n") { step ->
            val statusIcon = when (step.status) {
                "done" -> "✅"
                "in_progress" -> "🔄"
                else -> "⏳"
            }
            val weekRange = if (step.startWeek > 0 && step.endWeek > 0) {
                " [Тижні ${step.startWeek}-${step.endWeek}]"
            } else ""
            val progress = if (step.progressPercent > 0) " (${step.progressPercent}%)" else ""
            "$statusIcon Крок ${step.stepNumber}: ${step.title}$weekRange$progress"
        }

        val tasksText = weeklyTasks.joinToString("\n") { task ->
            val statusIcon = when (task.status) {
                "done" -> "✅"
                "skipped" -> "⏭️"
                else -> "🔲"
            }
            "$statusIcon ${task.taskNumber}. ${task.title}"
        }

        val doneCount = weeklyTasks.count { it.status == "done" }
        val skippedCount = weeklyTasks.count { it.status == "skipped" }

        val historyText = if (chatHistory.isNotEmpty()) {
            val lastMessages = chatHistory.takeLast(10)
            lastMessages.joinToString("\n") { msg ->
                val role = if (msg.role == "user") "Користувач" else "Коуч"
                "$role: ${msg.content.take(200)}${if (msg.content.length > 200) "..." else ""}"
            }
        } else {
            "Немає попередніх повідомлень"
        }

        return """
═══════════════════════════════════════════════════════════════
КОНТЕКСТ КОРИСТУВАЧА (ТИ - КАРЕЄРНИЙ КОУЧ)
═══════════════════════════════════════════════════════════════

🎯 ГОЛОВНА ЦІЛЬ: $goalTitle
💰 Бажаний дохід: $targetSalary

📋 СТРАТЕГІЧНИЙ ПЛАН (10 кроків на 3-12 місяців):
$stepsText

📅 ТИЖДЕНЬ $currentWeek — ПОТОЧНІ ЗАВДАННЯ:
$tasksText

📊 Прогрес тижня: $doneCount/10 виконано, $skippedCount пропущено

💬 ІСТОРІЯ ЧАТУ:
$historyText

═══════════════════════════════════════════════════════════════
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // ГОЛОВНА ФУНКЦІЯ v1.6: ГЕНЕРАЦІЯ ЦІЛІ + ПЛАНУ + ЗАВДАНЬ
    // з підтримкою зв'язку кроків і завдань
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateGoalWithPlan(
        answers: Map<Int, String>,
        questions: List<AssessmentQuestion>
    ): GeneratedPlan {
        val answersText = buildString {
            questions.forEach { question ->
                val answer = answers[question.id] ?: "Немає відповіді"
                appendLine("${question.text}")
                appendLine("Відповідь: $answer")
                appendLine()
            }
        }

        // Витягуємо ключові відповіді
        val currentSalary = answers[5] ?: ""
        val desiredSalary = answers[9] ?: ""
        val currentPosition = answers[3] ?: ""
        val desiredPosition = answers[8] ?: ""
        val experience = answers[4] ?: ""
        val barrier = answers[11] ?: ""
        val education = answers[2] ?: ""
        val skills = answers[6] ?: ""
        val achievements = answers[7] ?: ""
        val certificates = answers[13] ?: ""
        val motivation = answers[15] ?: ""
        val workPreference = answers[12] ?: ""

        val prompt = """
Ти - професійний career counselor з 20+ роками досвіду. 
Проаналізуй відповіді та створи ПОВНИЙ ПЛАН для користувача.

ВІДПОВІДІ КАНДИДАТА:
$answersText

═══════════════════════════════════════════════════════════════
ЗАВДАННЯ: Згенеруй JSON з такою структурою
═══════════════════════════════════════════════════════════════

{
  "goal": {
    "title": "[Коротка назва цілі на основі відповіді 8, наприклад: 'Відкрити власний бізнес' або 'Стати IT спеціалістом']",
    "target_salary": "[Бажана зарплата з відповіді 9]"
  },
  "match_score": [число від 0 до 100 — розрахуй чесно],
  "gap_analysis": "[Короткий текст 3-5 речень: поточний стан, що треба розвинути, скільки часу до мети]",
  "strategic_steps": [
    {
      "number": 1,
      "title": "[Назва кроку - до 5 слів]",
      "description": "[Опис 1-2 речення]",
      "timeframe": "Місяць 1-2",
      "start_week": 1,
      "end_week": 8
    },
    {
      "number": 2,
      "title": "[Назва]",
      "description": "[Опис]",
      "timeframe": "Місяць 1-2",
      "start_week": 1,
      "end_week": 8
    },
    ... всього РІВНО 10 кроків
  ],
  "weekly_tasks": [
    {
      "number": 1,
      "title": "[Конкретне завдання на 1-2 години]",
      "description": "[Що саме зробити]",
      "strategic_step_number": 1
    },
    {
      "number": 2,
      "title": "[Завдання]",
      "description": "[Опис]",
      "strategic_step_number": 1
    },
    ... всього РІВНО 10 завдань
  ]
}

═══════════════════════════════════════════════════════════════
ПРАВИЛА ГЕНЕРАЦІЇ:
═══════════════════════════════════════════════════════════════

MATCH SCORE — розрахуй за формулою:
1. Позиційний gap (0-20): "$currentPosition" → "$desiredPosition"
2. Досвід (0-20): "$experience"
3. Освіта (0-20): "$education" + "$certificates"
4. Навички (0-20): "$skills" + "$achievements"
5. Фінансовий gap (0-20): "$currentSalary" → "$desiredSalary"

СТРАТЕГІЧНІ КРОКИ (10 шт.):
- Напрямок на 3-12 місяців (приблизно 52 тижні)
- Від простого до складного
- Перші 2-3 кроки — подолання бар'єру "$barrier"
- Враховуй мотивацію: "$motivation"

ВАЖЛИВО ДЛЯ КРОКІВ — start_week та end_week:
- Кроки можуть виконуватись ПАРАЛЕЛЬНО (наприклад, крок 1 і крок 2 обидва тижні 1-8)
- Типовий розподіл:
  * Кроки 1-3: start_week=1, end_week=8 (Місяць 1-2)
  * Кроки 4-5: start_week=9, end_week=16 (Місяць 3-4)
  * Кроки 6-7: start_week=17, end_week=26 (Місяць 5-6)
  * Кроки 8-9: start_week=27, end_week=40 (Місяць 7-10)
  * Крок 10: start_week=41, end_week=52 (Місяць 11-12)
- Деякі кроки можуть тривати весь час (наприклад, "Розвиток впевненості" 1-52)

ТИЖНЕВІ ЗАВДАННЯ (10 шт.):
- КОНКРЕТНІ дії на ПЕРШИЙ ТИЖДЕНЬ
- Кожне завдання можна виконати за 1-3 години
- Реалістичні для України
- Включай конкретні ресурси (назви курсів, сайтів)

ВАЖЛИВО ДЛЯ ЗАВДАНЬ — strategic_step_number:
- Вказуй номер кроку (1-10), до якого відноситься завдання
- На першому тижні завдання мають бути для кроків з start_week=1
- Розподіл: 2-3 завдання на крок 1, 2-3 на крок 2, решта на крок 3
- Приклад: якщо крок 1 = "Самоаналіз", то завдання 1-3 мають strategic_step_number: 1

ВАЖЛИВО:
- Відповідай ТІЛЬКИ валідним JSON
- БЕЗ markdown, БЕЗ пояснень, БЕЗ тексту до/після JSON
- РІВНО 10 strategic_steps
- РІВНО 10 weekly_tasks
""".trimIndent()

        return try {
            val response = assessmentModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: throw Exception("Порожня відповідь")

            // Очищаємо від можливих markdown блоків
            val cleanJson = jsonText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            parseGeneratedPlan(cleanJson)
        } catch (e: Exception) {
            // Fallback — повертаємо базовий план
            createFallbackPlan(answers)
        }
    }

    private fun parseGeneratedPlan(jsonText: String): GeneratedPlan {
        val json = JSONObject(jsonText)

        // Парсимо goal
        val goalJson = json.getJSONObject("goal")
        val goal = GeneratedGoal(
            title = goalJson.getString("title"),
            targetSalary = goalJson.getString("target_salary")
        )

        // Парсимо match_score
        val matchScore = json.getInt("match_score")

        // Парсимо gap_analysis
        val gapAnalysis = json.getString("gap_analysis")

        // Парсимо strategic_steps
        val stepsArray = json.getJSONArray("strategic_steps")
        val strategicSteps = mutableListOf<GeneratedStrategicStep>()
        for (i in 0 until stepsArray.length()) {
            val stepJson = stepsArray.getJSONObject(i)
            strategicSteps.add(GeneratedStrategicStep(
                number = stepJson.getInt("number"),
                title = stepJson.getString("title"),
                description = stepJson.getString("description"),
                timeframe = stepJson.getString("timeframe"),
                startWeek = stepJson.optInt("start_week", 1),  // NEW
                endWeek = stepJson.optInt("end_week", 8)       // NEW
            ))
        }

        // Парсимо weekly_tasks
        val tasksArray = json.getJSONArray("weekly_tasks")
        val weeklyTasks = mutableListOf<GeneratedWeeklyTask>()
        for (i in 0 until tasksArray.length()) {
            val taskJson = tasksArray.getJSONObject(i)
            weeklyTasks.add(GeneratedWeeklyTask(
                number = taskJson.getInt("number"),
                title = taskJson.getString("title"),
                description = taskJson.getString("description"),
                strategicStepNumber = taskJson.optInt("strategic_step_number", 1)  // NEW
            ))
        }

        return GeneratedPlan(
            goal = goal,
            matchScore = matchScore,
            gapAnalysis = gapAnalysis,
            strategicSteps = strategicSteps,
            weeklyTasks = weeklyTasks
        )
    }

    private fun createFallbackPlan(answers: Map<Int, String>): GeneratedPlan {
        val desiredPosition = answers[8] ?: "Досягти кар'єрної мети"
        val desiredSalary = answers[9] ?: "Збільшити дохід"

        return GeneratedPlan(
            goal = GeneratedGoal(
                title = desiredPosition,
                targetSalary = desiredSalary
            ),
            matchScore = 50,
            gapAnalysis = "Не вдалось проаналізувати профіль автоматично. Рекомендуємо пройти оцінку ще раз.",
            strategicSteps = (1..10).map { i ->
                val (startW, endW) = when (i) {
                    1, 2, 3 -> Pair(1, 8)
                    4, 5 -> Pair(9, 16)
                    6, 7 -> Pair(17, 26)
                    8, 9 -> Pair(27, 40)
                    else -> Pair(41, 52)
                }
                GeneratedStrategicStep(
                    number = i,
                    title = "Крок $i",
                    description = "Опис кроку $i",
                    timeframe = "Місяць ${(i + 1) / 2}-${(i + 2) / 2}",
                    startWeek = startW,
                    endWeek = endW
                )
            },
            weeklyTasks = (1..10).map { i ->
                GeneratedWeeklyTask(
                    number = i,
                    title = "Завдання $i",
                    description = "Опис завдання $i",
                    strategicStepNumber = when {
                        i <= 3 -> 1
                        i <= 6 -> 2
                        else -> 3
                    }
                )
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЕНЕРАЦІЯ НАСТУПНОГО ТИЖНЯ (з прив'язкою до кроків)
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateNextWeekTasks(
        goalTitle: String,
        targetSalary: String,
        strategicSteps: List<StrategicStepItem>,
        completedTasks: List<WeeklyTaskItem>,
        skippedTasks: List<WeeklyTaskItem>,
        currentWeek: Int
    ): List<GeneratedWeeklyTask> {

        // Знаходимо активні кроки на цьому тижні
        val activeSteps = strategicSteps.filter { step ->
            currentWeek >= step.startWeek && currentWeek <= step.endWeek
        }

        val activeStepsText = if (activeSteps.isNotEmpty()) {
            activeSteps.joinToString("\n") { step ->
                "🔄 Крок ${step.stepNumber}: ${step.title} (тижні ${step.startWeek}-${step.endWeek})"
            }
        } else {
            strategicSteps.take(3).joinToString("\n") { step ->
                "📌 Крок ${step.stepNumber}: ${step.title}"
            }
        }

        val stepsText = strategicSteps.joinToString("\n") { step ->
            val statusIcon = when (step.status) {
                "done" -> "✅"
                "in_progress" -> "🔄"
                else -> "⏳"
            }
            val isActive = currentWeek >= step.startWeek && currentWeek <= step.endWeek
            val activeMarker = if (isActive) " ⬅️ АКТИВНИЙ" else ""
            "$statusIcon Крок ${step.stepNumber}: ${step.title} [Тижні ${step.startWeek}-${step.endWeek}]$activeMarker"
        }

        val completedText = completedTasks.joinToString("\n") { "✅ ${it.title}" }
        val skippedText = skippedTasks.joinToString("\n") { "⏭️ ${it.title}" }

        val prompt = """
Ти - професійний career counselor.

КОНТЕКСТ КОРИСТУВАЧА:
🎯 Ціль: $goalTitle
💰 Бажаний дохід: $targetSalary

СТРАТЕГІЧНІ КРОКИ (з діапазонами тижнів):
$stepsText

АКТИВНІ КРОКИ НА ТИЖНІ $currentWeek:
$activeStepsText

ТИЖДЕНЬ ${currentWeek - 1} — РЕЗУЛЬТАТИ:
Виконано (${completedTasks.size}/10):
$completedText

Пропущено (${skippedTasks.size}/10):
$skippedText

═══════════════════════════════════════════════════════════════
ЗАВДАННЯ: Згенеруй 10 завдань на ТИЖДЕНЬ $currentWeek
═══════════════════════════════════════════════════════════════

Формат — ТІЛЬКИ валідний JSON масив:
[
  {
    "number": 1,
    "title": "[Конкретне завдання]",
    "description": "[Що саме зробити]",
    "strategic_step_number": [номер кроку 1-10]
  },
  ... всього РІВНО 10 завдань
]

ПРАВИЛА:
- Завдання мають бути для АКТИВНИХ кроків (де тиждень $currentWeek в діапазоні start_week-end_week)
- Якщо активних кроків 2-3, розподіли завдання між ними
- Враховуй що користувач пропустив деякі завдання — можливо повторити важливі
- Завдання мають бути СКЛАДНІШИМИ ніж минулого тижня
- Кожне завдання на 1-3 години
- Конкретні ресурси та дії

ВІДПОВІДАЙ ТІЛЬКИ JSON МАСИВОМ!
""".trimIndent()

        return try {
            val response = assessmentModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: throw Exception("Порожня відповідь")

            val cleanJson = jsonText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            parseWeeklyTasks(cleanJson)
        } catch (e: Exception) {
            // Fallback — генеруємо базові завдання для активних кроків
            val activeStepNumbers = activeSteps.map { it.stepNumber }.ifEmpty { listOf(1, 2, 3) }
            (1..10).map { i ->
                val stepNum = activeStepNumbers[(i - 1) % activeStepNumbers.size]
                GeneratedWeeklyTask(
                    number = i,
                    title = "Завдання $i тижня $currentWeek",
                    description = "Продовжуйте працювати над кроком $stepNum",
                    strategicStepNumber = stepNum
                )
            }
        }
    }

    private fun parseWeeklyTasks(jsonText: String): List<GeneratedWeeklyTask> {
        val tasksArray = JSONArray(jsonText)
        val tasks = mutableListOf<GeneratedWeeklyTask>()

        for (i in 0 until tasksArray.length()) {
            val taskJson = tasksArray.getJSONObject(i)
            tasks.add(GeneratedWeeklyTask(
                number = taskJson.getInt("number"),
                title = taskJson.getString("title"),
                description = taskJson.getString("description"),
                strategicStepNumber = taskJson.optInt("strategic_step_number", 1)  // NEW
            ))
        }

        return tasks
    }

    // ═══════════════════════════════════════════════════════════════
    // СТАРІ ФУНКЦІЇ (для сумісності з поточним кодом)
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateAssessmentQuestions(
        type: String
    ): List<AssessmentQuestion> {
        return listOf(
            AssessmentQuestion(
                1,
                "Скільки вам років?",
                "current_state",
                "select",
                listOf("До 25", "26-35", "36-45", "Більше 45")
            ),
            AssessmentQuestion(
                2,
                "Яка у вас освіта?",
                "current_state",
                "select",
                listOf("Вища (Бакалавр/Магістр)", "Неповна вища (студент)", "Середня спеціальна", "Середня/без освіти")
            ),
            AssessmentQuestion(
                3,
                "Яка ваша поточна посада?",
                "current_state",
                "select",
                listOf("Не працюю/студент/стажер", "Виконавець/спеціаліст", "Керівник/менеджер", "Власний бізнес")
            ),
            AssessmentQuestion(
                4,
                "Скільки років досвіду роботи?",
                "current_state",
                "select",
                listOf("Без досвіду/до 1 року", "1-5 років", "5-10 років", "Більше 10 років")
            ),
            AssessmentQuestion(
                5,
                "Яка ваша поточна зарплата? (грн/міс)",
                "current_state",
                "select",
                listOf("Не працюю/до 20,000", "20,000-50,000", "50,000-100,000", "Більше 100,000")
            ),
            AssessmentQuestion(
                6,
                "Ваші ключові навички? (оберіть або напишіть свої)",
                "current_state",
                "select_or_custom",
                listOf("Комунікація та робота з людьми", "Аналітика та технічні навички", "Лідерство та управління", "Креативність та творчість", "💡 Ваш варіант")
            ),
            AssessmentQuestion(
                7,
                "Ваші головні досягнення?",
                "current_state",
                "select_or_custom",
                listOf("Ще немає значних досягнень", "Успішно виконав складні проекти", "Отримав підвищення/визнання", "Побудував команду/покращив процеси", "💡 Ваш варіант")
            ),
            AssessmentQuestion(
                8,
                "На яку посаду ви хочете перейти?",
                "desired_state",
                "select",
                listOf("Стати спеціалістом/фахівцем", "Стати керівником/менеджером", "Відкрити власний бізнес", "Змінити сферу діяльності")
            ),
            AssessmentQuestion(
                9,
                "Яку зарплату ви хочете отримувати? (грн/міс)",
                "desired_state",
                "select",
                listOf("25,000-50,000", "50,000-100,000", "100,000-150,000", "Більше 150,000")
            ),
            AssessmentQuestion(
                10,
                "В якому типі компанії хочете працювати?",
                "desired_state",
                "select",
                listOf("Велика міжнародна корпорація", "Середній/малий бізнес/стартап", "Державна організація", "Власний бізнес/фріланс")
            ),
            AssessmentQuestion(
                11,
                "Що найбільше заважає вам досягти кар'єрної мети?",
                "barriers",
                "select_or_custom",
                listOf("Брак знань/навичок", "Брак досвіду", "Брак часу", "Брак впевненості", "💡 Ваш варіант")
            ),
            AssessmentQuestion(
                12,
                "Що для вас найважливіше в роботі?",
                "desired_state",
                "select",
                listOf("Висока зарплата", "Розвиток та навчання", "Work-life balance", "Цікаві задачі та команда")
            ),
            AssessmentQuestion(
                13,
                "Чи є у вас сертифікати/курси?",
                "additional",
                "select",
                listOf("Немає", "1-3 курси пройдено", "Більше 3 курсів", "Міжнародні сертифікати")
            ),
            AssessmentQuestion(
                14,
                "Які у вас хобі/інтереси?",
                "additional",
                "select_or_custom",
                listOf("Спорт та активний відпочинок", "Читання та самоосвіта", "Творчість та мистецтво", "Технології та бізнес", "💡 Ваш варіант")
            ),
            AssessmentQuestion(
                15,
                "Що вас найбільше мотивує в кар'єрі?",
                "additional",
                "select",
                listOf("Фінансова незалежність", "Професійне визнання", "Допомога людям/суспільству", "Свобода та гнучкість")
            )
        )
    }

    // Стара функція — залишаємо для сумісності
    suspend fun analyzeCareerGap(
        answers: Map<Int, String>,
        questions: List<AssessmentQuestion>
    ): String {
        // Використовуємо нову функцію і повертаємо тільки gap analysis
        val plan = generateGoalWithPlan(answers, questions)
        return """
📊 CAREER GAP ANALYSIS

🎯 Match Score: ${plan.matchScore}%

${plan.gapAnalysis}

💪 СИЛЬНІ СТОРОНИ:
- Ваша мотивація та цілеспрямованість
- Готовність до змін
- Чітке розуміння мети

📈 ЩО ПОТРІБНО РОЗВИНУТИ:
- Дивіться ваш персональний план дій
""".trimIndent()
    }

    // Стара функція — залишаємо для сумісності
    suspend fun generateActionPlan(
        answers: Map<Int, String>,
        questions: List<AssessmentQuestion>,
        gapAnalysis: String
    ): String {
        val plan = generateGoalWithPlan(answers, questions)

        val stepsText = plan.strategicSteps.joinToString("\n\n") { step ->
            """
📍 КРОК ${step.number}: ${step.title}
⏰ Час: ${step.timeframe} (тижні ${step.startWeek}-${step.endWeek})

${step.description}
""".trimIndent()
        }

        return """
🎯 ACTION PLAN

$stepsText

━━━━━━━━━━━━━━━━━━━━━━

🎯 ЗАГАЛЬНИЙ ЧАС ДО МЕТИ: 6-12 місяців
""".trimIndent()
    }
}

// ═══════════════════════════════════════════════════════════════
// ДОПОМІЖНІ DATA CLASSES для роботи з Supabase даними
// ═══════════════════════════════════════════════════════════════

data class StrategicStepItem(
    val id: String,
    val goalId: String,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val timeframe: String,
    val status: String,           // "pending", "in_progress", "done"
    val startWeek: Int = 1,       // NEW: з якого тижня
    val endWeek: Int = 8,         // NEW: до якого тижня
    val progressPercent: Int = 0  // NEW: відсоток прогресу (розраховується)
)

data class WeeklyTaskItem(
    val id: String,
    val goalId: String,
    val weekNumber: Int,
    val taskNumber: Int,
    val title: String,
    val description: String,
    val status: String,                    // "pending", "done", "skipped"
    val strategicStepId: String? = null    // NEW: ID кроку з Supabase
)

data class ChatMessageItem(
    val id: String,
    val userId: String,
    val goalId: String,
    val role: String,
    val content: String,
    val createdAt: String
)

data class AssessmentQuestion(
    val id: Int,
    val text: String,
    val category: String,
    val inputType: String,
    val options: List<String>?
)