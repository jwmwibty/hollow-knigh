package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Color

/**
 * "Verdant Chasm" palette.
 *
 * Cool bioluminescent teal over near-black indigo, with bone-white light shafts.
 * Deliberately a different place than Hallownest — greener, wetter, lit from
 * below by the moss rather than from above — but built from the same silhouette
 * language so it still reads as that kind of world.
 */
object EnvPalette {

    // --- Air / void -------------------------------------------------------
    val voidTop = Color.rgb(5, 8, 14)
    val voidMid = Color.rgb(8, 20, 26)
    val voidBottom = Color.rgb(11, 30, 33)

    // --- Depth layers (further = darker, bluer) ---------------------------
    val farSilhouette = Color.rgb(8, 17, 22)
    val midRock = Color.rgb(16, 41, 45)
    val midRim = Color.rgb(58, 128, 116)
    val windowGlow = Color.rgb(126, 208, 178)
    val ceilingRoot = Color.rgb(7, 15, 19)

    // --- Stone you actually stand on --------------------------------------
    val rockBody = Color.rgb(17, 34, 39)
    val rockShadow = Color.rgb(5, 11, 14)
    val rockRim = Color.rgb(46, 104, 96)

    val stoneBody = Color.rgb(22, 42, 48)
    val stoneMid = Color.rgb(38, 68, 74)
    val stoneLit = Color.rgb(96, 124, 122)

    // --- Fossil bone (the great rib cage) ---------------------------------
    val boneLit = Color.rgb(158, 186, 172)
    val boneMid = Color.rgb(82, 110, 104)
    val boneDark = Color.rgb(28, 44, 48)

    // --- Water -------------------------------------------------------------
    val waterTop = Color.rgb(40, 126, 122)
    val waterMid = Color.rgb(14, 62, 68)
    val waterDeep = Color.rgb(5, 20, 26)

    // --- Flora -------------------------------------------------------------
    val ivyDeep = Color.rgb(14, 48, 42)
    val ivyMid = Color.rgb(34, 88, 68)
    val ivyLit = Color.rgb(92, 164, 116)
    val clothDeep = Color.rgb(12, 24, 38)
    val clothMid = Color.rgb(26, 50, 72)

    // --- Moss & flora ------------------------------------------------------
    val mossDeep = Color.rgb(20, 62, 52)
    val mossMid = Color.rgb(48, 116, 86)
    val mossLit = Color.rgb(118, 200, 148)
    val sporeTeal = Color.rgb(150, 232, 206)
    val sporeWarm = Color.rgb(242, 208, 146)
    val mushCap = Color.rgb(96, 196, 166)
    val mushStem = Color.rgb(196, 216, 200)

    // --- Foreground / framing ---------------------------------------------
    val foreground = Color.rgb(3, 6, 9)
}
