# 📁 СТРУКТУРА ПРОЕКТУ ANANTATA CAREER COACH

**Версія:** 1.6  
**Дата оновлення:** 01.12.2025

---

## 🗂️ ОГЛЯД ФАЙЛІВ

```
ai.anantata.careercoach/
├── MainActivity.kt          ← Головний файл, навігація
├── SupabaseRepository.kt    ← Робота з базою даних
├── GeminiRepository.kt      ← Робота з AI (Gemini)
├── GoalDashboardScreen.kt   ← Головний екран (таски)
├── GoalsListScreen.kt       ← Екран списку цілей (NEW!)
├── StrategyScreen.kt        ← Екран стратегії
├── AssessmentScreen.kt      ← Екран оцінки (15 питань)
├── AssessmentResults.kt     ← Екран результатів
├── AssessmentHistoryScreen.kt ← Історія оцінок
├── MatchScoreCard.kt        ← UI компонент score
├── OnboardingScreen.kt      ← Онбординг
└── SavedAssessmentResult.kt ← Data class
```

---

## 📄 ДЕТАЛЬНИЙ ОПИС КОЖНОГО ФАЙЛУ

### 1️⃣ `MainActivity.kt`

**Призначення:** Точка входу, головна навігація між екранами

**Містить:**

| Компонент | Тип | Опис |
|-----------|-----|------|
| `MainActivity` | Activity | Головний Activity клас |
| `MainApp()` | Composable | Головний компонент з логікою навігації |
| `FirstAssessmentFlow()` | Composable | Потік першої оцінки |
| `ChatScreen()` | Composable | Екран чату з AI |
| `WelcomeMessageCard()` | Composable | Привітальна картка |
| `MessageBubble()` | Composable | Бульбашка повідомлення |
| `ChatMessage` | data class | Локальний клас для UI чату |
| `parseAnswersFromJson()` | Function | Парсинг відповідей з JSON |
| `generatePlanContext()` | Function | Генерація контексту плану |

**Навігаційні стани:**
```kotlin
showOnboarding      → OnboardingScreen
showFirstAssessment → FirstAssessmentFlow
showDashboard       → GoalDashboardScreen
showChat            → ChatScreen
showStrategy        → StrategyScreen
showGoalsList       → GoalsListScreen
showHistory         → AssessmentHistoryScreen
viewingHistoryItem  → AssessmentResultsScreen (view mode)
```

---

### 2️⃣ `SupabaseRepository.kt`

**Призначення:** CRUD операції з базою даних Supabase

**Data Classes:**

| Клас | Опис |
|------|------|
| `AssessmentHistoryItem` | Результат оцінки з історії |
| `GoalItem` | Ціль користувача |
| `WeekStats` | Статистика тижня (done/skipped/pending) |

**Функції по категоріях:**

#### Conversations & Messages:
| Функція | Опис |
|---------|------|
| `createConversation()` | Створити розмову |
| `saveMessage()` | Зберегти повідомлення |

#### Assessment Results:
| Функція | Опис |
|---------|------|
| `saveAssessmentResult()` | Зберегти результат оцінки |
| `getAssessmentHistory()` | Отримати історію |
| `deleteAssessment()` | Видалити оцінку |
| `deleteAllUserData()` | Видалити всі дані користувача |

#### Goals:
| Функція | Опис |
|---------|------|
| `createGoal()` | Створити ціль |
| `getGoals()` | Отримати всі цілі |
| `getPrimaryGoal()` | Отримати головну ціль |
| `getGoalsCount()` | Кількість цілей (ліміт 3) |
| `setPrimaryGoal()` | Встановити головну |
| `resetAllPrimaryGoals()` | Скинути primary з усіх |
| `updateGoalStatus()` | Змінити статус |
| `deleteGoal()` | Видалити ціль |

#### Strategic Steps:
| Функція | Опис |
|---------|------|
| `saveStrategicSteps()` | Зберегти 10 кроків |
| `getStrategicSteps()` | Отримати кроки |
| `updateStrategicStepStatus()` | Змінити статус кроку |

