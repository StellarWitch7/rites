package nl.enjarai.rites.resource

import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import nl.enjarai.rites.type.Ritual
import nl.enjarai.rites.type.ritual_effect.RitualEffect
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object Rituals : JsonResource<Ritual>("rituals") {
    override fun processStream(identifier: Identifier, stream: InputStream) {
        val fileReader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val ritualFile = ResourceLoader.GSON.fromJson(fileReader, RitualFile::class.java) ?:
            throw IllegalArgumentException("File format invalid")

        values[identifier] = ritualFile.convert()
    }

    class RitualFile : ResourceLoader.TypeFile<Ritual> {
        val circles = arrayOf<Identifier>()
        val ingredients = hashMapOf<Identifier, Int>()
        val effects = listOf<RitualEffect>()

        override fun convert(): Ritual {
            return Ritual(
                circles.map {
                    CircleTypes.values[it] ?:
                        throw IllegalArgumentException("Invalid circle type: $it")
                },
                ingredients.mapKeys {
                    Registry.ITEM.get(it.key) ?:
                        throw IllegalArgumentException("Invalid ingredient: ${it.key}")
                },
                effects
            )
        }
    }
}