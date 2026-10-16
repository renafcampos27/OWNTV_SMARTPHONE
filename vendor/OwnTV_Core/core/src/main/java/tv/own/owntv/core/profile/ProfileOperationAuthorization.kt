package tv.own.owntv.core.profile

import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.util.Pin

enum class ProfileSensitiveAction { CHANGE_PIN, REDUCE_RESTRICTIONS, DELETE }

/** Process-local, one-use proof; not serializable into a backup or transferable to another target. */
class ProfileAuthorizationGrant internal constructor(
    internal val profileId: Long, internal val pinHash: String?, internal val isKids: Boolean,
    internal val actions: Set<ProfileSensitiveAction>, internal val expiresAtMs: Long,
)

class ProfileAuthorizationRequired : SecurityException("Profile protection changed or authorization is required")

internal class ProfileOperationAuthorization(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val issued = java.util.IdentityHashMap<ProfileAuthorizationGrant, Boolean>()

    @Synchronized fun issue(profile: ProfileEntity, pin: String, actions: Set<ProfileSensitiveAction>): ProfileAuthorizationGrant? {
        if (!Pin.verify(pin, profile.pinHash)) return null
        issued.keys.removeAll { it.expiresAtMs <= nowMs() }
        if (issued.size >= 32) issued.clear()
        return ProfileAuthorizationGrant(profile.id, profile.pinHash, profile.isKids, actions.toSet(), nowMs() + 120_000)
            .also { issued[it] = true }
    }

    @Synchronized fun consume(profile: ProfileEntity, actions: Set<ProfileSensitiveAction>, grant: ProfileAuthorizationGrant?): Boolean {
        if (actions.isEmpty()) return true
        if (grant == null || issued.remove(grant) == null) return false
        return nowMs() < grant.expiresAtMs && grant.profileId == profile.id && grant.pinHash == profile.pinHash &&
            grant.isKids == profile.isKids && grant.actions == actions
    }
}

internal fun sensitiveProfileEdit(profile: ProfileEntity, isKids: Boolean, pin: String?): Set<ProfileSensitiveAction> {
    if (profile.pinHash.isNullOrBlank()) return emptySet()
    return buildSet {
        if (pin != null) add(ProfileSensitiveAction.CHANGE_PIN)
        if (profile.isKids && !isKids) add(ProfileSensitiveAction.REDUCE_RESTRICTIONS)
    }
}
