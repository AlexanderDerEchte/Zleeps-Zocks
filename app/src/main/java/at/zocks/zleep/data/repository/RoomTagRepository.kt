package at.zocks.zleep.data.repository

import at.zocks.zleep.data.db.TagDao
import at.zocks.zleep.data.db.TagEntity
import at.zocks.zleep.data.db.toDomain
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTagRepository @Inject constructor(private val dao: TagDao) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> = dao.observeTags().map { list -> list.map { it.toDomain() } }

    override suspend fun getByKey(key: String): Tag? = dao.getByKey(key)?.toDomain()

    override suspend fun addCustomTag(label: String): Long {
        val trimmed = label.trim()
        require(trimmed.isNotEmpty()) { "Leerer Tag" }
        return dao.insert(TagEntity(key = null, label = trimmed))
    }

    override suspend fun deleteTag(id: Long) = dao.delete(id)
}
