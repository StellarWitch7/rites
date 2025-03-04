package nl.enjarai.rites.type.ritual_effect

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.serialization.Codec
import com.mojang.serialization.Lifecycle
import net.minecraft.command.EntitySelectorReader
import net.minecraft.entity.Entity
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.SimpleRegistry
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import nl.enjarai.rites.RitesMod
import nl.enjarai.rites.type.Ritual
import nl.enjarai.rites.type.RitualContext
import nl.enjarai.rites.type.ritual_effect.entity.GivePotionEffect
import nl.enjarai.rites.type.ritual_effect.entity.MergeEntityNbtEffect
import nl.enjarai.rites.type.ritual_effect.entity.SummonEntityEffect
import nl.enjarai.rites.type.ritual_effect.flow.*
import nl.enjarai.rites.type.ritual_effect.flow.logic.*
import nl.enjarai.rites.type.ritual_effect.flow.logic.comparison.EqualsEffect
import nl.enjarai.rites.type.ritual_effect.flow.logic.comparison.GreaterThanEffect
import nl.enjarai.rites.type.ritual_effect.flow.logic.comparison.LessThanEffect
import nl.enjarai.rites.type.ritual_effect.flow.loop.ForAreaEffect
import nl.enjarai.rites.type.ritual_effect.flow.loop.ForIEffect
import nl.enjarai.rites.type.ritual_effect.item.*
import nl.enjarai.rites.type.ritual_effect.special.focus.InternalizeFocusEffect
import nl.enjarai.rites.type.ritual_effect.special.waystone.BindWaystoneEffect
import nl.enjarai.rites.type.ritual_effect.special.waystone.UseWaystoneEffect
import nl.enjarai.rites.type.ritual_effect.visual.PlaySoundEffect
import nl.enjarai.rites.type.ritual_effect.visual.SpawnMovingParticlesEffect
import nl.enjarai.rites.type.ritual_effect.visual.SpawnParticlesEffect
import java.util.*

abstract class RitualEffect(val codec: Codec<out RitualEffect>) {
    val uuid: UUID = UUID.randomUUID()

    val id: Identifier? get() {
        return REGISTRY.getId(codec)
    }

    abstract fun activate(pos: BlockPos, ritual: Ritual, ctx: RitualContext): Boolean

    open fun isTicking(): Boolean {
        return false
    }

    open fun getTickCooldown(ctx: RitualContext): Int {
        return 0
    }

    open fun shouldKeepRitualRunning(): Boolean {
        return isTicking()
    }

    companion object {
        val REGISTRY = SimpleRegistry<Codec<out RitualEffect>>(
            RegistryKey.ofRegistry(RitesMod.id("ritual_effects")),
            Lifecycle.stable()
        )
        val CODEC: Codec<RitualEffect> = REGISTRY.codec.dispatch(
            "type",
            { it.codec },
            { it }
        )

        fun registerAll() {
            // control flow effects
            Registry.register(REGISTRY, RitesMod.id("tick"), TickingEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("for_i"), ForIEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("for_area"), ForAreaEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("if"), IfEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("true"), TrueEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("false"), FalseEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("and"), AndEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("or"), OrEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("not"), NotEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("equals"), EqualsEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("greater_than"), GreaterThanEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("less_than"), LessThanEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("variable"), VariableEffect.CODEC)

            // effects that actually do stuff
            Registry.register(REGISTRY, RitesMod.id("bind_waystone"), BindWaystoneEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("use_waystone"), UseWaystoneEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("internalize_focus"), InternalizeFocusEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("play_sound"), PlaySoundEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("spawn_particles"), SpawnParticlesEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("spawn_moving_particles"), SpawnMovingParticlesEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("drop_item"), DropItemEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("drop_item_ref"), DropItemRefEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("merge_item_nbt"), MergeItemNbtEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("merge_entity_nbt"), MergeEntityNbtEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("summon_entity"), SummonEntityEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("give_potion"), GivePotionEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("match_block"), MatchBlockEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("set_block"), SetBlockEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("run_function"), RunFunctionEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("run_command"), RunCommandEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("smelt_item"), SmeltItemEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("set_item"), SetItemEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("set_item_count"), SetItemCountEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("get_item_count"), GetItemCountEffect.CODEC)
            Registry.register(REGISTRY, RitesMod.id("transmutation"), TransmutationEffect.CODEC)
        }

        fun selectEntities(ctx: RitualContext, selectorString: String): List<Entity>? {
            return try {
                val selector = EntitySelectorReader(StringReader(selectorString)).read()
                selector.getEntities(getCommandSource(ctx))
            } catch (e: CommandSyntaxException) {
                null
            }
        }

        fun getCommandSource(ctx: RitualContext): ServerCommandSource {
            return ServerCommandSource(
                CommandOutput.DUMMY, Vec3d.ofBottomCenter(ctx.pos), Vec2f.ZERO, ctx.world as ServerWorld,
                3, "Rite", Text.of("Rite"), ctx.world.server, null
            )
        }
    }
}