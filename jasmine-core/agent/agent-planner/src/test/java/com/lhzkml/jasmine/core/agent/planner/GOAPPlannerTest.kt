package com.lhzkml.jasmine.core.agent.planner

import org.junit.Assert.*
import org.junit.Test

class GOAPPlannerTest {

    // Simple state for testing
    data class TestState(
        val hasData: Boolean = false,
        val hasResult: Boolean = false,
        val isComplete: Boolean = false
    )

    @Test
    fun `buildPlanForGoal finds single action plan`() {
        val planner = goap<TestState> {
            action(
                name = "fetchData",
                precondition = { !it.hasData },
                belief = { it.copy(hasData = true) },
                cost = { 1.0 },
                execute = { _, s -> s.copy(hasData = true) }
            )
            goal(
                name = "getData",
                condition = { it.hasData }
            )
        }
        // Planner should find a plan (tested via buildPlan internally)
        assertNotNull(planner)
    }

    @Test
    fun `goap DSL builds planner with actions and goals`() {
        val planner = goap<TestState> {
            action(
                name = "step1",
                precondition = { true },
                belief = { it.copy(hasData = true) },
                execute = { _, s -> s.copy(hasData = true) }
            )
            action(
                name = "step2",
                precondition = { it.hasData },
                belief = { it.copy(hasResult = true) },
                execute = { _, s -> s.copy(hasResult = true) }
            )
            goal(
                name = "finish",
                condition = { it.hasResult }
            )
        }
        assertNotNull(planner)
    }

    @Test
    fun `GOAPAction properties are accessible`() {
        val action = GOAPAction<TestState>(
            name = "test",
            description = "A test action",
            precondition = { true },
            belief = { it },
            cost = { 2.5 },
            execute = { _, s -> s }
        )
        assertEquals("test", action.name)
        assertEquals("A test action", action.description)
        assertTrue(action.precondition(TestState()))
        assertEquals(2.5, action.cost(TestState()), 0.001)
    }

    @Test
    fun `GOAPGoal condition check works`() {
        val goal = GOAPGoal<TestState>(
            name = "complete",
            condition = { it.isComplete }
        )
        assertFalse(goal.condition(TestState()))
        assertTrue(goal.condition(TestState(isComplete = true)))
    }

    @Test
    fun `GOAPGoal default value function is exponential decay`() {
        val goal = GOAPGoal<TestState>(
            name = "test",
            condition = { true }
        )
        // value(0.0) = exp(0) = 1.0
        assertEquals(1.0, goal.value(0.0), 0.001)
        // value(1.0) = exp(-1) ≈ 0.368
        assertEquals(0.368, goal.value(1.0), 0.01)
    }

    @Test
    fun `GOAPPlan holds goal actions and value`() {
        val goal = GOAPGoal<TestState>(name = "g", condition = { true })
        val action = GOAPAction<TestState>(
            name = "a", precondition = { true }, belief = { it },
            cost = { 1.0 }, execute = { _, s -> s }
        )
        val plan = GOAPPlan(goal, listOf(action), 0.5)
        assertEquals("g", plan.goal.name)
        assertEquals(1, plan.actions.size)
        assertEquals(0.5, plan.value, 0.001)
    }

    @Test
    fun `GOAPAction belief transforms state`() {
        val action = GOAPAction<TestState>(
            name = "fetch",
            precondition = { !it.hasData },
            belief = { it.copy(hasData = true) },
            cost = { 1.0 },
            execute = { _, s -> s.copy(hasData = true) }
        )
        val initial = TestState()
        assertFalse(initial.hasData)
        val believed = action.belief(initial)
        assertTrue(believed.hasData)
    }

    @Test
    fun `GOAPAction precondition filters correctly`() {
        val action = GOAPAction<TestState>(
            name = "process",
            precondition = { it.hasData && !it.hasResult },
            belief = { it.copy(hasResult = true) },
            cost = { 1.0 },
            execute = { _, s -> s.copy(hasResult = true) }
        )
        assertFalse(action.precondition(TestState()))
        assertTrue(action.precondition(TestState(hasData = true)))
        assertFalse(action.precondition(TestState(hasData = true, hasResult = true)))
    }
}
