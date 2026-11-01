package com.softeno.template.users.permission.api

import com.softeno.template.common.BaseException


open class PermissionException(message: String, cause: Throwable?) : BaseException(message, cause)
class PermissionNotFoundException(message: String) : PermissionException(message, null)
