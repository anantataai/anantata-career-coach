package ai.anantata.careercoach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val emoji: String,
    val description: String,
    val bullets: List<String> = emptyList()
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            title = "Ласкаво просимо в\nAnantata Career Coach",
            emoji = "🚀",
            description = "Розумний помічник для\nпланування кар'єри"
        ),
        OnboardingPage(
            title = "Сплануйте свою кар'єру",
            emoji = "🎯",
            description = "",
            bullets = listOf(
                "Відповісти на 15 питань",
                "Дізнатися сильні сторони",
                "Побачити що покращити"
            )
        ),
        OnboardingPage(
            title = "Отримайте план дій",
            emoji = "📋",
            description = "Ваш розумний помічник\nчекає на вас",
            bullets = listOf(
                "10 конкретних кроків",
                "З пріоритетами",
                "З термінами виконання"
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1a1f3a),
                        Color(0xFF0f172a)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button - фіксована висота для всіх сторінок
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                // Кнопка видима тільки на перших двох сторінках
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(onClick = onFinish) {
                        Text(
                            text = "Пропустити",
                            color = Color(0xFF94a3b8),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index)
                                    Color(0xFF6366f1)
                                else
                                    Color(0xFF334155)
                            )
                    )
                }
            }

            // Bottom button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    // Next button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366f1)
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Далі →",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Get Started button
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366f1)
                        ),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Розпочати →",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Emoji
        Text(
            text = page.emoji,
            fontSize = 80.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Title
        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Description (if exists)
        if (page.description.isNotEmpty()) {
            Text(
                text = page.description,
                fontSize = 18.sp,
                color = Color(0xFF94a3b8),
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier.padding(bottom = if (page.bullets.isNotEmpty()) 32.dp else 0.dp)
            )
        }

        // Bullets (if exist)
        if (page.bullets.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                page.bullets.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10b981)),
                            contentAlignment = Alignment.Center
                        ) {}

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = bullet,
                            fontSize = 17.sp,
                            color = Color(0xFFcbd5e1),
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}