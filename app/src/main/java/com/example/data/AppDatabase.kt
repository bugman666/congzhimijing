package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Account::class, Site::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secret_web_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AppRepository(private val appDao: AppDao) {
    val allAccounts = appDao.getAllAccounts()
    val allSites = appDao.getAllSites()

    suspend fun getAccountByUsername(username: String) = appDao.getAccountByUsername(username)
    suspend fun insertAccount(account: Account) = appDao.insertAccount(account)
    suspend fun updateAccount(account: Account) = appDao.updateAccount(account)
    suspend fun deleteAccount(account: Account) = appDao.deleteAccount(account)

    suspend fun getSiteByPasscode(passcode: String) = appDao.getSiteByPasscode(passcode)
    suspend fun insertSite(site: Site) = appDao.insertSite(site)
    suspend fun updateSite(site: Site) = appDao.updateSite(site)
    suspend fun deleteSite(site: Site) = appDao.deleteSite(site)
}
