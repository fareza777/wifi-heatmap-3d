package com.sinyal.app.wifi

import androidx.annotation.StringRes
import com.sinyal.app.R

/**
 * A one-glance verdict on how well a network protects the traffic on it.
 *
 * Four bands rather than a score out of ten: the underlying facts are discrete
 * — which protocol, WPS on or off — so a continuous number would imply a
 * precision that is not there, and invite comparing two networks that are
 * really in the same situation.
 */
enum class SecurityGrade(
    val letter: String,
    @StringRes val label: Int,
) {
    A("A", R.string.grade_a_label),
    B("B", R.string.grade_b_label),
    C("C", R.string.grade_c_label),
    F("F", R.string.grade_f_label),
}

/** The grade for one network, with the single fact that decided it. */
data class GradedNetwork(
    val ap: NearbyAp,
    val grade: SecurityGrade,
    @StringRes val reason: Int,
)

/**
 * Grades every network in a scan.
 *
 * Judged purely on what the beacon advertises. That is less than a real audit
 * would look at — it says nothing about password strength, firmware age, or who
 * else already knows the key — and the screen says so rather than letting a
 * letter grade imply the whole story.
 */
object SecurityGrading {

    fun gradeAll(networks: List<NearbyAp>): List<GradedNetwork> =
        networks.map(::grade).sortedWith(
            compareByDescending<GradedNetwork> { it.grade.ordinal }
                .thenByDescending { it.ap.rssiDbm },
        )

    fun grade(ap: NearbyAp): GradedNetwork {
        val (grade, reason) = when (ap.security) {
            SecurityType.WPA3 -> SecurityGrade.A to R.string.grade_a_why

            SecurityType.ENTERPRISE -> SecurityGrade.A to R.string.grade_enterprise_why

            // OWE encrypts without a password, which is genuinely better than an
            // open network — but it authenticates nothing, so it cannot sit
            // alongside WPA3 at the top.
            SecurityType.OWE -> SecurityGrade.B to R.string.grade_owe_why

            SecurityType.WPA2 -> if (ap.supportsWps) {
                SecurityGrade.C to R.string.grade_b_wps_why
            } else {
                SecurityGrade.B to R.string.grade_b_why
            }

            SecurityType.WPA -> SecurityGrade.C to R.string.grade_c_why
            SecurityType.WEP -> SecurityGrade.F to R.string.grade_f_wep_why
            SecurityType.OPEN -> SecurityGrade.F to R.string.grade_f_open_why
            SecurityType.UNKNOWN -> SecurityGrade.C to R.string.grade_unknown_why
        }

        return GradedNetwork(ap, grade, reason)
    }

    /** How many networks sit at each protection level, strongest first. */
    fun mix(networks: List<NearbyAp>): List<Pair<SecurityType, Int>> =
        networks.groupingBy { it.security }
            .eachCount()
            .toList()
            .sortedBy { (type, _) -> type.ordinal }
}
