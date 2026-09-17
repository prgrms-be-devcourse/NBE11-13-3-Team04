package com.example.iter.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// monolith(apps:monolith)와 별개로 뜨는 독립 프로세스다. 같은 리포지토리 안에 있지만
// 서로 클래스를 직접 참조하지 않고, DB 스키마도 나눠 쓰며(iter_chat), monolith와는
// Redis(티켓/그랜트/이벤트 스트림)로만 통신한다 — MSA-in-monorepo 구성.
//
// main()이 top-level 함수라 컴파일된 파사드 클래스 이름은 ChatApplicationKt 이다.
// build.gradle의 springBoot { mainClass = '...ChatApplicationKt' } 가 이걸 가리킨다.
@SpringBootApplication
class ChatApplication

fun main(args: Array<String>) {
    runApplication<ChatApplication>(*args)
}
