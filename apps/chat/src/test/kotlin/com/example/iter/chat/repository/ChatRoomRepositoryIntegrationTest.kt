package com.example.iter.chat.repository

import com.example.iter.chat.domain.ChatRoom
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

// Docker(Testcontainers)가 있어야 돈다 — 기본 test 태스크에서 빼고 integrationTest
// 태스크에서만 실행한다(apps/chat/build.gradle). 로컬에 Docker가 없으면
// `./gradlew :apps:chat:test`(기본 build/check 경로)는 이 클래스를 건너뛴다.
//
// 컨테이너 하나에 @ServiceConnection을 붙이면 Spring Boot가 JDBC(Flyway용)와
// R2DBC(리포지토리용) 접속 정보를 둘 다 자동으로 만들어 준다 — 우리가 URL을 직접
// 조립할 필요가 없다. 기동 시 Flyway가 이 컨테이너에 V1__init_chat.sql을 실행한다.
@Testcontainers
@SpringBootTest
@Tag("integration")
class ChatRoomRepositoryIntegrationTest {

    @Autowired
    lateinit var chatRoomRepository: ChatRoomRepository

    @Test
    fun `방을 저장하면 equipmentId와 requesterId로 다시 찾을 수 있다`() = runTest {
        val saved = chatRoomRepository.save(
            ChatRoom(
                equipmentId = 1L,
                equipmentName = "전동 드릴",
                ownerId = 10L,
                requesterId = 20L,
            ),
        )

        val found = chatRoomRepository.findByEquipmentIdAndRequesterId(1L, 20L)
        assertThat(found).isNotNull
        val room = found!!

        assertThat(room.id).isEqualTo(saved.id)
        assertThat(room.equipmentName).isEqualTo("전동 드릴")
    }

    @Test
    fun `같은 장비+요청자로 다시 문의해도 새 방이 생기지 않는다`() = runTest {
        val first = chatRoomRepository.save(
            ChatRoom(equipmentId = 2L, equipmentName = "캠핑 텐트", ownerId = 11L, requesterId = 21L),
        )

        val existing = chatRoomRepository.findByEquipmentIdAndRequesterId(2L, 21L)

        assertThat(existing).isNotNull
        assertThat(existing!!.id).isEqualTo(first.id)
    }

    companion object {
        // Testcontainers 2.0에서 DB별 하드코딩 컨테이너 클래스(MySQLContainer 등)를 통째로
        // deprecated 처리했다(대체 API가 나온 정도로 보이나, 2.0.5 기준 여전히 동작한다).
        // 기능은 그대로라 지금은 억제하고 쓴다.
        @Suppress("DEPRECATION")
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
    }
}
