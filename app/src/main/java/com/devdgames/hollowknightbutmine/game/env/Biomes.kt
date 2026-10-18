package com.devdgames.hollowknightbutmine.game.env

import android.graphics.Color
import com.devdgames.hollowknightbutmine.game.world.Biome

/**
 * Everything colour-related for one biome. Rock is lit near its exposed surfaces and falls
 * off into [rockDeep]; [glow] is the biome's signature light (lanterns, spores, crystals);
 * [far]/[mid] are the parallax silhouette tints.
 */
class BiomePalette(
    val skyTop: Int,
    val skyBottom: Int,
    val rock: Int,
    val rockDeep: Int,
    val rockRim: Int,
    val rockFace: Int,
    val moss: Int,
    val mossLit: Int,
    val grassy: Boolean,
    val ledge: Int,
    val ledgeLit: Int,
    val glow: Int,
    val far: Int,
    val mid: Int,
    val hazard: Int,
    val hazardLit: Int,
    val mapInk: Int,
    val titleTint: Int
)

object Biomes {
    private fun c(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    private val town = BiomePalette(
        skyTop = c(9, 10, 18), skyBottom = c(30, 33, 48),
        rock = c(52, 54, 66), rockDeep = c(17, 18, 25), rockRim = c(112, 116, 134), rockFace = c(70, 73, 88),
        moss = c(44, 48, 58), mossLit = c(96, 100, 112), grassy = false,
        ledge = c(44, 40, 38), ledgeLit = c(120, 108, 96),
        glow = c(255, 206, 128), far = c(16, 18, 28), mid = c(26, 28, 40),
        hazard = c(120, 130, 140), hazardLit = c(210, 215, 225),
        mapInk = c(178, 184, 200), titleTint = c(226, 230, 240)
    )

    private val cliffs = BiomePalette(
        skyTop = c(8, 10, 18), skyBottom = c(26, 30, 44),
        rock = c(44, 50, 64), rockDeep = c(13, 15, 22), rockRim = c(104, 114, 134), rockFace = c(60, 68, 86),
        moss = c(40, 52, 50), mossLit = c(84, 108, 96), grassy = true,
        ledge = c(40, 44, 50), ledgeLit = c(118, 126, 140),
        glow = c(190, 215, 255), far = c(14, 17, 27), mid = c(22, 26, 38),
        hazard = c(120, 130, 140), hazardLit = c(210, 215, 225),
        mapInk = c(160, 176, 200), titleTint = c(220, 228, 244)
    )

    private val crossroads = BiomePalette(
        skyTop = c(8, 11, 18), skyBottom = c(22, 30, 42),
        rock = c(40, 48, 64), rockDeep = c(12, 15, 22), rockRim = c(104, 122, 150), rockFace = c(58, 68, 90),
        moss = c(34, 46, 54), mossLit = c(70, 92, 104), grassy = false,
        ledge = c(44, 48, 58), ledgeLit = c(128, 140, 160),
        glow = c(150, 205, 255), far = c(14, 18, 28), mid = c(22, 28, 42),
        hazard = c(150, 160, 175), hazardLit = c(220, 226, 236),
        mapInk = c(170, 190, 222), titleTint = c(222, 232, 246)
    )

    private val green = BiomePalette(
        skyTop = c(5, 8, 14), skyBottom = c(11, 30, 33),
        rock = c(22, 46, 50), rockDeep = c(5, 12, 15), rockRim = c(58, 128, 116), rockFace = c(30, 62, 64),
        moss = c(24, 72, 58), mossLit = c(118, 200, 148), grassy = true,
        ledge = c(28, 60, 52), ledgeLit = c(112, 184, 140),
        glow = c(150, 232, 206), far = c(7, 18, 22), mid = c(12, 34, 36),
        hazard = c(40, 150, 80), hazardLit = c(150, 255, 150),
        mapInk = c(140, 212, 180), titleTint = c(210, 246, 230)
    )

    private val fungal = BiomePalette(
        skyTop = c(16, 10, 20), skyBottom = c(44, 26, 40),
        rock = c(62, 42, 54), rockDeep = c(18, 11, 17), rockRim = c(160, 112, 128), rockFace = c(84, 56, 72),
        moss = c(96, 56, 64), mossLit = c(222, 150, 96), grassy = true,
        ledge = c(70, 46, 50), ledgeLit = c(196, 140, 110),
        glow = c(255, 178, 92), far = c(22, 13, 24), mid = c(38, 22, 38),
        hazard = c(110, 150, 40), hazardLit = c(190, 255, 110),
        mapInk = c(220, 170, 150), titleTint = c(250, 228, 210)
    )

    private val city = BiomePalette(
        skyTop = c(5, 9, 24), skyBottom = c(17, 30, 58),
        rock = c(34, 48, 84), rockDeep = c(8, 12, 26), rockRim = c(116, 156, 210), rockFace = c(48, 66, 110),
        moss = c(30, 44, 74), mossLit = c(80, 110, 150), grassy = false,
        ledge = c(36, 50, 86), ledgeLit = c(150, 190, 240),
        glow = c(176, 214, 255), far = c(9, 14, 32), mid = c(16, 24, 50),
        hazard = c(90, 120, 170), hazardLit = c(200, 220, 255),
        mapInk = c(150, 190, 240), titleTint = c(214, 232, 255)
    )

    private val crystal = BiomePalette(
        skyTop = c(16, 8, 22), skyBottom = c(46, 18, 52),
        rock = c(64, 40, 76), rockDeep = c(18, 10, 22), rockRim = c(200, 130, 212), rockFace = c(90, 56, 104),
        moss = c(90, 50, 100), mossLit = c(240, 160, 240), grassy = false,
        ledge = c(70, 46, 84), ledgeLit = c(220, 150, 230),
        glow = c(255, 150, 230), far = c(24, 12, 30), mid = c(40, 20, 50),
        hazard = c(230, 140, 220), hazardLit = c(255, 220, 255),
        mapInk = c(236, 170, 236), titleTint = c(255, 226, 250)
    )

    private val resting = BiomePalette(
        skyTop = c(11, 9, 18), skyBottom = c(32, 28, 48),
        rock = c(48, 44, 66), rockDeep = c(14, 12, 20), rockRim = c(140, 130, 176), rockFace = c(66, 60, 90),
        moss = c(52, 48, 72), mossLit = c(120, 112, 150), grassy = false,
        ledge = c(50, 46, 64), ledgeLit = c(160, 150, 190),
        glow = c(214, 222, 255), far = c(17, 15, 26), mid = c(28, 24, 42),
        hazard = c(150, 150, 175), hazardLit = c(225, 225, 240),
        mapInk = c(190, 180, 220), titleTint = c(236, 232, 250)
    )

    private val edge = BiomePalette(
        skyTop = c(12, 12, 15), skyBottom = c(44, 44, 50),
        rock = c(60, 60, 68), rockDeep = c(18, 18, 21), rockRim = c(160, 160, 170), rockFace = c(82, 82, 92),
        moss = c(70, 70, 76), mossLit = c(150, 150, 156), grassy = false,
        ledge = c(60, 58, 60), ledgeLit = c(170, 168, 170),
        glow = c(240, 240, 248), far = c(22, 22, 26), mid = c(36, 36, 42),
        hazard = c(170, 170, 178), hazardLit = c(236, 236, 240),
        mapInk = c(200, 200, 208), titleTint = c(240, 240, 244)
    )

    private val waterways = BiomePalette(
        skyTop = c(6, 13, 9), skyBottom = c(20, 40, 26),
        rock = c(34, 54, 40), rockDeep = c(8, 15, 10), rockRim = c(96, 142, 108), rockFace = c(48, 74, 56),
        moss = c(40, 70, 46), mossLit = c(110, 170, 110), grassy = true,
        ledge = c(44, 60, 46), ledgeLit = c(120, 160, 120),
        glow = c(150, 230, 170), far = c(9, 18, 12), mid = c(16, 30, 20),
        hazard = c(40, 110, 60), hazardLit = c(120, 220, 140),
        mapInk = c(150, 210, 160), titleTint = c(214, 244, 220)
    )

    private val deepnest = BiomePalette(
        skyTop = c(3, 2, 5), skyBottom = c(13, 9, 15),
        rock = c(30, 22, 32), rockDeep = c(6, 4, 8), rockRim = c(82, 64, 86), rockFace = c(40, 30, 44),
        moss = c(30, 24, 32), mossLit = c(80, 66, 84), grassy = false,
        ledge = c(34, 26, 36), ledgeLit = c(110, 90, 112),
        glow = c(255, 140, 70), far = c(8, 6, 10), mid = c(16, 12, 18),
        hazard = c(120, 110, 125), hazardLit = c(200, 190, 205),
        mapInk = c(150, 120, 150), titleTint = c(230, 214, 230)
    )

    private val basin = BiomePalette(
        skyTop = c(5, 7, 12), skyBottom = c(18, 22, 32),
        rock = c(34, 40, 54), rockDeep = c(8, 10, 16), rockRim = c(110, 122, 146), rockFace = c(48, 56, 74),
        moss = c(36, 42, 56), mossLit = c(100, 110, 130), grassy = false,
        ledge = c(60, 66, 74), ledgeLit = c(170, 184, 190),
        glow = c(170, 200, 230), far = c(10, 13, 20), mid = c(18, 22, 32),
        hazard = c(160, 170, 180), hazardLit = c(225, 230, 236),
        mapInk = c(160, 176, 200), titleTint = c(222, 230, 242)
    )

    private val abyss = BiomePalette(
        skyTop = c(1, 1, 3), skyBottom = c(8, 8, 13),
        rock = c(16, 16, 24), rockDeep = c(2, 2, 4), rockRim = c(58, 58, 78), rockFace = c(24, 24, 34),
        moss = c(16, 16, 24), mossLit = c(50, 50, 70), grassy = false,
        ledge = c(18, 18, 26), ledgeLit = c(80, 80, 104),
        glow = c(200, 210, 230), far = c(4, 4, 7), mid = c(8, 8, 13),
        hazard = c(60, 60, 80), hazardLit = c(140, 140, 170),
        mapInk = c(110, 110, 140), titleTint = c(214, 214, 230)
    )

    fun palette(b: Biome): BiomePalette = when (b) {
        Biome.TOWN -> town
        Biome.CLIFFS -> cliffs
        Biome.CROSSROADS -> crossroads
        Biome.GREEN -> green
        Biome.FUNGAL -> fungal
        Biome.CITY -> city
        Biome.CRYSTAL -> crystal
        Biome.RESTING -> resting
        Biome.EDGE -> edge
        Biome.WATERWAYS -> waterways
        Biome.DEEPNEST -> deepnest
        Biome.BASIN -> basin
        Biome.ABYSS -> abyss
    }
}
