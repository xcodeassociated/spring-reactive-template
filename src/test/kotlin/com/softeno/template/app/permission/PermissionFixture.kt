package com.softeno.template.app.permission

import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.testsupport.R

/**
 * Permission fixtures. All fields are randomized per call via [R]; tests keep the
 * returned document and assert against its values, so no two tests (or runs)
 * ever see the same data.
 */
interface PermissionFixture {

    fun aPermissionToSave(
        name: String = R.name(),
        description: String = R.text()
    ): PermissionDocument =
        PermissionDocument(
            id = null,
            createdDate = null,
            createdByUser = null,
            modifiedByUser = null,
            lastModifiedDate = null,
            version = null,
            name = name,
            description = description
        )

    fun aPermission(): PermissionDocument =
        aPermissionToSave().copy(id = R.objectId(), version = 0L)
}
