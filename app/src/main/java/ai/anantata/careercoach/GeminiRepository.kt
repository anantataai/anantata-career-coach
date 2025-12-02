package ai.anantata.careercoach

import ai.anantata.careercoach.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import org.json.JSONArray

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES v2.0 — БЛОКИ + НАПРЯМКИ + КРОКИ
// ═══════════════════════════════════════════════════════════════

data class GeneratedGoal(
    val title: String,
    val targetSalary: String
)

/**
 * Напрямок (раніше "Крок") — один з 10 напрямків розвитку
 * Наприклад: "Самоаналіз", "Розвиток впевненості", "Основи підприємництва"
 */
data class GeneratedDirection(
    val number: Int,          // 1-10
    val title: String,        // Коротка назва напрямку
    val description: String   // Опис напрямку
)

/**
 * Крок (раніше "Завдання") — конкретна дія для виконання
 * 10 кроків на кожен напрямок = 100 кроків в блоці
 */
data class GeneratedStep(
    val number: Int,           // 1-100 (глобальний номер в блоці)
    val localNumber: Int,      // 1-10 (номер в межах напрямку)
    val title: String,         // Коротка назва кроку
    val description: String,   // Короткий опис (генерується одразу)
    val directionNumber: Int   // До якого напрямку відноситься (1-10)
)

/**
 * Повний план = 10 напрямків × 10 кроків = 100 кроків
 */
