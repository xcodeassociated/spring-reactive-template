package com.softeno.template.app.permission.api

import com.softeno.template.app.common.BaseException


open class PermissionException(message: String, cause: Throwable?) : BaseException(message, cause)
class PermissionNotFoundException(message: String) : PermissionException(message, null)
