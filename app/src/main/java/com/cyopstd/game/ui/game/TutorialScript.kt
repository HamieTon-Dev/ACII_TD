package com.cyopstd.game.ui.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.AgentType

/**
 * What the tutorial points at.
 *
 * Only the two field readouts are pointed at with an arrow, and that is not a
 * limitation — they are the two things drawn inside the native Canvas that a
 * player has no other way of having named for them. Everything else the
 * tutorial teaches is a Compose control with its own label on it, and an arrow
 * to a thing that already says what it is adds nothing.
 */
enum class TutorialTarget { NONE, WAVE_READOUT, CRYPTO_READOUT }

/**
 * How the player gets past a step.
 *
 * A step that waits for an action shows no button, because a CONTINUE that
 * does nothing until you do something else is a button that looks broken.
 */
enum class TutorialGate {
    /** CONTINUE. */
    ACKNOWLEDGE,

    /** A yes/no question with its own two buttons. */
    ASK_BRIEFING,

    /** Waits for the player to do the thing. */
    ACTION
}

/**
 * One card of the guided first run.
 *
 * The script is a table rather than a `when` over an integer because three
 * separate things have to agree about each step — what the card says, what the
 * arrow points at, and where SKIP is allowed to sit — and three `when`s over
 * the same integer is three places to forget to update.
 */
data class TutorialStep(
    val title: String,
    val body: String,
    val target: TutorialTarget = TutorialTarget.NONE,
    val gate: TutorialGate = TutorialGate.ACKNOWLEDGE,
    /** Agent the player must have this many of before the step clears. */
    val requiresAgent: AgentType? = null,
    val requiresCount: Int = 0
)

object TutorialScript {

    const val INTRO = 0
    const val WAVE_READOUT = 1
    const val CRYPTO_READOUT = 2
    const val BRIEFING_OFFER = 3
    const val BRIEFING = 4
    const val OPEN_ROSTER = 5
    const val PICK_FIREWALL = 6
    const val PLACE_FIREWALLS = 7
    const val PICK_TARPIT = 8
    const val PLACE_TARPITS = 9
    const val START_WAVE = 10

    /** How many of each the guided run insists on, as the owner asked. */
    const val REQUIRED_FIREWALLS = 2
    const val REQUIRED_TARPITS = 2

    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = "WELCOME TO CyOps TD",
            body = "Cyberattacks are inbound on CORE-SERVER. Deploy Cyber Agents " +
                "beside the routes to stop them before they land."
        ),
        TutorialStep(
            title = "THE WAVE COUNT",
            body = "This is the attack wave you are facing. It climbs for as long " +
                "as you hold the server — every fifth wave brings a boss.",
            target = TutorialTarget.WAVE_READOUT
        ),
        TutorialStep(
            title = "CRYPTO ◇",
            // The owner's words, kept verbatim: this is the one line in the
            // tutorial that was specified rather than described.
            body = "Money earned to buy agents to defend server. Crypto◇ earned " +
                "for every kill and every wave completed.",
            target = TutorialTarget.CRYPTO_READOUT
        ),
        TutorialStep(
            title = "KNOW WHAT IS COMING?",
            body = "A quick rundown of the attacks you will meet, the bosses, and " +
                "what your agents do about them. Takes about twenty seconds.",
            gate = TutorialGate.ASK_BRIEFING
        ),
        TutorialStep(
            title = "THREAT BRIEFING",
            // Filled from the catalog at render time; see TutorialBriefing.
            body = ""
        ),
        TutorialStep(
            title = "STEP 1 — OPEN THE ROSTER",
            body = "Tap the AGENTS button in the control bar below.",
            gate = TutorialGate.ACTION
        ),
        TutorialStep(
            title = "STEP 2 — PICK FIREWALL",
            body = "Select FIREWALL. It is cheap, reliable, and cannot be jammed.",
            gate = TutorialGate.ACTION
        ),
        TutorialStep(
            title = "STEP 3 — DEPLOY TWO FIREWALLS",
            body = "Tap two highlighted deployment nodes beside a route. Two is " +
                "not a suggestion: one agent cannot cover a lane on its own.",
            gate = TutorialGate.ACTION,
            requiresAgent = AgentType.FIREWALL,
            requiresCount = REQUIRED_FIREWALLS
        ),
        TutorialStep(
            title = "STEP 4 — PICK TARPIT",
            body = "Open AGENTS again and select TARPIT. It deals no damage — it " +
                "slows every threat inside its aura so your FIREWALLs get more " +
                "shots at them.",
            gate = TutorialGate.ACTION
        ),
        TutorialStep(
            title = "STEP 5 — DEPLOY TWO TARPITS",
            body = "Place two TARPITs so their auras cover the ground your " +
                "FIREWALLs are shooting at. Slowing is what makes the damage land.",
            gate = TutorialGate.ACTION,
            requiresAgent = AgentType.TARPIT,
            requiresCount = REQUIRED_TARPITS
        ),
        TutorialStep(
            title = "STEP 6 — START THE WAVE",
            body = "Tap NEXT WAVE. Your agents fire automatically. Every attack you " +
                "stop pays out ◇ Crypto, which buys more agents and upgrades.",
            gate = TutorialGate.ACTION
        )
    )

    fun stepAt(index: Int): TutorialStep? = steps.getOrNull(index)

    /** The last index; reaching past it ends the tutorial. */
    val lastIndex: Int get() = steps.lastIndex

    /**
     * What the guided placements cost, all in.
     *
     * Asserted against [Balance.STARTING_CRYPTO] by a test rather than assumed:
     * a tutorial that tells a player to place four agents they cannot afford is
     * a tutorial that cannot be completed, and the costs and the starting purse
     * are edited by different people for different reasons.
     */
    val requiredSpend: Int
        get() = AgentType.FIREWALL.cost * REQUIRED_FIREWALLS +
            AgentType.TARPIT.cost * REQUIRED_TARPITS
}
