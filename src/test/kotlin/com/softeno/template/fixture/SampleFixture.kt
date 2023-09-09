@file:JvmName("SampleFixture")

package com.softeno.template.fixture

import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.sample.http.dto.SampleResponseDto

class SampleResponseDtoFixture {
    companion object {
        @JvmStatic
        fun someDto(data: String) = SampleResponseDto(data)
    }
}

// todo: refactor and randomize
interface PermissionFixture {

    fun aPermission(): PermissionDocument {
        // todo: randomize data
        return PermissionDocument(
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

    fun aPermissionToSave(): PermissionDocument {
        // todo: randomize data
        return PermissionDocument(
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