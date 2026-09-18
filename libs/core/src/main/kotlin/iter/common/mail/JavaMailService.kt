package iter.common.mail

import org.slf4j.LoggerFactory
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

private val log = LoggerFactory.getLogger(JavaMailService::class.java)

// 알림(SSE)과 달리 메일은 전송 자체가 I/O 대기가 길어 요청 스레드를 막지 않도록 비동기로 보냄
// 실패해도 알림을 만든 트랜잭션엔 영향이 없어야 하므로 예외를 밖으로 던지지 않고 로그만 남김
@Service
class JavaMailService(
    private val javaMailSender: JavaMailSender,
) : MailService {

    @Async("mailExecutor")
    override fun send(message: MailMessage) {
        val mailMessage = SimpleMailMessage()
        mailMessage.setTo(message.to)
        mailMessage.setFrom(message.from)
        mailMessage.setSubject(message.subject)
        mailMessage.setText(message.body)

        try {
            javaMailSender.send(mailMessage)
        } catch (e: MailException) {
            log.warn("메일 발송 실패", e)
        }
    }
}
