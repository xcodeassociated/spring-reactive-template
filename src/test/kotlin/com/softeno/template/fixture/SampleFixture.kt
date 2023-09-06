@file:JvmName("SampleFixture")

package com.softeno.template.fixture

import com.softeno.template.sample.http.dto.SampleResponseDto
import com.softeno.template.users.db.Permission

class SampleResponseDtoFixture {
    companion object {
        @JvmStatic
        fun someDto(data: String) = SampleResponseDto(data)
    }
}

interface PermissionFixture {


    fun aPermission(): Permission {
        return Permission(
            id = "",
            createdDate = null,
            createdByUser = "",
            modifiedByUser = "",
            lastModifiedDate = null,
            version = 0.toLong(),
            name = "some permission",
            description = "some description"
        )
    }
}