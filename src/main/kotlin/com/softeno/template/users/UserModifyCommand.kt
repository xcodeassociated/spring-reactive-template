package com.softeno.template.users

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty

/** Create/update input for the users core (`role` = permission ids on the wire). */
data class UserModifyCommand(
    val name: String,
    val email: String,
    @param:JsonProperty("role")
    @param:JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissionIds: Set<String>,
    val version: Long?
)