#### Weekly Tasks:
| Функція | Опис |
|---------|------|
| `saveWeeklyTasks()` | Зберегти завдання тижня |
| `getWeeklyTasks()` | Отримати завдання |
| `getCurrentWeekNumber()` | Поточний тиждень |
| `getMaxWeekNumber()` | Максимальний тиждень |
| `updateTaskStatus()` | Змінити статус завдання |
| `isWeekComplete()` | Чи завершено тиждень |
| `getWeekStats()` | Статистика тижня |

#### Chat Messages:
| Функція | Опис |
|---------|------|
| `saveChatMessage()` | Зберегти повідомлення чату |
| `getChatHistory()` | Отримати історію чату |
| `clearChatHistory()` | Очистити історію |

#### Комплексні:
| Функція | Опис |
|---------|------|
| `saveCompletePlan()` | Зберегти ціль + кроки + завдання |

---

### 3️⃣ `GeminiRepository.kt`

**Призначення:** Робота з AI (Google Gemini)

**Data Classes:**

| Клас | Опис | Використання |
|------|------|--------------|
| `GeneratedGoal` | Згенерована ціль | Результат генерації |
| `GeneratedStrategicStep` | Згенерований стратегічний крок | Результат генерації |
| `GeneratedWeeklyTask` | Згенероване тижневе завдання | Результат генерації |
| `GeneratedPlan` | Повний план (goal + steps + tasks) | Результат генерації |
| `StrategicStepItem` | Крок з бази даних | Читання з Supabase |
| `WeeklyTaskItem` | Завдання з бази даних | Читання з Supabase |
| `ChatMessageItem` | Повідомлення з бази даних | Читання з Supabase |
| `AssessmentQuestion` | Питання оцінки | 15 питань assessment |

**Функції:**

| Функція | Опис |
|---------|------|
| `sendMessage()` | Надіслати повідомлення в чат |
| `sendMessageWithContext()` | З контекстом користувача |
| `buildAIContext()` | Побудувати контекст для AI |
| `generateGoalWithPlan()` | Згенерувати повний план |
| `generateNextWeekTasks()` | Згенерувати наступний тиждень |
| `generateAssessmentQuestions()` | Отримати 15 питань |
| `analyzeCareerGap()` | Аналіз gap (legacy) |
| `generateActionPlan()` | План дій (legacy) |

**Моделі Gemini:**
- `chatModel` — для чату (temperature: 0.7)
- `assessmentModel` — для assessment (temperature: 0.3)

---

### 4️⃣ `GoalDashboardScreen.kt`

**Призначення:** Головний екран з ціллю та тижневими завданнями

**Composables:**

| Функція | Опис |
|---------|------|
| `GoalDashboardScreen()` | Головний екран |
| `WeekHeaderWithNavigation()` | Заголовок тижня з ◀ ▶ |
| `HistoryHintCard()` | Підказка при перегляді історії |
| `NoGoalScreen()` | Екран коли немає цілі |
| `GoalCard()` | Картка головної цілі |
| `TaskItemCard()` | Картка одного завдання |
| `TaskStatusButton()` | Кнопка статусу (✅/🔲/⏭️) |
| `EmptyTasksCard()` | Пуста картка |
| `GenerateNextWeekButton()` | Кнопка генерації тижня |
| `GeneratingWeekIndicator()` | Індикатор генерації |
| `WeekCompleteDialog()` | Діалог завершення тижня |

**Функціонал:**
- ✅ Відображення головної цілі
- ✅ Список завдань поточного тижня
- ✅ Навігація по тижнях (◀ ▶)
- ✅ Відмітка виконання завдань (done/skipped)
- ✅ Генерація наступного тижня
- ✅ Прогрес-бар
- ✅ Перегляд історії тижнів (read-only)

---

### 5️⃣ `GoalsListScreen.kt` ✨ NEW

**Призначення:** Екран управління всіма цілями користувача (макс. 3)

**Composables:**

| Функція | Опис |
|---------|------|
| `GoalsListScreen()` | Головний екран списку цілей |
| `GoalListItemCard()` | Картка однієї цілі з кнопками |
| `AddNewGoalCard()` | Картка додавання нової цілі |
| `NoGoalsContent()` | Екран коли немає цілей |

