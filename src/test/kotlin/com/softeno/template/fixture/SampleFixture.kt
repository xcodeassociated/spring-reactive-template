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

// todo: refactor and randomize
interface PermissionFixture {

    fun aPermission(): Permission {
        // todo: randomize data
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

    fun aPermissionToSave(): Permission {
        // todo: randomize data
        return Permission(
            id = null,
            createdDate = null,
            createdByUser = null,
            modifiedByUser = null,
            lastModifiedDate = null,
            version = null,
            name = "some permission",
            description = "some description"
        )
    }
}