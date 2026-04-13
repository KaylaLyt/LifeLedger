package com.codex.offlineledger.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.codex.offlineledger.data.entity.GiftRecordEntity
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.PersonEntity
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.SnapshotExpenseEntity
import com.codex.offlineledger.data.entity.TodoEntity

data class SnapshotWithDetails(
    @Embedded val snapshot: SnapshotEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "snapshotId",
    )
    val balances: List<SnapshotBalanceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "snapshotId",
    )
    val expenses: List<SnapshotExpenseEntity>,
)

data class PersonWithGifts(
    @Embedded val person: PersonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "personId",
    )
    val gifts: List<GiftRecordEntity>,
)

data class TodoWithRule(
    @Embedded val todo: TodoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "todoId",
    )
    val rule: RecurrenceRuleEntity?,
)

data class NoteCategoryWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
    val noteCount: Int,
)

data class NoteWithCategory(
    @Embedded val note: NoteEntity,
)
