package iter.ai.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kotlin.jvm.JvmRecord

@JvmRecord
data class EquipmentDraftRequest(
    @field:NotNull
    @field:Size(min = 1, max = 5)
    val imageKeys: List<@NotBlank @Size(max = 500) String>?,

    @field:Size(max = 100)
    val name: String?,

    val category: String?
)
