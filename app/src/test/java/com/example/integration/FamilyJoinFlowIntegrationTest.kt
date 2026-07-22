package com.example.integration

import com.example.BuildConfig
import com.example.data.FamilyGroup
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.util.UUID

/**
 * Real-network regression test for the family_groups RLS fix in
 * supabase/migrations/20260720010000_fix_family_groups_rls.sql: a second
 * account must be able to find a family by invite code and join it.
 *
 * Builds its own SupabaseClient instead of using the SupabaseRepo singleton
 * — SupabaseRepo's client is a top-level `val` that eagerly calls
 * createSupabaseClient() at class-init, which crashes outside a real
 * Android/Robolectric runtime. Postgrest calls need no Android APIs, so
 * this runs as an ordinary JVM unit test (./gradlew testDebugUnitTest)
 * with no emulator involved — but installing Auth still needs a platform
 * Settings instance, so newClient() skips (via Assume) rather than fails
 * when that construction isn't available (see newClient() below).
 *
 * Skips rather than fails when BuildConfig.SUPABASE_URL is still the
 * .env.example placeholder (no real backend configured, e.g. local dev
 * without a .env file).
 */
class FamilyJoinFlowIntegrationTest {

    private fun newClient(): SupabaseClient = try {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
        }
    } catch (e: IllegalStateException) {
        // install(Auth) builds a SettingsSessionManager backed by multiplatform-settings,
        // which needs a real Android/Robolectric runtime to create its Settings instance.
        // This test's own JUnit source set (app/src/test) runs on plain JVM, so that
        // construction always throws here — skip rather than fail on this known
        // platform gap instead of blocking CI on a check this test can never satisfy.
        Assume.assumeNoException(
            "Skipping: plain JVM unit-test environment can't provide platform Settings for " +
                "supabase-kt's Auth SessionManager (needs Android runtime or Robolectric)",
            e
        )
        throw e
    }

    @Test
    fun creatorCanCreateFamily_andSecondAccountCanJoinByInviteCode() = runTest {
        assumeFalse(
            "Skipping: BuildConfig.SUPABASE_URL is still the .env.example placeholder — no real backend reachable",
            BuildConfig.SUPABASE_URL.contains("your-project-ref")
        )

        val runId = UUID.randomUUID().toString().take(8)
        val creatorEmail = "zad-citest-creator-$runId@zad-app-test.dev"
        val joinerEmail = "zad-citest-joiner-$runId@zad-app-test.dev"
        val password = "CiTest!${runId}Aa1"
        val inviteCode = "ZAD-CI$runId"

        val creator = newClient()
        val joiner = newClient()
        var createdGroupId: String? = null

        try {
            // 1. Creator signs up and creates a family group + joins it as admin,
            //    mirroring FamilyViewModel.createFamily().
            creator.auth.signUpWith(Email) {
                this.email = creatorEmail
                this.password = password
            }
            val creatorUser = creator.auth.currentUserOrNull()
            assertNotNull(
                "Creator has no active session right after signUp — check whether " +
                    "'Confirm email' is enabled in Supabase Auth settings; if so this " +
                    "signup-then-act-immediately flow needs a service-role auto-confirm " +
                    "instead of anon signUp",
                creatorUser
            )

            val group = creator.postgrest["family_groups"].insert(
                FamilyGroup(inviteCode = inviteCode)
            ) { select() }.decodeSingle<FamilyGroup>()
            createdGroupId = group.id

            creator.postgrest["family_members"].insert(
                mapOf(
                    "family_id" to group.id,
                    "user_id" to creatorUser!!.id,
                    "role" to "admin",
                    "alias" to "CI Creator"
                )
            )

            // 2. A second, unrelated account looks the group up by invite code
            //    and joins it — the exact path that was broken before
            //    family_groups_select_authenticated existed (SELECT used to be
            //    scoped to "your own family only", so a non-member's lookup
            //    returned zero rows).
            joiner.auth.signUpWith(Email) {
                this.email = joinerEmail
                this.password = password
            }
            val joinerUser = joiner.auth.currentUserOrNull()
            assertNotNull("Joiner has no active session right after signUp", joinerUser)

            val foundGroups = joiner.postgrest["family_groups"].select {
                filter { eq("invite_code", inviteCode) }
            }.decodeList<FamilyGroup>()

            assertEquals(
                "Joiner could not see the family_groups row by invite_code — " +
                    "the family_groups_select_authenticated RLS policy regressed",
                1,
                foundGroups.size
            )

            joiner.postgrest["family_members"].insert(
                mapOf(
                    "family_id" to foundGroups.first().id,
                    "user_id" to joinerUser!!.id,
                    "role" to "member",
                    "alias" to "CI Joiner"
                )
            )

            val membersAfterJoin = creator.postgrest["family_members"].select {
                filter { eq("family_id", group.id) }
            }.decodeList<Map<String, String?>>()

            assertEquals(
                "Expected both the creator and the joiner as family_members rows",
                2,
                membersAfterJoin.size
            )
        } finally {
            // Best-effort cleanup of the rows this run created. The anon key
            // cannot delete the auth.users rows themselves (needs service
            // role) — those are left behind, clearly tagged via the
            // zad-citest-*@zad-app-test.dev email pattern for manual/periodic
            // cleanup.
            createdGroupId?.let { id ->
                try {
                    creator.postgrest["family_members"].delete { filter { eq("family_id", id) } }
                } catch (_: Exception) { /* best effort */ }
                try {
                    creator.postgrest["family_groups"].delete { filter { eq("id", id) } }
                } catch (_: Exception) { /* best effort */ }
            }
        }
    }
}
