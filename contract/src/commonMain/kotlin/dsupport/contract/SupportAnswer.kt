package dsupport.contract

import kotlinx.serialization.Serializable

// Versão mínima; os demais campos entram na etapa 8.1.
@Serializable
data class SupportAnswer(val answer: String = "")
