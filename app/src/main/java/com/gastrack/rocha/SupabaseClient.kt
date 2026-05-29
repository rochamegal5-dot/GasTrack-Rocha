package com.gastrack.rocha

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    // ⚠️ REEMPLAZAR CON TUS DATOS DE SUPABASE ⚠️
    private const val SUPABASE_URL = "https://plnodukthcnnwmxlzwsm.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBsbm9kdWt0aGNubndteGx6d3NtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAwNTkwMTcsImV4cCI6MjA5NTYzNTAxN30.WkTzFvNiM0BotwTLtxzQpq9-b08stueIlsp8K0b9iq8"

    val client: SupabaseClient by lazy {
        createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
            install(Postgrest)
        }
    }
}