data class GeneratedPlan(
    val goal: GeneratedGoal,
    val matchScore: Int,
    val gapAnalysis: String,
    val directions: List<GeneratedDirection>,  // 10 напрямків
    val steps: List<GeneratedStep>             // 100 кроків
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

    // Модель для Assessment - більш детермінована
    private val assessmentModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.3f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 16384  // Збільшено для 100 кроків
        }
    )

    // ═══════════════════════════════════════════════════════════════
    // ЧАТОВА ФУНКЦІЯ з КОНТЕКСТОМ
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
Якщо користувач питає про "напрямок 1", "крок 5" тощо — це з його плану вище.
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

    fun sendMessage(message: String): Flow<String> = sendMessageWithContext(message, null)

    // ═══════════════════════════════════════════════════════════════
    // ГЕНЕРАЦІЯ КОНТЕКСТУ ДЛЯ ШІ
    // ═══════════════════════════════════════════════════════════════

    fun buildAIContext(
        goalTitle: String,
        targetSalary: String,
        directions: List<DirectionItem>,
        steps: List<StepItem>,
        currentBlock: Int,
        chatHistory: List<ChatMessageItem> = emptyList()
    ): String {
        val directionsText = directions.joinToString("\n") { dir ->
            val dirSteps = steps.filter { it.directionId == dir.id }
            val doneCount = dirSteps.count { it.status == "done" }
            val statusIcon = when {
                doneCount == 10 -> "✅"
                doneCount > 0 -> "🔄"
                else -> "⏳"
            }
            "$statusIcon Напрямок ${dir.directionNumber}: ${dir.title} ($doneCount/10)"
        }

        val activeSteps = steps.filter { it.status == "pending" }.take(10)
        val stepsText = activeSteps.joinToString("\n") { step ->
            "🔲 Крок ${step.stepNumber}: ${step.title}"
        }

        val doneCount = steps.count { it.status == "done" }
        val skippedCount = steps.count { it.status == "skipped" }

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

📦 БЛОК $currentBlock — 10 НАПРЯМКІВ, 100 КРОКІВ:
$directionsText

🔲 НАСТУПНІ КРОКИ ДО ВИКОНАННЯ:
$stepsText

📊 Прогрес блоку: $doneCount/100 виконано, $skippedCount пропущено

💬 ІСТОРІЯ ЧАТУ:
$historyText

═══════════════════════════════════════════════════════════════
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // v2.0: ГЕНЕРАЦІЯ ЦІЛІ + 10 НАПРЯМКІВ + 100 КРОКІВ
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

        val prompt = """
Ти - професійний career counselor з 20+ роками досвіду. 
Проаналізуй відповіді та створи ПОВНИЙ ПЛАН: 10 напрямків × 10 кроків = 100 кроків.

ВІДПОВІДІ КАНДИДАТА:
$answersText

═══════════════════════════════════════════════════════════════
ЗАВДАННЯ: Згенеруй JSON з такою структурою
═══════════════════════════════════════════════════════════════

{
  "goal": {
    "title": "[Коротка назва цілі, наприклад: 'Відкрити власний бізнес']",
    "target_salary": "[Бажана зарплата з відповіді 9]"
  },
  "match_score": [число від 0 до 100],
  "gap_analysis": "[Короткий текст 3-5 речень]",
  "directions": [
    {
      "number": 1,
      "title": "Самоаналіз та визначення ніші",
      "description": "Глибоке розуміння своїх сильних сторін та вибір напрямку"
    },
    {
      "number": 2,
      "title": "Розвиток впевненості",
      "description": "Подолання страхів та розвиток лідерських якостей"
    },
    ... всього РІВНО 10 напрямків
  ],
  "steps": [
    {
      "number": 1,
      "local_number": 1,
      "title": "Записати 5 своїх досягнень",
      "description": "Згадайте та запишіть 5 найбільших професійних чи особистих досягнень",
      "direction_number": 1
    },
    {
      "number": 2,
      "local_number": 2,
      "title": "Визначити 3 ключові навички",
      "description": "Проаналізуйте свої досягнення та виділіть навички, які допомогли їх досягти",
      "direction_number": 1
    },
    ... всього РІВНО 100 кроків (по 10 на кожен напрямок)
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

10 НАПРЯМКІВ:
- Логічна послідовність від простого до складного
- Перші 2-3 напрямки — подолання бар'єру "$barrier"
- Враховуй мотивацію: "$motivation"
- Останні напрямки — досягнення цілі "$desiredPosition"

100 КРОКІВ (по 10 на кожен напрямок):
- Кожен крок — КОНКРЕТНА дія на 30 хв - 2 години
- Від простого до складного в межах напрямку
- Реалістичні для України
- Включай конкретні ресурси (назви курсів, сайтів, інструментів)
- description — короткий опис 1-2 речення

НУМЕРАЦІЯ:
- number: 1-100 (глобальний номер)
- local_number: 1-10 (номер в межах напрямку)
- direction_number: 1-10 (до якого напрямку відноситься)

Приклад для напрямку 2:
- Крок 11: number=11, local_number=1, direction_number=2
- Крок 12: number=12, local_number=2, direction_number=2
- ...
- Крок 20: number=20, local_number=10, direction_number=2

ВАЖЛИВО:
- Відповідай ТІЛЬКИ валідним JSON
- БЕЗ markdown, БЕЗ пояснень
- РІВНО 10 directions
- РІВНО 100 steps
""".trimIndent()

        return try {
            val response = assessmentModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: throw Exception("Порожня відповідь")

            val cleanJson = jsonText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            parseGeneratedPlan(cleanJson)
        } catch (e: Exception) {
            println("❌ Error generating plan: ${e.message}")
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

        // Парсимо directions (10 напрямків)
        val directionsArray = json.getJSONArray("directions")
        val directions = mutableListOf<GeneratedDirection>()
        for (i in 0 until directionsArray.length()) {
            val dirJson = directionsArray.getJSONObject(i)
            directions.add(GeneratedDirection(
                number = dirJson.getInt("number"),
                title = dirJson.getString("title"),
                description = dirJson.getString("description")
            ))
        }

        // Парсимо steps (100 кроків)
        val stepsArray = json.getJSONArray("steps")
        val steps = mutableListOf<GeneratedStep>()
        for (i in 0 until stepsArray.length()) {
            val stepJson = stepsArray.getJSONObject(i)
            steps.add(GeneratedStep(
                number = stepJson.getInt("number"),
                localNumber = stepJson.optInt("local_number", (i % 10) + 1),
                title = stepJson.getString("title"),
                description = stepJson.optString("description", ""),
                directionNumber = stepJson.getInt("direction_number")
            ))
        }

        return GeneratedPlan(
            goal = goal,
            matchScore = matchScore,
            gapAnalysis = gapAnalysis,
            directions = directions,
            steps = steps
        )
    }

    private fun createFallbackPlan(answers: Map<Int, String>): GeneratedPlan {
        val desiredPosition = answers[8] ?: "Досягти кар'єрної мети"
        val desiredSalary = answers[9] ?: "Збільшити дохід"

        val defaultDirections = listOf(
            "Самоаналіз та визначення ніші",
            "Розвиток впевненості",
            "Основи підприємництва",
            "Фінансова грамотність",
            "Маркетинг та продажі",
            "Нетворкінг",
            "Юридичні аспекти",
            "Операційне управління",
            "Масштабування",
            "Запуск бізнесу"
        )

        return GeneratedPlan(
            goal = GeneratedGoal(
                title = desiredPosition,
                targetSalary = desiredSalary
            ),
            matchScore = 50,
            gapAnalysis = "Не вдалось проаналізувати профіль автоматично. Рекомендуємо пройти оцінку ще раз.",
            directions = defaultDirections.mapIndexed { index, title ->
                GeneratedDirection(
                    number = index + 1,
                    title = title,
                    description = "Напрямок ${index + 1} вашого розвитку"
                )
            },
            steps = (1..100).map { i ->
                val dirNum = ((i - 1) / 10) + 1
                val localNum = ((i - 1) % 10) + 1
                GeneratedStep(
                    number = i,
                    localNumber = localNum,
                    title = "Крок $i",
                    description = "Опис кроку $i для напрямку $dirNum",
                    directionNumber = dirNum
                )
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // v2.0: ГЕНЕРАЦІЯ НАСТУПНОГО БЛОКУ (після 100 кроків)
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateNextBlock(
        goalTitle: String,
        targetSalary: String,
        previousDirections: List<DirectionItem>,
        completedSteps: List<StepItem>,
        skippedSteps: List<StepItem>,
        blockNumber: Int
    ): GeneratedPlan {

        val completedByDirection = previousDirections.map { dir ->
            val dirSteps = completedSteps.filter { it.directionId == dir.id }
            "${dir.title}: ${dirSteps.size}/10 виконано"
        }.joinToString("\n")

        val skippedText = skippedSteps.take(20).joinToString("\n") {
            "⏭️ ${it.title}"
        }

        val prompt = """
Ти - професійний career counselor.

КОНТЕКСТ КОРИСТУВАЧА:
🎯 Ціль: $goalTitle
💰 Бажаний дохід: $targetSalary

📦 БЛОК ${blockNumber - 1} ЗАВЕРШЕНО!

РЕЗУЛЬТАТИ ПО НАПРЯМКАХ:
$completedByDirection

ПРОПУЩЕНІ КРОКИ (${skippedSteps.size}):
$skippedText

═══════════════════════════════════════════════════════════════
ЗАВДАННЯ: Згенеруй БЛОК $blockNumber — нові 10 напрямків × 10 кроків = 100 кроків
═══════════════════════════════════════════════════════════════

Враховуй:
- Що вийшло добре у попередньому блоці — розвивай далі
- Пропущені кроки — можливо включити важливі з них
- Нові, складніші завдання для подальшого прогресу
- Ближче до фінальної цілі

Формат JSON такий самий як для першого блоку:
{
  "goal": { "title": "$goalTitle", "target_salary": "$targetSalary" },
  "match_score": [оновлений score враховуючи прогрес],
  "gap_analysis": "[Що залишилось до мети]",
  "directions": [ ... 10 напрямків ... ],
  "steps": [ ... 100 кроків ... ]
}

ВАЖЛИВО:
- Напрямки можуть повторюватись або бути новими
- Кроки мають бути СКЛАДНІШІ ніж у попередньому блоці
- Відповідай ТІЛЬКИ валідним JSON
""".trimIndent()

        return try {
            val response = assessmentModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: throw Exception("Порожня відповідь")

            val cleanJson = jsonText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            parseGeneratedPlan(cleanJson)
        } catch (e: Exception) {
            println("❌ Error generating next block: ${e.message}")
            // Fallback — повертаємо базовий план
            createFallbackPlan(mapOf(8 to goalTitle, 9 to targetSalary))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // v2.0: ГЕНЕРАЦІЯ ДЕТАЛЬНОГО ОПИСУ КРОКУ (on-demand)
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateStepDetails(
        stepTitle: String,
        stepDescription: String,
        directionTitle: String,
        goalTitle: String
    ): String {
        val prompt = """
Ти - професійний career counselor.

КОНТЕКСТ:
🎯 Ціль користувача: $goalTitle
📂 Напрямок: $directionTitle
📌 Крок: $stepTitle
📝 Короткий опис: $stepDescription

═══════════════════════════════════════════════════════════════
ЗАВДАННЯ: Напиши ДЕТАЛЬНИЙ ОПИС цього кроку (200-400 слів)
═══════════════════════════════════════════════════════════════

Включи:
1. ЩО САМЕ РОБИТИ — покрокова інструкція
2. ЯК РОБИТИ — конкретні методи, інструменти
3. РЕСУРСИ — назви сайтів, курсів, книг (реальні, для України)
4. ОЧІКУВАНИЙ РЕЗУЛЬТАТ — що отримає користувач
5. ПОРАДИ — типові помилки, лайфхаки

Пиши українською, дружнім тоном, конкретно та практично.
Використовуй емодзі для структури.
""".trimIndent()

        return try {
            val response = chatModel.generateContent(prompt)
            response.text?.trim() ?: stepDescription
        } catch (e: Exception) {
            println("❌ Error generating step details: ${e.message}")
            stepDescription
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // СТАРІ ФУНКЦІЇ (для сумісності)
    // ═══════════════════════════════════════════════════════════════

    suspend fun generateAssessmentQuestions(
        type: String
    ): List<AssessmentQuestion> {
        return listOf(
            AssessmentQuestion(1, "Скільки вам років?", "current_state", "select",
                listOf("До 25", "26-35", "36-45", "Більше 45")),
            AssessmentQuestion(2, "Яка у вас освіта?", "current_state", "select",
                listOf("Вища (Бакалавр/Магістр)", "Неповна вища (студент)", "Середня спеціальна", "Середня/без освіти")),
            AssessmentQuestion(3, "Яка ваша поточна посада?", "current_state", "select",
                listOf("Не працюю/студент/стажер", "Виконавець/спеціаліст", "Керівник/менеджер", "Власний бізнес")),
            AssessmentQuestion(4, "Скільки років досвіду роботи?", "current_state", "select",
                listOf("Без досвіду/до 1 року", "1-5 років", "5-10 років", "Більше 10 років")),
            AssessmentQuestion(5, "Яка ваша поточна зарплата? (грн/міс)", "current_state", "select",
                listOf("Не працюю/до 20,000", "20,000-50,000", "50,000-100,000", "Більше 100,000")),
            AssessmentQuestion(6, "Ваші ключові навички?", "current_state", "select_or_custom",
                listOf("Комунікація та робота з людьми", "Аналітика та технічні навички", "Лідерство та управління", "Креативність та творчість", "💡 Ваш варіант")),
            AssessmentQuestion(7, "Ваші головні досягнення?", "current_state", "select_or_custom",
                listOf("Ще немає значних досягнень", "Успішно виконав складні проекти", "Отримав підвищення/визнання", "Побудував команду/покращив процеси", "💡 Ваш варіант")),
            AssessmentQuestion(8, "На яку посаду ви хочете перейти?", "desired_state", "select",
                listOf("Стати спеціалістом/фахівцем", "Стати керівником/менеджером", "Відкрити власний бізнес", "Змінити сферу діяльності")),
            AssessmentQuestion(9, "Яку зарплату ви хочете отримувати? (грн/міс)", "desired_state", "select",
                listOf("25,000-50,000", "50,000-100,000", "100,000-150,000", "Більше 150,000")),
            AssessmentQuestion(10, "В якому типі компанії хочете працювати?", "desired_state", "select",
                listOf("Велика міжнародна корпорація", "Середній/малий бізнес/стартап", "Державна організація", "Власний бізнес/фріланс")),
            AssessmentQuestion(11, "Що найбільше заважає вам досягти кар'єрної мети?", "barriers", "select_or_custom",
                listOf("Брак знань/навичок", "Брак досвіду", "Брак часу", "Брак впевненості", "💡 Ваш варіант")),
            AssessmentQuestion(12, "Що для вас найважливіше в роботі?", "desired_state", "select",
                listOf("Висока зарплата", "Розвиток та навчання", "Work-life balance", "Цікаві задачі та команда")),
            AssessmentQuestion(13, "Чи є у вас сертифікати/курси?", "additional", "select",
                listOf("Немає", "1-3 курси пройдено", "Більше 3 курсів", "Міжнародні сертифікати")),
            AssessmentQuestion(14, "Які у вас хобі/інтереси?", "additional", "select_or_custom",
                listOf("Спорт та активний відпочинок", "Читання та самоосвіта", "Творчість та мистецтво", "Технології та бізнес", "💡 Ваш варіант")),
            AssessmentQuestion(15, "Що вас найбільше мотивує в кар'єрі?", "additional", "select",
                listOf("Фінансова незалежність", "Професійне визнання", "Допомога людям/суспільству", "Свобода та гнучкість"))
        )
    }

    // Стара функція — для сумісності
    suspend fun analyzeCareerGap(
        answers: Map<Int, String>,
        questions: List<AssessmentQuestion>
    ): String {
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
- Дивіться ваш персональний план: 10 напрямків, 100 кроків!
""".trimIndent()
    }

    // Стара функція — для сумісності
    suspend fun generateActionPlan(
        answers: Map<Int, String>,
        questions: List<AssessmentQuestion>,
        gapAnalysis: String
    ): String {
        val plan = generateGoalWithPlan(answers, questions)

        val directionsText = plan.directions.joinToString("\n\n") { dir ->
            "📍 НАПРЯМОК ${dir.number}: ${dir.title}\n${dir.description}"
        }

        return """
🎯 ACTION PLAN — 10 НАПРЯМКІВ, 100 КРОКІВ!

$directionsText

━━━━━━━━━━━━━━━━━━━━━━

🚀 Працюйте у своєму темпі!
Виконайте всі 100 кроків — і переходьте до наступного блоку.
""".trimIndent()
    }

    // ═══════════════════════════════════════════════════════════════
    // DEPRECATED — для зворотної сумісності з старим кодом
    // ═══════════════════════════════════════════════════════════════

    @Deprecated("Use generateGoalWithPlan instead")
    suspend fun generateNextWeekTasks(
        goalTitle: String,
        targetSalary: String,
        strategicSteps: List<StrategicStepItem>,
        completedTasks: List<WeeklyTaskItem>,
        skippedTasks: List<WeeklyTaskItem>,
        currentWeek: Int
    ): List<GeneratedWeeklyTask> {
        // Повертаємо пустий список — ця функція більше не використовується
        return emptyList()
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES ДЛЯ SUPABASE (v2.0)
// ═══════════════════════════════════════════════════════════════

/**
 * Напрямок в БД (раніше StrategicStepItem)
 */
data class DirectionItem(
    val id: String,
    val goalId: String,
    val directionNumber: Int,     // 1-10
    val title: String,
    val description: String,
    val status: String,           // "pending", "in_progress", "done"
    val blockNumber: Int = 1      // Номер блоку
)

/**
 * Крок в БД (раніше WeeklyTaskItem)
 */
data class StepItem(
    val id: String,
    val goalId: String,
    val directionId: String,      // ID напрямку
    val blockNumber: Int,         // Номер блоку (1, 2, 3...)
    val stepNumber: Int,          // Глобальний номер 1-100
    val localNumber: Int,         // Номер в межах напрямку 1-10
    val title: String,
    val description: String,      // Короткий опис
    val detailedDescription: String? = null,  // Детальний опис (генерується on-demand)
    val status: String            // "pending", "done", "skipped"
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

// ═══════════════════════════════════════════════════════════════
// DEPRECATED DATA CLASSES — для зворотної сумісності
// ═══════════════════════════════════════════════════════════════

@Deprecated("Use DirectionItem instead")
data class StrategicStepItem(
    val id: String,
    val goalId: String,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val timeframe: String,
    val status: String,
    val startWeek: Int = 1,
    val endWeek: Int = 8,
    val progressPercent: Int = 0
)

@Deprecated("Use StepItem instead")
data class WeeklyTaskItem(
    val id: String,
    val goalId: String,
    val weekNumber: Int,
    val taskNumber: Int,
    val title: String,
    val description: String,
    val status: String,
    val strategicStepId: String? = null
)

@Deprecated("Use GeneratedStep instead")
data class GeneratedWeeklyTask(
    val number: Int,
    val title: String,
    val description: String,
    val strategicStepNumber: Int
)

@Deprecated("Use GeneratedDirection instead")
data class GeneratedStrategicStep(
    val number: Int,
    val title: String,
    val description: String,
    val timeframe: String,
    val startWeek: Int,
    val endWeek: Int
)