**Функціонал:**
- ✅ Показує всі цілі користувача (макс 3)
- ✅ Позначка ⭐ головної цілі (золота зірка)
- ✅ Кнопка "Зробити головною"
- ✅ Кнопка "Видалити" з діалогом підтвердження
- ✅ Кнопка "Додати нову ціль" (якщо < 3)
- ✅ Показує статус цілі (активна/на паузі/завершена)
- ✅ Показує цільову зарплату

**Параметри:**
```kotlin
GoalsListScreen(
    userId: String,
    supabaseRepo: SupabaseRepository,
    onBack: () -> Unit,
    onAddNewGoal: () -> Unit,
    onGoalSelected: (String) -> Unit  // goalId
)
```

---

### 6️⃣ `StrategyScreen.kt`

**Призначення:** Екран 10 стратегічних кроків

**Composables:**

| Функція | Опис |
|---------|------|
| `StrategyScreen()` | Головний екран |
| `StrategyHeader()` | Заголовок з прогресом |
| `StrategicStepCard()` | Картка кроку |
| `StepNumberBadge()` | Бейдж з номером |
| `StepStatusButton()` | Кнопка статусу |
| `StatusChip()` | Чіп вибору статусу |
| `NoStrategyScreen()` | Екран без стратегії |

**Статуси кроків:**
- `pending` — ⏳ Очікує
- `in_progress` — 🔄 В процесі
- `done` — ✅ Виконано

---

### 7️⃣ `AssessmentScreen.kt`

**Призначення:** Екран проходження оцінки (15 питань)

**Composables:**

| Функція | Опис |
|---------|------|
| `AssessmentScreenUI()` | Головний екран оцінки |
| `BeautifulOptionCard()` | Картка варіанту відповіді |

**Допоміжні функції:**
| Функція | Опис |
|---------|------|
| `getCustomInputLabel()` | Label для кастомного поля |
| `getCustomInputPlaceholder()` | Placeholder для кастомного поля |

**Типи питань:**
- `select` — вибір з варіантів
- `select_or_custom` — вибір або свій варіант

---

### 8️⃣ `AssessmentResults.kt`

**Призначення:** Екран результатів оцінки

**Ймовірно містить:**
- `AssessmentResultsScreen()` — екран результатів
- `parseAssessmentResults()` — парсинг результатів
- `ParsedAssessmentResult` — data class

---

### 9️⃣ `AssessmentHistoryScreen.kt`

**Призначення:** Історія всіх оцінок користувача

---

### 🔟 `MatchScoreCard.kt`

**Призначення:** UI компонент відображення Match Score

---

### 1️⃣1️⃣ `OnboardingScreen.kt`

**Призначення:** Екран онбордингу для нових користувачів

---

## 🔗 СХЕМА ЗВ'ЯЗКІВ

```
MainActivity.kt (навігація)
    │
    ├── OnboardingScreen.kt (перший запуск)
    │
    ├── AssessmentScreen.kt (15 питань)
    │       ↓
    │   GeminiRepository.kt (генерація плану)
    │       ↓
    │   SupabaseRepository.kt (збереження)
    │       ↓
    ├── AssessmentResults.kt (показ результатів)
    │
    ├── GoalDashboardScreen.kt (головний екран)
    │       │
    │       ├── SupabaseRepository.kt (CRUD tasks)
    │       ├── GeminiRepository.kt (генерація тижня)
    │       │
    │       └── Навігація на:
    │           ├── ChatScreen (в MainActivity)
    │           ├── StrategyScreen.kt
    │           └── GoalsListScreen.kt ← NEW!
    │
    ├── GoalsListScreen.kt (управління цілями) ← NEW!
    │       └── SupabaseRepository.kt (CRUD goals)
    │
    ├── StrategyScreen.kt (10 кроків)
    │       └── SupabaseRepository.kt
    │
    └── AssessmentHistoryScreen.kt (історія)
            └── SupabaseRepository.kt
```

---

## 📊 DATA CLASSES — ДЕ ЩО ЗНАХОДИТЬСЯ

### В `GeminiRepository.kt`:
```kotlin
// Для генерації (output від AI)
data class GeneratedGoal(title, targetSalary)
data class GeneratedStrategicStep(number, title, description, timeframe)
data class GeneratedWeeklyTask(number, title, description)
data class GeneratedPlan(goal, matchScore, gapAnalysis, strategicSteps, weeklyTasks)

// Для читання з бази (input для AI context)
data class StrategicStepItem(id, goalId, stepNumber, title, description, timeframe, status)
data class WeeklyTaskItem(id, goalId, weekNumber, taskNumber, title, description, status)
data class ChatMessageItem(id, userId, goalId, role, content, createdAt)
data class AssessmentQuestion(id, text, category, inputType, options)
```

