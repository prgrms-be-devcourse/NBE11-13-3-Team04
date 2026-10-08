package iter.auth.exception

import iter.common.exception.CustomException
import iter.common.exception.ErrorCode

class RefreshTokenReuseException : CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
