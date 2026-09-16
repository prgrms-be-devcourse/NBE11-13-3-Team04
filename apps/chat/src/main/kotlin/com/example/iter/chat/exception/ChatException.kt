package com.example.iter.chat.exception

class ChatException(val errorCode: ChatErrorCode) : RuntimeException(errorCode.message)
