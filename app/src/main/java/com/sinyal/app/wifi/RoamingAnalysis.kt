package com.sinyal.app.wifi

import com.sinyal.app.ar.ApSurvey
import com.sinyal.app.ar.RoamEvent

/** One handover, judged. */
data class RoamVerdict(
    val event: RoamEvent,
    val wasLate: Boolean,
    /** How much stronger the new radio was at the moment of the switch. */
    val gainDb: Int,
)

/**
 * Judges how well the phone moved between transmitters during a walk.
 *
 * The complaint this answers is the commonest one in any home with a mesh or a
 * repeater, and it is almost never diagnosed: the phone clings to the node it
 * first joined long after a nearer one would serve it better. It is called a
 * sticky client, the symptom is "the Wi-Fi is bad in the back room" even though
 * a node is in that room, and nothing on the phone reports it.
 *
 * A handover is called late when the radio being left had already fallen below
 * the point where throughput collapses, while the one being joined was
 * comfortably strong. Both halves matter: leaving a weak radio for another weak
 * one is not a failure of roaming, it is a coverage gap, and saying so would
 * send someone to fix the wrong thing.
 */
object RoamingAnalysis {

    fun run(survey: ApSurvey): List<RoamVerdict> =
        survey.roams.map { event ->
            val leaving = event.leavingDbm
            val gain = event.joiningDbm - (leaving ?: event.joiningDbm)

            RoamVerdict(
                event = event,
                wasLate = leaving != null &&
                    leaving <= STICKY_DBM &&
                    event.joiningDbm >= COMFORTABLE_DBM &&
                    gain >= WORTH_MOVING_DB,
                gainDb = gain,
            )
        }

    /**
     * Below this a link is already struggling; a phone still holding on here
     * while a better radio is in range has held on too long.
     */
    private const val STICKY_DBM = -70

    /** Strong enough that the switch was clearly available earlier. */
    private const val COMFORTABLE_DBM = -60

    /** Smaller than this and swapping radios costs more than it gains. */
    private const val WORTH_MOVING_DB = 8
}
