package com.softeno.template.users.http.coroutine

open class BaseException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

open class PermissionException(message: String, cause: Throwable?) : BaseException(message, cause)
class PermissionNotFoundException(message: String) : PermissionException(message, null)

open class UserException(message: String, cause: Throwable?) : BaseException(message, cause)
class UserNotFoundException(message: String) : UserException(message, null)

class VersionMissingException(message: String ): BaseException(message, null)
