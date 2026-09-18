package iter.chat.repository

import iter.chat.domain.Message
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface MessageRepository : CoroutineCrudRepository<Message, Long> {

    // 과거 메시지 커서 페이지(REST GET /rooms/{roomId}/messages) 용.
    // id 내림차순 = idx_room_id_id(room_id, id DESC)를 그대로 탄다.
    fun findByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, id: Long, pageable: Pageable): Flow<Message>

    fun findByRoomIdOrderByIdDesc(roomId: Long, pageable: Pageable): Flow<Message>

    // 방 목록의 "마지막 메시지"용. 정렬은 findByRoomIdOrderByIdDesc와 같은 인덱스를 탄다.
    suspend fun findFirstByRoomIdOrderByIdDesc(roomId: Long): Message?

    // 미읽음 수 계산 — last_read_message_id가 있으면 그 이후 것만, 없으면 전체를 센다
    // (참여자 서비스에서 null 여부에 따라 둘 중 하나를 호출한다).
    //
    // 내가 보낸 메시지는 빼고 센다. 예전에는 sender 조건이 아예 없어서, 다른 기기/탭에서
    // 내가 보낸 메시지가 내 안읽음 배지를 올렸다.
    //
    // 파생 쿼리(...AndSenderIdNot)를 쓰지 않고 직접 쓰는 이유: sender_id <> :userId 로 번역되는데
    // SQL에서 NULL <> 값은 TRUE가 아니라 NULL이라, sender_id가 NULL인 SYSTEM 메시지까지
    // 통째로 빠져버린다. SYSTEM 메시지는 안읽음에 포함돼야 하므로 NULL을 명시적으로 살린다.
    @Query(
        """
        select count(*) from messages
        where room_id = :roomId and id > :id and (sender_id is null or sender_id <> :userId)
        """,
    )
    suspend fun countUnreadAfter(roomId: Long, id: Long, userId: Long): Long

    @Query(
        """
        select count(*) from messages
        where room_id = :roomId and (sender_id is null or sender_id <> :userId)
        """,
    )
    suspend fun countUnreadAll(roomId: Long, userId: Long): Long
}
