package test

import drone.MavsdkHandler
import io.mockk.mockk
import supabase.SupabaseMessageHandler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MavsdkHandlerTest {
    val supabaseHandler = mockk<SupabaseMessageHandler>()
    val handler = MavsdkHandler(supabaseHandler)

    @BeforeTest
    fun setUp() {
        val handler = MavsdkHandler(supabaseHandler)
    }

    @AfterTest
    fun tearDown() {
    }

    @Test
    fun startUploadImagesJob() {
        handler.connectToDrone()
    }
}