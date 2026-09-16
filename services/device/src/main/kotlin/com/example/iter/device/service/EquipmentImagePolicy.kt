package com.example.iter.device.service

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode
import org.springframework.stereotype.Component

@Component
class EquipmentImagePolicy {

    fun validateMetadata(contentType: String, size: Long) {
        if (contentType !in ALLOWED_CONTENT_TYPES || size <= 0 || size > MAX_IMAGE_SIZE) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }
    }

    fun validateSignature(contentType: String?, header: ByteArray) {
        val valid = when (contentType ?: "") {
            "image/jpeg" -> isJpeg(header)
            "image/png" -> isPng(header)
            "image/webp" -> isWebp(header)
            else -> false
        }
        if (!valid) {
            throw CustomException(ErrorCode.INVALID_IMAGE)
        }
    }

    fun extensionOf(contentType: String?): String = when (contentType ?: "") {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> throw CustomException(ErrorCode.INVALID_IMAGE)
    }

    private fun isJpeg(header: ByteArray): Boolean =
        header.size >= 3 &&
            unsigned(header[0]) == 0xFF &&
            unsigned(header[1]) == 0xD8 &&
            unsigned(header[2]) == 0xFF

    private fun isPng(header: ByteArray): Boolean {
        val signature = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (header.size < signature.size) {
            return false
        }
        for (index in signature.indices) {
            if (unsigned(header[index]) != signature[index]) {
                return false
            }
        }
        return true
    }

    // 코틀린은 Byte 와 Char 를 암묵적으로 넓혀서 비교하지 않는다. 자바의 header[0] == 'R' 을
    // 그대로 옮기면 컴파일이 안 되고, 'R'.code.toByte() 로 명시해야 한다.
    private fun isWebp(header: ByteArray): Boolean =
        header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()

    private fun unsigned(value: Byte): Int = value.toInt() and 0xFF

    companion object {
        // 자바에서 EquipmentImagePolicy.MAX_IMAGE_COUNT 로 참조하므로 const 여야 한다.
        const val MIN_IMAGE_COUNT = 1
        const val MAX_IMAGE_COUNT = 5
        const val MAX_IMAGE_SIZE = 10L * 1024 * 1024

        private val ALLOWED_CONTENT_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
        )
    }
}
