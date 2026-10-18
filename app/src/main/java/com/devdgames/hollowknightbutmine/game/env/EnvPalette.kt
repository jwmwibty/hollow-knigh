package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Color

/**
 * "Verdant Chasm" palette.
 *
 * Cool bioluminescent teal over near-black indigo, with bone-white light shafts.
 * Deliberately a different place than Hallownest — greener, wetter, lit from
 * below by the moss rather than from above — but built from the same silhouette
 * language so it still reads as that kind of world.
 *
 * Each biome additionally owns a sky (void) gradient + fog colour + three
 * atmospheric depth layers (far / mid / near). Far layers sit *closer* to the
 * fog colour and near layers are darker, which is what gives the caverns
 * their sense of depth.
 */
object EnvPalette {

    // --- Air / void (shared defaults) --------------------------------------
    val voidTop = Color.rgb(5, 8, 14)
    val voidMid = Color.rgb(8, 20, 26)
    val voidBottom = Color.rgb(11, 30, 33)

    // --- Biome 1: Moss Grotto — wet teal-green, lit from below -------------
    val mossSkyTop = Color.rgb(3, 9, 11)
    val mossSkyMid = Color.rgb(6, 22, 23)
    val mossSkyBottom = Color.rgb(10, 38, 34)
    val mossFog = Color.rgb(52, 128, 112)
    val mossFar = Color.rgb(13, 42, 42)
    val mossMidLayer = Color.rgb(8, 29, 31)
    val mossNear = Color.rgb(4, 15, 18)

    // --- Biome 2: Ossuary Depths — cold indigo over fossil bone -----------
    val boneSkyTop = Color.rgb(4, 5, 13)
    val boneSkyMid = Color.rgb(9, 12, 27)
    val boneSkyBottom = Color.rgb(17, 20, 38)
    val boneFog = Color.rgb(86, 104, 168)
    val boneFar = Color.rgb(22, 28, 52)
    val boneMidLayer = Color.rgb(13, 18, 38)
    val boneNear = Color.rgb(6, 8, 20)

    // --- Biome 3: Ruined Sanctuary — pale stone teal with warm windows ----
    val sanctSkyTop = Color.rgb(6, 11, 17)
    val sanctSkyMid = Color.rgb(13, 27, 32)
    val sanctSkyBottom = Color.rgb(24, 44, 42)
    val sanctFog = Color.rgb(96, 140, 128)
    val sanctFar = Color.rgb(26, 48, 50)
    val sanctMidLayer = Color.rgb(15, 34, 38)
    val sanctNear = Color.rgb(7, 18, 22)

    // --- Depth layers (further = darker, bluer) -----------------------------
    val farSilhouette = Color.rgb(8, 17, 22)
    val midRock = Color.rgb(16, 41, 45)
    val midRim = Color.rgb(58, 128, 116)
    val windowGlow = Color.rgb(126, 208, 178)
    val ceilingRoot = Color.rgb(7, 15, 19)

    // --- Stone you actually stand on ----------------------------------------
    val rockBody = Color.rgb(17, 34, 39)
    val rockShadow = Color.rgb(5, 11, 14)
    val rockRim = Color.rgb(46, 104, 96)

    val stoneBody = Color.rgb(22, 42, 48)
    val stoneMid = Color.rgb(38, 68, 74)
    val stoneLit = Color.rgb(96, 124, 122)

    // --- Cut stone (sanctuary masonry, the sunken bridge) -------------------
    val cutStoneBody = Color.rgb(26, 46, 52)
    val cutStoneShadow = Color.rgb(9, 18, 22)
    val cutStoneRim = Color.rgb(112, 142, 138)
    val cutStoneJoint = Color.rgb(8, 16, 19)

    // --- Fossil bone (the great rib cage) -----------------------------------
    val boneLit = Color.rgb(158, 186, 172)
    val boneMid = Color.rgb(82, 110, 104)
    val boneDark = Color.rgb(28, 44, 48)
    val boneBody = Color.rgb(46, 66, 66)
    val boneShadow = Color.rgb(12, 22, 26)

    // --- Water ----------------------------------------------------------------
    val waterTop = Color.rgb(40, 126, 122)
    val waterMid = Color.rgb(14, 62, 68)
    val waterDeep = Color.rgb(5, 20, 26)
    val waterFoam = Color.rgb(170, 235, 215)

    // --- Flora ------------------------------------------------------------------
    val ivyDeep = Color.rgb(14, 48, 42)
    val ivyMid = Color.rgb(34, 88, 68)
    val ivyLit = Color.rgb(92, 164, 116)
    val clothDeep = Color.rgb(12, 24, 38)
    val clothMid = Color.rgb(26, 50, 72)

    // --- Moss & flora -------------------------------------------------------------
    val mossDeep = Color.rgb(20, 62, 52)
    val mossMid = Color.rgb(48, 116, 86)
    val mossLit = Color.rgb(118, 200, 148)
    val sporeTeal = Color.rgb(150, 232, 206)
    val sporeWarm = Color.rgb(242, 208, 146)
    val sporeCold = Color.rgb(150, 176, 255)
    val mushCap = Color.rgb(96, 196, 166)
    val mushStem = Color.rgb(196, 216, 200)

    // --- Hazards / accents -----------------------------------------------------------
    val spikeLit = Color.rgb(196, 210, 218)
    val spikeDark = Color.rgb(96, 112, 124)
    val candleGold = Color.rgb(255, 214, 130)
    val brazierEmber = Color.rgb(255, 150, 72)

    // --- Foreground / framing ----------------------------------------------------------
    val foreground = Color.rgb(3, 6, 9)
}
