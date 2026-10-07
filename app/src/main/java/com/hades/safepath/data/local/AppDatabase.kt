package com.hades.safepath.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hades.safepath.data.model.Alert
import com.hades.safepath.data.model.PackItem

@Database(entities = [Alert::class, PackItem::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun packItemDao(): PackItemDao
}
