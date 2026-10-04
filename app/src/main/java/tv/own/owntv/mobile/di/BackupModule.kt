package tv.own.owntv.mobile.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.backup.BackupAppSettings
import tv.own.owntv.mobile.backup.MobileBackupAppSettings

val mobileBackupModule = module {
    single<BackupAppSettings> { MobileBackupAppSettings(androidContext()) }
}
