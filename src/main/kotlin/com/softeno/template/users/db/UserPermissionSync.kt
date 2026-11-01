package com.softeno.template.users.db

import com.softeno.template.users.permission.db.PermissionDocument
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component

/**
 * Keeps the permission snapshots embedded in [UserDocument]s honest (ADR-0005):
 * a permission update replaces the snapshot in every user holding it, a delete
 * pulls it out of every user. Fetch + saveAll (not raw arrayFilters) keeps this
 * coroutine-idiomatic and participates in the documents' optimistic locking.
 *
 * Consistency window: the fan-out is not atomic with the permission write — a
 * user read racing it sees the previous snapshot at most one propagation
 * behind; a user saved concurrently with the fan-out may keep the old snapshot
 * until the next permission change. Acceptable for the template; document
 * jobs needing strict consistency should join on `permission` instead.
 */
@Component
class UserPermissionSync(
    private val userRepository: UserRepository,
) {
    suspend fun syncPermission(permission: PermissionDocument) {
        val snapshot = PermissionSnapshot.of(permission)
        val affected = userRepository.findByPermissionsPermissionId(permission.id!!).toList()
        // save (not saveAll): coroutine saveAll does not apply version-checked
        // updates for existing documents — verified empirically (Spring Data Mongo 5.1)
        affected.forEach { user ->
            userRepository.save(user.copy(permissions = user.permissions.map { if (it.permissionId == snapshot.permissionId) snapshot else it }.toSet()))
        }
    }

    suspend fun pullPermission(permissionId: String) {
        val affected = userRepository.findByPermissionsPermissionId(permissionId).toList()
        affected.forEach { user ->
            userRepository.save(user.copy(permissions = user.permissions.filterNot { it.permissionId == permissionId }.toSet()))
        }
    }
}
