package tv.own.owntv.core.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.ownTVTestDatabase
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.util.Pin

/** Production Room driver proves conditional protection checks and the last-profile constraint. */
@RunWith(AndroidJUnit4::class)
class ProfileAuthorizationDaoTest {
    private lateinit var db: OwnTVDatabase
    @Before fun setUp() { db = ownTVTestDatabase() }
    @After fun tearDown() { db.close() }

    @Test fun staleProtectionCannotEditOrDeleteAndAvatarDoesNotRevertIt() = runBlocking {
        val dao = db.profileDao()
        val oldHash = Pin.hash("1234")
        val newHash = Pin.hash("5678")
        val id = dao.insert(ProfileEntity(name = "Protected", avatarColor = 0, isKids = true, pinHash = oldHash))
        dao.insert(ProfileEntity(name = "Survivor", avatarColor = 0))
        dao.setPin(id, newHash)
        assertEquals(0, dao.updateAuthorized(id, oldHash, true, "Stale", 2, false, null))
        assertEquals(0, dao.deleteAuthorized(id, oldHash, true))
        assertEquals(0, dao.updateAuthorized(id, newHash, false, "Wrong state", 2, false, null))
        assertEquals(1, dao.setAvatarPath(id, "avatar-path"))
        val current = dao.getById(id)!!
        assertEquals(newHash, current.pinHash)
        assertTrue(current.isKids)
        assertEquals("Protected", current.name)
        assertEquals("avatar-path", current.avatarPath)
        assertEquals(1, dao.updateAuthorized(id, newHash, true, "Renamed", 2, true, newHash))
        assertEquals("avatar-path", dao.getById(id)!!.avatarPath)
        assertEquals(1, dao.deleteAuthorized(id, newHash, true))
        assertEquals(0, dao.setAvatarPath(id, "missing-profile"))
    }

    @Test fun unprotectedProfileUsesNullComparisonAndLastProfileCannotBeDeleted() = runBlocking {
        val dao = db.profileDao()
        val id = dao.insert(ProfileEntity(name = "Only", avatarColor = 0))
        assertEquals(1, dao.updateAuthorized(id, null, false, "Updated", 3, false, null))
        assertEquals(0, dao.deleteAuthorized(id, null, false))
        val other = dao.insert(ProfileEntity(name = "Other", avatarColor = 0))
        assertEquals(1, dao.deleteAuthorized(id, null, false))
        assertEquals(0, dao.deleteAuthorized(other, null, false))
        assertNotNull(dao.getById(other))
    }
}
