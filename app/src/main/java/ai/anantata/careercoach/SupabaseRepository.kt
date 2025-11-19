package ai.anantata.careercoach

import ai.anantata.careercoach.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock

class SupabaseRepository {
    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
    }

    suspend fun createConversation(conversationId: String) {
        try {
            supabase.from("conversations").insert(
                mapOf(
                    "id" to conversationId,
                    "created_at" to Clock.System.now().toString()
                )
            )
        } catch (e: Exception) {
            println("Error creating conversation: ${e.message}")
            // Ігноруємо помилку якщо конверсація вже існує
        }
    }

    suspend fun saveMessage(
        conversationId: String,
        role: String,
        content: String
    ) {
        try {
            // Обмежуємо довжину повідомлення до 5000 символів
            val truncatedContent = if (content.length > 5000) {
                content.take(5000) + "\n\n[Повідомлення обрізано через довжину]"
            } else {
                content
            }

            supabase.from("messages").insert(
                mapOf(
                    "conversation_id" to conversationId,
                    "role" to role,
                    "content" to truncatedContent,
                    "created_at" to Clock.System.now().toString()
                )
            )
        } catch (e: Exception) {
            println("Error saving message: ${e.message}")
            // Не падаємо якщо не вдалось зберегти - додаток продовжує працювати
        }
    }
}