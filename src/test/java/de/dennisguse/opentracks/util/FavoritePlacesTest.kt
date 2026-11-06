package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.FavoritePlace
import org.junit.Assert.*
import org.junit.Test

class FavoritePlacesTest {

    private val home = CustomLocation(-23.44, -46.38, "Casa", true)
    private val work = CustomLocation(-23.50, -46.62, "Trabalho", true)
    private val gym = FavoritePlace("g", "Academia", "🏋️", -23.46, -46.40)
    private val school = FavoritePlace("s", "Escola", "🎓", -23.4605, -46.4005)

    @Test
    fun homeAndWorkKeepTheirWording() {
        val start = FavoritePlaces.placeAt(home.lat, home.lon, home, work, listOf(gym))
        val end = FavoritePlaces.placeAt(work.lat + 0.001, work.lon, home, work, listOf(gym))
        assertEquals("🏠 Casa ➔ 🏢 Trabalho", FavoritePlaces.badge(start, end))
    }

    @Test
    fun favoriteIsDetectedAndTheClosestWins() {
        assertEquals("🏋️ Academia", FavoritePlaces.placeAt(-23.4601, -46.4001, home, work, listOf(school, gym)))
        assertEquals("🎓 Escola", FavoritePlaces.placeAt(-23.4606, -46.4006, home, work, listOf(gym, school)))
        assertNull(FavoritePlaces.placeAt(-23.0, -46.0, home, work, listOf(gym)))
    }

    @Test
    fun badgeForOneKnownEnd() {
        assertEquals("Destino: 🏋️ Academia", FavoritePlaces.badge(null, "🏋️ Academia"))
        assertEquals("Partida: 🏠 Casa", FavoritePlaces.badge("🏠 Casa", null))
        assertEquals("Partida: 🏠 Casa", FavoritePlaces.badge("🏠 Casa", "🏠 Casa"))
        assertNull(FavoritePlaces.badge(null, null))
    }
}
