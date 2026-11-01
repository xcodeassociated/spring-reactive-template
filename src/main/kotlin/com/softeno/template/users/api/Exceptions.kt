package com.softeno.template.users.api

import com.softeno.template.common.BaseException

open class UserException(message: String, cause: Throwable?) : BaseException(message, cause)

class UserNotFoundException(message: String) : UserException(message, null)

class VersionMissingException(message: String) : BaseException(message, null)
