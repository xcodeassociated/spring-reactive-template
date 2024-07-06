package com.softeno.template.app.user.api

import com.softeno.template.app.common.BaseException

open class UserException(message: String, cause: Throwable?) : BaseException(message, cause)

class UserNotFoundException(message: String) : UserException(message, null)

class VersionMissingException(message: String) : BaseException(message, null)
