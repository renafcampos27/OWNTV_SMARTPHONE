package tv.own.owntv.core.profile

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.util.Pin

class ProfileOperationAuthorizationTest {
    private val protected = ProfileEntity(id = 1, name = "Protected", avatarColor = 0, isKids = true, pinHash = Pin.hash("1234"))

    @Test fun sensitiveOperationsRequireProofWhileHarmlessChangesDoNot() {
        val policy = ProfileOperationAuthorization()
        assertTrue(sensitiveProfileEdit(protected, isKids = true, pin = null).isEmpty())
        assertTrue(policy.consume(protected, emptySet(), null))
        assertFalse(policy.consume(protected, sensitiveProfileEdit(protected, false, ""), null))
        assertEquals(setOf(ProfileSensitiveAction.CHANGE_PIN, ProfileSensitiveAction.REDUCE_RESTRICTIONS), sensitiveProfileEdit(protected, false, ""))
        assertTrue(sensitiveProfileEdit(protected.copy(pinHash = null), false, "5555").isEmpty())
    }

    @Test fun wrongPinDoesNotIssueAGrant() {
        assertNull(ProfileOperationAuthorization().issue(protected, "9999", setOf(ProfileSensitiveAction.DELETE)))
    }

    @Test fun grantsAreTargetAndActionBoundAndCannotBeReused() {
        val policy = ProfileOperationAuthorization()
        val actions = setOf(ProfileSensitiveAction.DELETE)
        val wrongTarget = policy.issue(protected, "1234", actions)!!
        assertFalse(policy.consume(protected.copy(id = 2), actions, wrongTarget))
        val wrongAction = policy.issue(protected, "1234", actions)!!
        assertFalse(policy.consume(protected, setOf(ProfileSensitiveAction.CHANGE_PIN), wrongAction))
        val valid = policy.issue(protected, "1234", actions)!!
        assertTrue(policy.consume(protected, actions, valid))
        assertFalse(policy.consume(protected, actions, valid))
    }

    @Test fun changingThePinOrParentalStateInvalidatesAnOutstandingGrant() {
        val policy = ProfileOperationAuthorization()
        val actions = setOf(ProfileSensitiveAction.DELETE)
        val changedPin = policy.issue(protected, "1234", actions)!!
        assertFalse(policy.consume(protected.copy(pinHash = Pin.hash("1234")), actions, changedPin))
        val changedKids = policy.issue(protected, "1234", actions)!!
        assertFalse(policy.consume(protected.copy(isKids = false), actions, changedKids))
    }

    @Test fun grantsExpireAndCannotBeMovedBetweenAuthorizers() {
        var now = 0L
        val policy = ProfileOperationAuthorization { now }
        val actions = setOf(ProfileSensitiveAction.DELETE)
        val expired = policy.issue(protected, "1234", actions)!!
        now = 120_000
        assertFalse(policy.consume(protected, actions, expired))
        val another = policy.issue(protected, "1234", actions)!!
        assertFalse(ProfileOperationAuthorization { now }.consume(protected, actions, another))
    }
}
