package com.betcoin.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the post-v1.0 migration harness (spec §3 Schema Versioning).
 *
 * Proves [MigrationTestHelper] can load the exported schema JSON from the androidTest assets and
 * create/validate the current schema version. Once v1.0 ships and the destructive fallback is
 * removed, real [androidx.room.migration.Migration]s will be tested the same way.
 */
@RunWith(AndroidJUnit4::class)
class BetCoinDatabaseMigrationTest {

  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    BetCoinDatabase::class.java,
  )

  @Test
  fun currentVersion_isCreatedFromExportedSchemaAndValidated() {
    helper.createDatabase(TEST_DB, DB_VERSION).close()
    helper.runMigrationsAndValidate(TEST_DB, DB_VERSION, true).close()
  }

  private companion object {
    const val TEST_DB = "migration-test.db"
  }
}
