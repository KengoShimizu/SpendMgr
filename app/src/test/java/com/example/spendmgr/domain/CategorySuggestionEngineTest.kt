package com.example.spendmgr.domain

import com.example.spendmgr.data.CategoryHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CategorySuggestionEngineTest {

    private lateinit var repository: CategoryHistoryRepository
    private lateinit var engine: CategorySuggestionEngine

    @BeforeEach
    fun setUp() {
        repository = mockk()
        engine = CategorySuggestionEngine(repository)
    }

    @Test
    fun `suggest returns matching categories from repository`() = runTest {
        coEvery { repository.searchByPrefix("食") } returns listOf("食費", "食料品")

        val result = engine.suggest("食")

        assertEquals(listOf("食費", "食料品"), result)
    }

    @Test
    fun `suggest returns empty list when prefix is empty`() = runTest {
        val result = engine.suggest("")

        assertEquals(emptyList<String>(), result)
        coVerify(exactly = 0) { repository.searchByPrefix(any()) }
    }

    @Test
    fun `suggest returns empty list when no matches`() = runTest {
        coEvery { repository.searchByPrefix("xyz") } returns emptyList()

        val result = engine.suggest("xyz")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `recordCategory saves non-blank category`() = runTest {
        coEvery { repository.save("食費") } returns Unit

        engine.recordCategory("食費")

        coVerify(exactly = 1) { repository.save("食費") }
    }

    @Test
    fun `recordCategory does not save blank category`() = runTest {
        engine.recordCategory("")
        engine.recordCategory("   ")

        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `suggest delegates prefix to repository`() = runTest {
        coEvery { repository.searchByPrefix("ラン") } returns listOf("ランチ", "ランニング用品")

        val result = engine.suggest("ラン")

        assertEquals(listOf("ランチ", "ランニング用品"), result)
        coVerify(exactly = 1) { repository.searchByPrefix("ラン") }
    }
}