### В `SupabaseRepository.kt`:
```kotlin
data class AssessmentHistoryItem(id, userId, matchScore, gapAnalysis, actionPlan, answers, createdAt)
data class GoalItem(id, userId, assessmentId, title, targetSalary, isPrimary, status, createdAt, updatedAt)
data class WeekStats(total, done, skipped, pending) // + isComplete, progressPercent
```

### В `MainActivity.kt`:
```kotlin
data class ChatMessage(role, content) // Локальний для UI
```

---

## ⚠️ ВАЖЛИВІ ПРАВИЛА

### 1. НЕ ДУБЛЮВАТИ UI КОМПОНЕНТИ:

| Компонент | Вже є в |
|-----------|---------|
| `GoalCard` | `GoalDashboardScreen.kt` |
| `TaskItemCard` | `GoalDashboardScreen.kt` |
| `TaskStatusButton` | `GoalDashboardScreen.kt` |
| `WeekHeaderWithNavigation` | `GoalDashboardScreen.kt` |
| `StrategicStepCard` | `StrategyScreen.kt` |
| `BeautifulOptionCard` | `AssessmentScreen.kt` |
| `GoalListItemCard` | `GoalsListScreen.kt` |
| `AddNewGoalCard` | `GoalsListScreen.kt` |

### 2. Data Classes розміщення:
- `Generated*` — в `GeminiRepository.kt` (результат генерації AI)
- `*Item` — в `GeminiRepository.kt` (дані з бази для контексту AI)
- `*Stats`, `*HistoryItem`, `GoalItem` — в `SupabaseRepository.kt`

### 3. Навігація:
- ВСЯ логіка навігації в `MainActivity.kt`
- Екрани отримують callbacks (`onBack`, `onOpenChat`, `onOpenStrategy`, etc.)
- Не створювати окремі Navigation компоненти

### 4. Репозиторії:
- `SupabaseRepository` — ТІЛЬКИ для роботи з базою
- `GeminiRepository` — ТІЛЬКИ для роботи з AI
- Не змішувати логіку

---

## 📋 ТАБЛИЦІ SUPABASE

```
users
  └── goals (макс. 3)
        ├── strategic_steps (10 шт.)
        ├── weekly_tasks (10 шт. × N тижнів)
        └── chat_messages (історія)
  └── assessment_results (історія оцінок)
  └── conversations → messages (legacy чат)
```

---

## 🔄 ПОТІК ДАНИХ

### Перша оцінка:
```
AssessmentScreen (15 питань)
    ↓ answers: Map<Int, String>
GeminiRepository.generateGoalWithPlan()
    ↓ GeneratedPlan
SupabaseRepository.saveCompletePlan()
    ↓ goalId
GoalDashboardScreen (показ завдань)
```

### Генерація наступного тижня:
```
GoalDashboardScreen (тиждень завершено)
    ↓ completedTasks, skippedTasks
GeminiRepository.generateNextWeekTasks()
    ↓ List<GeneratedWeeklyTask>
SupabaseRepository.saveWeeklyTasks(weekNumber + 1)
    ↓ success
GoalDashboardScreen (показ нового тижня)
```

### Чат з контекстом:
```
ChatScreen (повідомлення користувача)
    ↓
GeminiRepository.buildAIContext() + sendMessageWithContext()
    ↓ AI response
ChatScreen (показ відповіді)
```

### Управління цілями (NEW):
```
GoalsListScreen
    ├── getGoals(userId) → показ списку
    ├── setPrimaryGoal(userId, goalId) → зміна головної
    ├── deleteGoal(goalId) → видалення з підтвердженням
    └── onAddNewGoal → AssessmentScreen (нова оцінка)
```

---

## 📝 НОТАТКИ

_Додавай сюди важливі зміни під час розробки:_

- 01.12.2025: Створено документацію структури проекту
- 01.12.2025: Додано GoalsListScreen.kt — повноцінний екран управління цілями
- ...