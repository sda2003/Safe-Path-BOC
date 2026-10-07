package com.hades.safepath.family

import com.hades.safepath.auth.PhoneNumberNormalizer
import com.hades.safepath.auth.ServerUserProfile
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.data.dao.FamilyContactDao
import com.hades.safepath.data.entity.FamilyContactEntity
import com.hades.safepath.util.NetworkMonitor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FamilyContactRecord(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("contact_name") val contactName: String,
    @SerialName("contact_phone") val contactPhone: String
)

@Serializable
private data class FamilyContactRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("contact_name") val contactName: String,
    @SerialName("contact_phone") val contactPhone: String
)

sealed class FamilyCircleError(val message: String) {
    object NotVerified : FamilyCircleError("Verify your phone to use Family Circle.")
    object Offline : FamilyCircleError("Internet required to manage contacts.")
    object LimitReached : FamilyCircleError("Contact limit reached for your plan.")
    data class InvalidPhone(val detail: String) : FamilyCircleError(detail)
    data class Server(val detail: String) : FamilyCircleError(detail)
}

@Singleton
class FamilyCircleRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val verificationRepository: VerificationRepository,
    private val familyContactDao: FamilyContactDao,
    private val networkMonitor: NetworkMonitor
) {

    fun observeContacts(ownerId: String): Flow<List<FamilyContactEntity>> =
        familyContactDao.observeByOwner(ownerId)

    suspend fun refreshContactsFromServer(): Result<List<FamilyContactEntity>> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(Exception(FamilyCircleError.Offline.message))
        }
        val profile = verificationRepository.getServerUserProfile()
            ?: return Result.failure(Exception(FamilyCircleError.NotVerified.message))
        if (!profile.verified) {
            return Result.failure(Exception(FamilyCircleError.NotVerified.message))
        }

        return runCatching {
            val rows = supabaseClient.postgrest["family_contacts"].select {
                filter { eq("owner_id", profile.id) }
            }.decodeList<FamilyContactRow>()

            val entities = rows.map {
                FamilyContactEntity(
                    id = it.id,
                    ownerId = it.ownerId,
                    contactName = it.contactName,
                    contactPhone = it.contactPhone
                )
            }
            familyContactDao.deleteAllForOwner(profile.id)
            familyContactDao.upsertAll(entities)
            entities
        }
    }

    suspend fun addContact(name: String, rawPhone: String): Result<FamilyContactEntity> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(Exception(FamilyCircleError.Offline.message))
        }

        val profile = verificationRepository.getServerUserProfile()
            ?: return Result.failure(Exception(FamilyCircleError.NotVerified.message))
        if (!profile.verified) {
            return Result.failure(Exception(FamilyCircleError.NotVerified.message))
        }

        val phone = PhoneNumberNormalizer.normalize(rawPhone)
            ?: return Result.failure(Exception(FamilyCircleError.InvalidPhone("Enter a valid phone number.").message))

        val maxContacts = FamilyCircleLimits.maxContactsForTier(profile.tier)
        val currentCount = runCatching {
            supabaseClient.postgrest["family_contacts"].select {
                filter { eq("owner_id", profile.id) }
            }.decodeList<FamilyContactRow>().size
        }.getOrElse {
            familyContactDao.countByOwner(profile.id)
        }

        if (currentCount >= maxContacts) {
            val tierLabel = if (profile.tier == "pro") "Pro" else "Free"
            return Result.failure(
                Exception("Contact limit reached ($tierLabel plan: $maxContacts). Upgrade to Pro for up to 5 contacts.")
            )
        }

        return runCatching {
            val record = FamilyContactRecord(
                ownerId = profile.id,
                contactName = name.trim().ifBlank { "Contact" },
                contactPhone = phone
            )
            val inserted = supabaseClient.postgrest["family_contacts"].insert(record) {
                select()
            }.decodeSingle<FamilyContactRow>()

            val entity = FamilyContactEntity(
                id = inserted.id,
                ownerId = inserted.ownerId,
                contactName = inserted.contactName,
                contactPhone = inserted.contactPhone
            )
            familyContactDao.upsert(entity)
            entity
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(FamilyCircleError.Server("Could not add contact. Try again.").message)) }
        )
    }

    suspend fun removeContact(contactId: String): Result<Unit> {
        if (!networkMonitor.isOnline()) {
            return Result.failure(Exception(FamilyCircleError.Offline.message))
        }
        val profile = verificationRepository.getServerUserProfile()
            ?: return Result.failure(Exception(FamilyCircleError.NotVerified.message))

        return runCatching {
            supabaseClient.postgrest["family_contacts"].delete {
                filter {
                    eq("id", contactId)
                    eq("owner_id", profile.id)
                }
            }
            familyContactDao.delete(contactId, profile.id)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(Exception(FamilyCircleError.Server("Could not remove contact.").message)) }
        )
    }

    suspend fun getServerProfileOrFail(): ServerUserProfile? =
        verificationRepository.getServerUserProfile()?.takeIf { it.verified }
}
