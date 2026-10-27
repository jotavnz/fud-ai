//
// Muscle.kt
//
// Kotlin port of the MIT-licensed MuscleMap Swift package
// (github.com/melihcolpan/MuscleMap, Copyright (c) 2026 Melih Colpan).
// See LICENSE for the original license text.
//

package com.fittribe.musclemap

/** Which face of the body to display. */
enum class BodySide { Front, Back }

/** The body gender model. Only [Male] art is currently bundled. */
enum class BodyGender { Male, Female }

/** Which side of the body a muscle belongs to. */
enum class MuscleSide { Left, Right, Both }

/** All muscle groups that can be highlighted on the body. */
enum class Muscle(val raw: String) {
    Abs("abs"),
    Biceps("biceps"),
    Calves("calves"),
    Chest("chest"),
    Deltoids("deltoids"),
    Feet("feet"),
    Forearm("forearm"),
    Gluteal("gluteal"),
    Hamstring("hamstring"),
    Hands("hands"),
    Head("head"),
    Knees("knees"),
    LowerBack("lower-back"),
    Obliques("obliques"),
    Quadriceps("quadriceps"),
    Tibialis("tibialis"),
    Trapezius("trapezius"),
    Triceps("triceps"),
    UpperBack("upper-back"),

    // New muscle groups
    RotatorCuff("rotator-cuff"),
    Serratus("serratus"),
    Rhomboids("rhomboids"),

    // Sub-groups
    Ankles("ankles"),
    Adductors("adductors"),
    Neck("neck"),
    HipFlexors("hip-flexors"),
    UpperChest("upper-chest"),
    LowerChest("lower-chest"),
    InnerQuad("inner-quad"),
    OuterQuad("outer-quad"),
    UpperAbs("upper-abs"),
    LowerAbs("lower-abs"),
    FrontDeltoid("front-deltoid"),
    RearDeltoid("rear-deltoid"),
    UpperTrapezius("upper-trapezius"),
    LowerTrapezius("lower-trapezius");

    /** Whether this is a cosmetic part (head/hair) rather than a muscle. */
    val isCosmeticPart: Boolean get() = this == Head

    /** The parent muscle group, if this muscle is a sub-group. */
    val parentGroup: Muscle?
        get() = when (this) {
            UpperChest, LowerChest -> Chest
            InnerQuad, OuterQuad, HipFlexors -> Quadriceps
            UpperAbs, LowerAbs -> Abs
            FrontDeltoid, RearDeltoid -> Deltoids
            UpperTrapezius, LowerTrapezius -> Trapezius
            Serratus -> Obliques
            Ankles -> Feet
            Adductors -> Hamstring
            Neck -> Head
            else -> null
        }

    /** Whether this muscle is a sub-group of another muscle. */
    val isSubGroup: Boolean get() = parentGroup != null

    /**
     * Whether this sub-group is always rendered even when sub-groups are hidden.
     * When tapped in default mode, the parent muscle is returned instead.
     */
    val isAlwaysVisibleSubGroup: Boolean
        get() = when (this) {
            Ankles, Adductors, Neck -> true
            else -> false
        }

    companion object {
        private val byRaw = entries.associateBy { it.raw }
        fun fromRaw(raw: String): Muscle? = byRaw[raw]
    }
}

/** Internal-only slug that includes hair for rendering purposes. */
internal enum class BodySlug(val raw: String) {
    Abs("abs"),
    Biceps("biceps"),
    Calves("calves"),
    Chest("chest"),
    Deltoids("deltoids"),
    Feet("feet"),
    Forearm("forearm"),
    Gluteal("gluteal"),
    Hamstring("hamstring"),
    Hands("hands"),
    Hair("hair"),
    Head("head"),
    Knees("knees"),
    LowerBack("lower-back"),
    Obliques("obliques"),
    Quadriceps("quadriceps"),
    Tibialis("tibialis"),
    Trapezius("trapezius"),
    Triceps("triceps"),
    UpperBack("upper-back"),

    RotatorCuff("rotator-cuff"),
    Serratus("serratus"),
    Rhomboids("rhomboids"),

    Ankles("ankles"),
    Adductors("adductors"),
    Neck("neck"),
    HipFlexors("hip-flexors"),
    UpperChest("upper-chest"),
    LowerChest("lower-chest"),
    InnerQuad("inner-quad"),
    OuterQuad("outer-quad"),
    UpperAbs("upper-abs"),
    LowerAbs("lower-abs"),
    FrontDeltoid("front-deltoid"),
    RearDeltoid("rear-deltoid"),
    UpperTrapezius("upper-trapezius"),
    LowerTrapezius("lower-trapezius");

    /** The muscle this slug maps to, or null for cosmetic-only slugs (hair). */
    val muscle: Muscle? get() = if (this == Hair) null else Muscle.fromRaw(raw)
}
