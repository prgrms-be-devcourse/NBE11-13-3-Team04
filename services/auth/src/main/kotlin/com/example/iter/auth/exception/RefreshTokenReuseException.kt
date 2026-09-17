package com.example.iter.auth.exception

import com.example.iter.common.exception.CustomException
import com.example.iter.common.exception.ErrorCode

class RefreshTokenReuseException : CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
