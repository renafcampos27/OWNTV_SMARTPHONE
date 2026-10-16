package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<ProfileEntity>

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ProfileEntity?>

    @Query("UPDATE profiles SET avatarId = :avatarId WHERE id = :id")
    suspend fun setAvatar(id: Long, avatarId: Int)

    /** Point a profile at a picture of its own, or null for the drawn tile. Used by restore. */
    @Query("UPDATE profiles SET avatarPath = :path WHERE id = :id")
    suspend fun setAvatarPath(id: Long, path: String?): Int

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    /** Compare protection atomically: a grant for an old PIN/parental state never overwrites a new one. */
    @Query("UPDATE profiles SET name = :name, avatarId = :avatarId, isKids = :isKids, pinHash = :pinHash WHERE id = :id AND pinHash IS :expectedPinHash AND isKids = :expectedKids")
    suspend fun updateAuthorized(id: Long, expectedPinHash: String?, expectedKids: Boolean,
        name: String, avatarId: Int, isKids: Boolean, pinHash: String?): Int

    @Query("DELETE FROM profiles WHERE id = :id AND pinHash IS :expectedPinHash AND isKids = :expectedKids AND (SELECT COUNT(*) FROM profiles) > 1")
    suspend fun deleteAuthorized(id: Long, expectedPinHash: String?, expectedKids: Boolean): Int

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("UPDATE profiles SET pinHash = :pinHash WHERE id = :id")
    suspend fun setPin(id: Long, pinHash: String?)
}
