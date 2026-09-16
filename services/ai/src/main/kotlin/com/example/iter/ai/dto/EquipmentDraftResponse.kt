package com.example.iter.ai.dto

import java.util.UUID
import kotlin.jvm.JvmRecord

@JvmRecord
data class EquipmentDraftResponse(val jobId: UUID?, val status: String?, val draft: Map<String, Any?>?, val message: String?)
